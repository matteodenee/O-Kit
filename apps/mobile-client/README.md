# Client mobile O'kit

Application Expo SDK 57 compatible Android, iOS et aperçu web. Keycloak gère
l'inscription et la connexion avec Authorization Code et PKCE ; `user-service`
conserve ensuite le profil métier et ses adresses de livraison sauvegardées.

## Configuration locale

Les valeurs par défaut correspondent à Docker Compose :

```text
Keycloak    http://localhost:8090
Expo Web   http://localhost:19006
API Gateway http://localhost:8080
```

Pour surcharger les URL sans ajouter de secret :

```bash
cp .env.example .env.local
```

Les variables `EXPO_PUBLIC_*` sont intégrées au bundle et ne doivent jamais
contenir de secret.

## Android physique avec un câble USB

Le flux OIDC natif doit être testé avec le development build O'kit, et non avec
Expo Go. Expo Go ne peut pas garantir le callback stable `okit://auth` déclaré
dans Keycloak.

Le mode recommandé conserve les URL `localhost` du fichier `.env.local` et les
transporte vers le PC avec `adb reverse`. Il évite d'exposer Keycloak et la
gateway sur le Wi-Fi et maintient le même issuer OIDC côté mobile et backend :

```text
EXPO_PUBLIC_KEYCLOAK_URL=http://localhost:8090
EXPO_PUBLIC_API_URL=http://localhost:8080
```

Après avoir démarré Docker Compose depuis la racine du dépôt, brancher le
téléphone avec le débogage USB activé puis lancer :

```bash
cd apps/mobile-client
npm ci
npm run start:android:usb
```

Cette commande vérifie l'issuer, PKCE S256, la santé de la gateway et la
présence du development build `com.okit.mobile`. Elle crée ensuite les tunnels
USB pour Keycloak (`8090`), la gateway (`8080`) et Metro (`19006`), puis ouvre
le build O'kit installé. Pour la première installation seulement :

```bash
npm run android
```

La procédure complète et le diagnostic réseau se trouvent dans
[`docs/devops/mobile-physical-device.md`](../../docs/devops/mobile-physical-device.md).

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

Ouvrir `http://localhost:19006`. Après connexion ou inscription, le formulaire
appelle la gateway pour charger `GET /api/v1/users/me`. Un profil absent peut être
créé par le bouton « Enregistrer mon profil », qui appelle `PUT /api/v1/users/me`.

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
