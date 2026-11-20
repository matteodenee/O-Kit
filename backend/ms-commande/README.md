# ms-commande

Microservice **Commande, Panier et Flocage** d'O'kit.
Le panier est une commande au statut `PANIER` (au plus un par utilisateur).
`id_utilisateur` (= `sub` du JWT), `id_produit` et `id_variante` sont des identifiants d'autres services, sans clé étrangère.

## Démarrage

```bash
docker compose up -d commande-db keycloak catalogue-service stock-service
cd backend/ms-commande
DB_PASSWORD=okit_commande_password mvn spring-boot:run     # port 8085
mvn test                                                   # Docker requis (Testcontainers)
```

Swagger : http://localhost:8085/swagger-ui.html

## Cycle de vie

`PANIER → CONFIRMEE → EN_PREPARATION → EXPEDIEE → LIVREE`, annulation possible depuis `CONFIRMEE` ou `EN_PREPARATION`.

## Endpoints

| Méthode | Chemin | Rôle | Description |
|---|---|---|---|
| GET | `/api/v1/panier` | BUYER | Panier courant |
| POST | `/api/v1/panier/lignes` | BUYER | `{idProduit, idVariante, quantite, flocage?: {texte, numero}}` |
| PATCH | `/api/v1/panier/lignes/{id}` | BUYER | `{quantite}` |
| DELETE | `/api/v1/panier/lignes/{id}` | BUYER | Retire une ligne |
| DELETE | `/api/v1/panier` | BUYER | Vide le panier |
| POST | `/api/v1/commandes` | BUYER | `{adresseLivraison}` : paiement + décrément stock, renvoie la commande |
| GET | `/api/v1/commandes` | BUYER | Historique |
| GET | `/api/v1/commandes/{id}` | BUYER | Détail |
| PATCH | `/api/v1/commandes/{id}` | BUYER | `{adresseLivraison}` tant que non expédiée |
| POST | `/api/v1/commandes/{id}/annulation` | BUYER | Annule (stock libéré + remboursement) |
| POST | `/api/v1/commandes/{id}/lignes/{idLigne}/annulation` | BUYER | Annule une ligne, recalcule le total |
| GET | `/api/v1/seller/commandes?statut=` | SELLER | Liste paginée |
| GET | `/api/v1/seller/commandes/{id}` | SELLER | Détail |
| PATCH | `/api/v1/seller/commandes/{id}/statut` | SELLER | `{statut, codeSuiviTransporteur?}` (code obligatoire pour `EXPEDIEE`) |

Erreurs au format ProblemDetail : 400 requête invalide, 404 introuvable, 409 état / stock insuffisant (`manques`), 402 paiement refusé, 503 service indisponible.

## Règles métier

- Flocage seulement si `estFlocable` côté catalogue ; texte en majuscules, 15 caractères max, numéro 0 à 99 ; prix fixe `okit.commande.prix-flocage-ht` par maillot.
- Prix unitaire HT et TVA copiés au moment de l'ajout (promotion incluse).
- Maximum 10 exemplaires par taille ; disponibilité vérifiée auprès de ms-stock (indicatif).
- Frais de livraison : forfait TTC `okit.commande.frais-livraison`.

## Appels sortants

| Vers | Appel | Authentification |
|---|---|---|
| ms-catalogue | `GET /api/v1/products/{id}` | aucune |
| ms-stock | `GET /api/v1/stocks/check` | token de l'acheteur relayé |
| ms-stock | `POST /internal/stocks/decrement` et `/release` | client credentials Keycloak `ms-commande` (rôle `SVC_COMMANDE`) |
| ms-paiement | `PaiementClient` | **simulé** par `MockPaiementClient` en attendant le service |

Validation d'une commande : paiement, puis `decrement` avec une référence `CMD-<id>-<timestamp>` (nouvelle à chaque tentative). En cas de stock insuffisant, remboursement et le panier est conservé. Les annulations de ligne utilisent la référence `<ref>-L<idLigne>` car ms-stock est idempotent par référence.

## Limites connues

- Pas encore de RabbitMQ : l'événement `ORDER_PAID` est un appel REST idempotent vers ms-stock.
- Si ms-stock ne répond plus après le paiement, le client est remboursé, mais un décrément a pu avoir lieu (erreur journalisée, à vérifier à la main).
- Pas d'endpoint `/internal` pour ms-retour et le chatbot (à ajouter).
