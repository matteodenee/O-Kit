# O'Kit

O'Kit est une application de m-commerce. Le périmètre utilisateur livré couvre
l'authentification centralisée par Keycloak, le profil métier acheteur dans
`user-service`, l'application mobile Expo et l'authentification du backoffice
vendeur.

## État du MVP utilisateur

Le MVP d'authentification et de profil de `user-service` est terminé :

- `GET /api/v1/users/me` consulte le profil de l'acheteur courant ;
- `PUT /api/v1/users/me` crée ou remplace ce profil ;
- `GET /api/v1/users/me/id` expose uniquement son UUID métier aux services
  agissant avec son JWT ;
- PostgreSQL conserve prénom, nom, téléphone et date de naissance facultative ;
- Keycloak reste propriétaire des comptes, mots de passe, e-mails, sessions et
  rôles ;
- l'application mobile accepte exclusivement `BUYER` ;
- le backoffice exige `SELLER` sans attribution directe de `BUYER` et ignore
  les rôles techniques hérités de `default-roles-okit` ;
- un vendeur ne possède aucune donnée dans `user-service`.

Les adresses de livraison et la wishlist sont envisagées comme de nouvelles
tranches fonctionnelles autour du domaine utilisateur. Elles ne font pas partie
du MVP actuel et leur service propriétaire doit être décidé avant leur
implémentation. La wishlist dépendra notamment des identifiants définis par le
domaine catalogue.

## Architecture livrée

| Composant | Rôle | URL locale |
| --- | --- | --- |
| Keycloak | Identités, rôles et parcours OIDC | `http://localhost:8090` |
| Mailpit | Courriels de développement | `http://localhost:8025` |
| PostgreSQL | Instance locale mutualisable, base logique `okit_users` | `localhost:5432` |
| `user-service` | Profil métier acheteur | `http://localhost:8081` |
| `api-gateway` | Entrée des API | `http://localhost:8080` |
| Client Expo | Application acheteur | `http://localhost:19006` |
| Backoffice React | Authentification vendeur | `http://localhost:5173` |

## Démarrage rapide

Prérequis : Docker avec Compose, Node.js avec npm et Git.

```bash
cp .env.example .env
```

Remplacer les valeurs `change-me-local-only` dans `.env`, puis démarrer la
stack depuis la racine du dépôt :

```bash
docker compose up --build -d
docker compose ps
```

Appliquer ensuite la configuration Keycloak au realm existant, sans supprimer
les comptes déjà créés :

```bash
set -a
source .env
set +a
./infra/keycloak/reconcile-local-realm.sh
```

Vérification rapide :

```bash
curl --fail http://127.0.0.1:8090/realms/okit/.well-known/openid-configuration
curl --fail http://127.0.0.1:8081/health
curl --fail http://127.0.0.1:8080/health
curl --include http://127.0.0.1:8080/api/v1/users/me
./backend/ms-utilisateur/scripts/test-local-user-flow.sh
```

Les trois premières requêtes doivent réussir, la quatrième doit retourner
`401 Unauthorized`, puis le script doit annoncer `Local user flow succeeded`.

## Interfaces

Application acheteur :

```bash
cd apps/mobile-client
cp .env.example .env.local
npm ci
npm run web
```

Backoffice vendeur :

```bash
cd apps/seller-backoffice
cp .env.example .env.local
npm ci
npm run dev
```

## Vérifications automatisées

```bash
cd backend/ms-utilisateur
mvn verify
```

```bash
cd apps/mobile-client
npm test
npm run lint
npm run typecheck
```

Le guide complet, notamment pour Android, Keycloak et les scénarios BUYER et
SELLER, se trouve dans [`README_DEVELOPPEUR.md`](README_DEVELOPPEUR.md).

## Documentation

- [`user-service`](backend/ms-utilisateur/README.md)
- [Contrat OpenAPI](docs/api/user-service.openapi.yaml)
- [Contrat d'intégration interservices](docs/api/user-service-integration.md)
- [Modèle de données](docs/architecture/user-service-data-model.md)
- [Authentification Keycloak](docs/auth/keycloak.md)
- [Parcours mobile](docs/mobile/auth-profile-flow.md)
- [Décisions d'architecture](docs/decisions/)

Les fichiers `.env`, mots de passe, tokens, kubeconfigs et autres secrets ne
doivent jamais être versionnés. Ne pas utiliser `docker compose down --volumes`
sur un environnement dont les comptes ou profils doivent être conservés.
