# O'kit API Gateway

Gateway applicative Spring Cloud Gateway Server WebFlux. Elle protège les API
métier avec les JWT Keycloak, centralise le CORS et route les requêtes vers les
microservices internes.

## Routes livrées

| Route publique | Destination | Réécriture |
| --- | --- | --- |
| `/api/v1/users/**` | `${USER_SERVICE_URL}` | aucune |
| `GET /api/v1/products/**` | `${CATALOGUE_SERVICE_URL}` | aucune |
| `POST, PATCH /api/v1/products/**` | `${CATALOGUE_SERVICE_URL}` | aucune |

L'en-tête `Authorization` est transmis au `user-service`, qui valide également
le JWT.

Les lectures Catalogue exigent un JWT valide. Les écritures sont autorisées
uniquement lorsque le claim signé `okit_direct_realm_roles` contient `SELLER`
et ne contient pas `BUYER`. Le microservice Catalogue n'étant pas exposé sur un
port hôte dans Compose, la Gateway constitue le point d'entrée local de ces
opérations.

## Variables d'environnement

| Variable | Description |
| --- | --- |
| `USER_SERVICE_URL` | URL du service utilisateur, `http://localhost:8081` par défaut |
| `CATALOGUE_SERVICE_URL` | URL du service Catalogue, `http://localhost:8082` par défaut |
| `OIDC_ISSUER_URI` | issuer public du realm Keycloak |
| `OIDC_JWK_SET_URI` | endpoint des clés publiques Keycloak |
| `CORS_ALLOWED_ORIGIN` | origine navigateur exacte, `http://localhost:19006` par défaut |
| `SELLER_CORS_ALLOWED_ORIGIN` | origine du backoffice, `http://localhost:5173` par défaut |
| `SERVER_PORT` | port HTTP, `8080` par défaut |

## Vérification locale

```bash
mvn verify
```

Avec Docker Compose, la gateway est exposée sur `http://localhost:8080`.

```bash
# Réponse attendue : 401 sans JWT.
curl --output /dev/null --write-out '%{http_code}\n' \
  http://localhost:8080/api/v1/users/me
```

Les endpoints `/health` et `/metrics` sont destinés aux probes et à
l'observabilité internes.
