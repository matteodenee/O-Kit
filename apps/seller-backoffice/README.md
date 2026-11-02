# O'kit seller backoffice

SPA React et TypeScript réservée aux employés O'kit portant exclusivement le
rôle realm Keycloak `SELLER`. Elle ne contient aucune fonctionnalité catalogue
et ne stocke aucun profil vendeur dans `user-service`.

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

Le backoffice est accessible sur `http://localhost:5173`.

## Authentification

- client public : `okit-seller-web` ;
- flux : Authorization Code avec PKCE S256 ;
- rôle obligatoire : `SELLER` ;
- rôle incompatible : `BUYER` ;
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
