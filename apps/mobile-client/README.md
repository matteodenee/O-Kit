# Client mobile O'kit

Application Expo SDK 57 compatible Android, iOS et aperçu web. Keycloak gère
l'inscription et la connexion avec Authorization Code et PKCE ; `user-service`
conserve ensuite le profil métier et ses adresses de livraison sauvegardées.

## Configuration locale

Les valeurs par défaut correspondent à Docker Compose :

```text
Keycloak    http://localhost:8080
Expo Web   http://localhost:8081
API Gateway http://localhost:8083
```

Pour surcharger les URL sans ajouter de secret :

```bash
cp .env.example .env.local
```

Les variables `EXPO_PUBLIC_*` sont intégrées au bundle et ne doivent jamais
contenir de secret.

## Aperçu web

Depuis la racine du dépôt, démarrer d'abord le backend :

```bash
docker compose up --build keycloak postgres user-service api-gateway
```

Puis, dans `apps/mobile-client` :

```bash
npm ci
npm run web
```

Ouvrir `http://localhost:8081`. Après connexion ou inscription, le formulaire
appelle la gateway pour charger `GET /api/users/me`. Un profil absent peut être
créé par le bouton « Enregistrer mon profil », qui appelle `PUT /api/users/me`.

Une fois le profil créé, **Gérer mes adresses** ouvre la route Expo Router
`/addresses`. Elle permet de créer et modifier une adresse internationale, de
choisir l'adresse par défaut et de masquer une adresse après confirmation. Le
même écran fonctionne sur Android, iOS et dans l'aperçu web.

Le client renouvelle automatiquement l'access token avant expiration et retente
une seule fois un appel ayant reçu `401`. Les sessions natives sont conservées
dans SecureStore. L'aperçu web utilise uniquement `sessionStorage`, afin qu'une
session survive à un rechargement mais pas à la fermeture de l'onglet.

Le client exige exclusivement le rôle realm `BUYER`. Un compte vendeur
`SELLER`, y compris un compte portant accidentellement les deux rôles, est
refusé avant l'accès au profil.

## Vérifications

```bash
npm test
npm run lint
npm run typecheck
npx expo-doctor
```

Le workflow fonctionnel complet est documenté dans
[`docs/mobile/auth-profile-flow.md`](../../docs/mobile/auth-profile-flow.md).
