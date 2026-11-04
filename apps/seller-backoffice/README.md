# O'kit seller backoffice

SPA React et TypeScript réservée aux employés O'kit auxquels le rôle realm
Keycloak `SELLER` est attribué directement, sans attribution directe de
`BUYER`. Le mapping technique `default-roles-okit` est conservé. Elle permet de
consulter et modifier les produits exposés par le microservice Catalogue, sans
stocker de profil vendeur dans `user-service`.

## Démarrage local

Depuis la racine du dépôt, démarrer et réconcilier Keycloak :

```bash
docker compose up -d keycloak
set -a
source .env
set +a
./infra/keycloak/reconcile-local-realm.sh
```

Puis :

```bash
cd apps/seller-backoffice
cp .env.example .env.local
npm ci
npm run dev
```

Le backoffice est accessible sur `http://localhost:5173`, utilise Keycloak sur
`http://localhost:8090` et appelle l'API Gateway sur `http://localhost:8080`.

La gestion Catalogue permet la liste, le détail, la modification, le statut et
la disponibilité globale. La création attend encore les endpoints de lecture
des référentiels requis par l'API. Le périmètre et cette limite sont détaillés
dans [`docs/seller/catalogue-management.md`](../../docs/seller/catalogue-management.md).

## Authentification

- client public : `okit-seller-web` ;
- flux : Authorization Code avec PKCE S256 ;
- rôle directement attribué obligatoire : `SELLER` ;
- rôle directement attribué incompatible : `BUYER` ;
- claim signé utilisé : `okit_direct_realm_roles` ;
- création du compte : administration Keycloak uniquement ;
- stockage des tokens : mémoire uniquement, géré par `keycloak-js`.

La procédure de création d'un vendeur est décrite dans
[`docs/seller/authentication-flow.md`](../../docs/seller/authentication-flow.md).
Le script local `infra/keycloak/create-local-seller.sh` automatise cette
opération tout en exigeant les identifiants d'un administrateur.

## Vérifications

```bash
npm run lint
npm run typecheck
npm run build
```

Depuis la racine du dépôt, le parcours Keycloak vendeur peut être validé par :

```bash
set -a
source .env
set +a
./backend/ms-utilisateur/scripts/test-local-seller-flow.sh
```
