# ms-stock

Microservice de gestion des stocks par variante (taille) pour O'kit : quantités, mouvements, alertes de stock bas.
Il ne connaît ni les produits ni les tailles : `variantId` est l'identifiant `Variante.idVariante` fourni par ms-catalogue, traité comme un identifiant opaque (aucune FK inter-service).

## Démarrage

Prérequis : Java 21, Maven, PostgreSQL, Docker (tests Testcontainers).

```bash
cd backend/ms-stock
export DB_PASSWORD=...
mvn spring-boot:run        # port 8084 par défaut
mvn verify                 # tests (Docker requis)
```

Le schéma est géré uniquement par Flyway (`db/migration/V1__init_stock_schema.sql`).

## Variables d'environnement

| Variable | Défaut | Rôle |
|---|---|---|
| `SERVER_PORT` | `8084` | Port HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5432/okit_stock_db` | Base PostgreSQL |
| `DB_USERNAME` | `okit_stock` | Utilisateur |
| `DB_PASSWORD` | *(obligatoire)* | Mot de passe |
| `DB_POOL_SIZE` | `10` | Taille du pool |
| `OIDC_ISSUER_URI` | `http://localhost:8090/realms/okit` | Issuer Keycloak |
| `OIDC_JWK_SET_URI` | `http://localhost:8090/realms/okit/protocol/openid-connect/certs` | Clés de validation JWT |
| `SWAGGER_ENABLED` | `true` | Swagger UI (`/swagger-ui.html`, spec sur `/v3/api-docs`) |

## Sécurité

JWT Keycloak. Les rôles sont lus dans `realm_access.roles` et préfixés `ROLE_` (même convention que ms-utilisateur).

| Chemin | Rôle requis | Exposé via la gateway |
|---|---|---|
| `/api/v1/stocks/**` | `BUYER` ou `SELLER` | oui |
| `/api/v1/seller/**` | `SELLER` | oui |
| `/internal/**` | `SVC_COMMANDE` | **non, jamais** |

`/actuator/health` et `/actuator/info` sont publics.

## Statut d'un stock

Le backend renvoie un statut, la couleur est décidée par le frontend.

- `OUT_OF_STOCK` : quantité ≤ 0, ou aucune ligne de stock pour la variante
- `LOW_STOCK` : quantité < seuil (10 par défaut)
- `IN_STOCK` : sinon

## Endpoints client

- `GET /api/v1/stocks?variantIds=1,2,3` : `[{ "variantId", "status" }]`
- `GET /api/v1/stocks/check?variantId=1&qty=2` : `{ "variantId", "available", "status" }`. Indicatif : le stock n'est réellement réservé qu'au `decrement`.

## Endpoints vendeur

- `GET /api/v1/seller/stocks` : lignes de stock existantes (paginé)
- `POST /api/v1/seller/stocks` : `{ "variantId", "quantity", "threshold"? }`. Crée la ligne, idempotent (renvoie la ligne existante si elle existe déjà)
- `POST /api/v1/seller/stocks/{variantId}/restock` : `{ "quantity" }` (> 0), mouvement `RESTOCK`. 404 si la ligne n'existe pas
- `PATCH /api/v1/seller/stocks/{variantId}/threshold` : `{ "threshold" }`
- `GET /api/v1/seller/notifications?unread=true`
- `GET /api/v1/seller/notifications/unread-count` : `{ "count" }`
- `POST /api/v1/seller/notifications/{id}/read` : 204

Une notification `LOW_STOCK` est créée une seule fois au franchissement du seuil (`stock < seuil`). Une `OUT_OF_STOCK` est créée quand le stock tombe à 0. L'alerte est réarmée quand le stock remonte à `>= seuil`. La notification est écrite dans la même transaction que le mouvement.

## Contrat pour l'équipe commande (`/internal`)

Appels directs de service à service (nom de service Docker, port 8084), avec un token **client credentials** Keycloak portant le rôle realm `SVC_COMMANDE`.

> À créer côté Keycloak : rôle realm `SVC_COMMANDE` et client confidentiel avec service account auquel ce rôle est affecté. Absent de `okit-realm.json` à ce jour.

### `POST /internal/stocks/decrement`

À appeler quand une commande passe à « payée ».

```json
{
  "orderId": "CMD-2026-000123",
  "lines": [
    { "variantId": 12, "quantity": 2 },
    { "variantId": 15, "quantity": 1 }
  ]
}
```

Contraintes : `orderId` non vide, 80 caractères max ; `lines` non vide ; `quantity` ≥ 1. Les lignes qui ciblent la même variante sont additionnées.

**Atomique (tout ou rien).** Si une seule ligne est insuffisante, rien n'est décrémenté.

| Statut | Signification | Corps |
|---|---|---|
| `200` | Tout est décrémenté | `{ "orderId", "status": "DECREMENTED" }` |
| `409` | Stock insuffisant (cas métier) | `application/problem+json` avec `orderId` et `shortages: [{ "variantId", "requested", "available" }]` |
| `400` | Requête invalide | ProblemDetail |
| `401` / `403` | Token absent / rôle manquant | |

Une variante sans ligne de stock est traitée comme insuffisante (`available: 0`).

**Idempotence par `orderId`.** Rejouer le même `orderId` renvoie exactement la même réponse (200 ou 409) sans nouvel effet. Le retry est donc sans danger : prévoir un timeout et un retry côté appelant.

Attention : un `409` peut aussi signifier « requête concurrente avec le même `orderId` en cours, réessayez » (corps sans `shortages`). Distinguer les deux cas par la présence de `shortages`, et rejouer dans le second.

### `POST /internal/stocks/release`

À appeler pour annuler une commande déjà décrémentée. Même corps que `decrement`.

- `200` : `{ "orderId", "status": "RELEASED" }`, quantités ré-incrémentées (mouvement `ORDER_CANCELLED`)
- `404` : une des variantes n'a pas de ligne de stock
- Idempotent par `orderId`, sous la clé interne `release:<orderId>` (indépendante de celle de `decrement`)

`release` ne vérifie pas qu'un `decrement` a eu lieu : c'est à l'appelant de n'annuler que les commandes décrémentées, avec les mêmes lignes.

## Données

Tables : `stocks`, `stock_movements` (append-only), `idempotent_requests`, `notifications`.
Concurrence : verrou pessimiste (`SELECT ... FOR UPDATE`) pris dans l'ordre trié de `id_variante` (pas de deadlock), `@Version` en filet de sécurité.

## Limites connues

- Aucune purge automatique de `idempotent_requests`.
- Pas d'endpoint de correction manuelle vers le bas ni d'historique des mouvements.