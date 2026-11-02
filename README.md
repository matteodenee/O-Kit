# O'Kit

O'Kit est une application de m-commerce. Le périmètre utilisateur livré couvre
l'authentification centralisée par Keycloak, le profil métier acheteur dans
`user-service`, la gestion des adresses de livraison, l'application mobile Expo
et l'authentification du backoffice vendeur.

## État du MVP utilisateur

Le MVP d'authentification et de profil de `user-service` est terminé :

- `GET /api/users/me` consulte le profil de l'acheteur courant ;
- `PUT /api/users/me` crée ou remplace ce profil ;
- PostgreSQL conserve prénom, nom, téléphone et date de naissance facultative ;
- Keycloak reste propriétaire des comptes, mots de passe, e-mails, sessions et
  rôles ;
- l'application mobile accepte exclusivement `BUYER` ;
- le backoffice accepte exclusivement `SELLER` ;
- un vendeur ne possède aucune donnée dans `user-service`.

La tranche adresses appartient également à `user-service` : un acheteur peut
enregistrer plusieurs adresses internationales, choisir celle par défaut et
masquer celles qu'il n'utilise plus depuis l'application Expo ou son aperçu
web. La wishlist reste différée jusqu'à stabilisation des identifiants définis
par le domaine catalogue.

## Architecture livrée

| Composant | Rôle | URL locale |
| --- | --- | --- |
| Keycloak | Identités, rôles et parcours OIDC | `http://localhost:8080` |
| Mailpit | Courriels de développement | `http://localhost:8025` |
| PostgreSQL | Base propre à `user-service` | `localhost:5432` |
| `user-service` | Profil et adresses de l'acheteur | `http://localhost:8082` |
| `api-gateway` | Entrée des API | `http://localhost:8083` |
| Client Expo | Application acheteur | `http://localhost:8081` |
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
curl --fail http://127.0.0.1:8080/realms/okit/.well-known/openid-configuration
curl --fail http://127.0.0.1:8082/health
curl --fail http://127.0.0.1:8083/health
curl --include http://127.0.0.1:8083/api/users/me
./services/user-service/scripts/test-local-user-flow.sh
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
cd services/user-service
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

- [`user-service`](services/user-service/README.md)
- [Contrat OpenAPI](docs/api/user-service.openapi.yaml)
- [Modèle de données](docs/architecture/user-service-data-model.md)
- [Authentification Keycloak](docs/auth/keycloak.md)
- [Parcours mobile](docs/mobile/auth-profile-flow.md)
- [Gestion des adresses](docs/functional/user-addresses.md)
- [Décisions d'architecture](docs/decisions/)

Les fichiers `.env`, mots de passe, tokens, kubeconfigs et autres secrets ne
doivent jamais être versionnés. Ne pas utiliser `docker compose down --volumes`
sur un environnement dont les comptes ou profils doivent être conservés.
