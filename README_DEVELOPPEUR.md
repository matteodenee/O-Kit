# O'Kit — Guide développeur

Ce guide permet de démarrer et valider rapidement le périmètre utilisateur
sans réinitialiser les données locales. Toutes les commandes sont à exécuter
depuis le dépôt `O-Kit`, sauf indication contraire.

## 1. Périmètre validé

`user-service` est propriétaire du profil métier acheteur et de ses adresses de
livraison sauvegardées. Il ne possède ni credentials, ni e-mail persistant, ni
rôle, ni donnée vendeur.

Keycloak possède les comptes et les rôles :

- `BUYER` utilise l'application Expo et `/api/users/**` ;
- `SELLER` utilise uniquement le backoffice ;
- un compte portant `SELLER`, ou simultanément `BUYER` et `SELLER`, reçoit
  `403 Forbidden` sur `user-service`.

Les adresses internationales sont gérées dans une table dédiée, avec une seule
adresse active par défaut et un archivage logique. La wishlist reste différée
jusqu'à la définition du contrat d'identification des produits du catalogue.

## 2. Prérequis

Obligatoires :

- Git ;
- Docker avec la commande `docker compose` ;
- Node.js et npm.

Pour les vérifications complètes :

- un JDK 21 ou supérieur et Maven ;
- Android Studio, le SDK Android et `adb` pour un appareil physique.

## 3. Récupérer la branche à valider

```bash
git clone git@gitlab.com:iwa-group2/o-kit.git
cd o-kit
git fetch origin
git switch feature/user-addresses
git pull --ff-only origin feature/user-addresses
```

Vérifier que le dépôt est propre avant de commencer :

```bash
git status
```

## 4. Configurer l'environnement local

```bash
cp .env.example .env
```

Remplacer dans `.env` les deux valeurs `change-me-local-only` :

```env
KEYCLOAK_ADMIN_USERNAME=admin
KEYCLOAK_ADMIN_PASSWORD=mot_de_passe_admin_local
USER_DB_PASSWORD=mot_de_passe_postgres_local
```

Le fichier `.env` est ignoré par Git. Ne jamais y placer un secret destiné à
la production et ne jamais le commiter.

Le mot de passe PostgreSQL doit rester identique après la création du volume.
Le modifier uniquement dans `.env` empêcherait la base existante de démarrer.

## 5. Démarrer la stack

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

Cinq conteneurs doivent être actifs : `keycloak`, `mailpit`, `postgres`,
`user-service` et `api-gateway`. PostgreSQL, Mailpit, `user-service` et la
gateway doivent devenir `healthy`.

Pour suivre leur démarrage :

```bash
docker compose logs -f keycloak postgres user-service api-gateway
```

Quitter avec `Ctrl+C` ne stoppe pas les conteneurs.

## 6. Réconcilier Keycloak sans perdre les comptes

```bash
set -a
source .env
set +a
./infra/keycloak/reconcile-local-realm.sh
```

Le script est idempotent. Il configure le realm `okit`, les clients publics,
PKCE S256, les rôles `BUYER`/`SELLER`, Mailpit et le thème vendeur sans
réinitialiser le volume Keycloak.

Administration : `http://localhost:8080/admin/`.

## 7. Vérification rapide des services

```bash
curl --fail http://127.0.0.1:8080/realms/okit/.well-known/openid-configuration
curl --fail http://127.0.0.1:8082/health
curl --fail http://127.0.0.1:8082/metrics
curl --fail http://127.0.0.1:8083/health
curl --include http://127.0.0.1:8083/api/users/me
```

Résultats attendus :

- découverte OIDC, healthchecks et métriques : succès HTTP ;
- `/api/users/me` sans token : `401 Unauthorized`.

## 8. Test automatique complet BUYER

Après avoir chargé `.env` comme à l'étape 6 :

```bash
./services/user-service/scripts/test-local-user-flow.sh
```

Le script crée puis nettoie un acheteur temporaire. Il vérifie :

- inscription Keycloak ;
- rôle `BUYER` ;
- Authorization Code avec PKCE ;
- échange et renouvellement des tokens ;
- création et persistance PostgreSQL du profil ;
- reconnexion et accès via la gateway ;
- refus d'un appel sans token.

Résultat final attendu :

```text
Local user flow succeeded: registration, PKCE login, profile persistence, reconnection and token refresh.
```

## 9. Application acheteur dans le navigateur

Dans un nouveau terminal :

```bash
cd apps/mobile-client
cp .env.example .env.local
npm ci
npm run web
```

Ouvrir `http://localhost:8081`, puis :

1. créer un compte ;
2. vérifier le retour automatique depuis Keycloak ;
3. enregistrer le profil ;
4. ouvrir **Gérer mes adresses** ;
5. ajouter une première adresse et vérifier qu'elle est définie par défaut ;
6. ajouter une seconde adresse, la choisir par défaut, puis masquer la première ;
7. recharger la page ;
8. se déconnecter et se reconnecter.

Le profil et les adresses actives doivent être relus depuis PostgreSQL après
rechargement et reconnexion. Pour masquer l'adresse par défaut lorsqu'une autre
adresse existe, choisir d'abord cette autre adresse comme nouvelle valeur par
défaut.

## 10. Application acheteur sur Android

Activer les options développeur et le débogage USB, brancher le téléphone,
puis accepter l'autorisation affichée sur l'appareil.

```bash
adb devices -l
adb reverse tcp:8080 tcp:8080
adb reverse tcp:8081 tcp:8081
adb reverse tcp:8083 tcp:8083
adb reverse --list
```

Depuis `apps/mobile-client` :

```bash
npm ci
npx expo run:android --device
```

Pour les lancements suivants, le build de développement déjà installé peut
être réutilisé :

```bash
npx expo start --dev-client --port 8081
```

Les redirections ADB sont à rejouer après un redémarrage du serveur ADB ou une
reconnexion du téléphone. Le callback `okit://auth` doit revenir directement
à l'application, sans page « Unmatched Route ».

## 11. Backoffice et compte SELLER

Un compte vendeur est créé uniquement par un administrateur. Après avoir
chargé `.env` :

```bash
SELLER_EMAIL='vendeur@example.test' \
SELLER_TEMPORARY_PASSWORD='mot_de_passe_temporaire' \
SELLER_FIRST_NAME='Camille' \
SELLER_LAST_NAME='Martin' \
./infra/keycloak/create-local-seller.sh
```

Puis :

```bash
cd apps/seller-backoffice
cp .env.example .env.local
npm ci
npm run dev
```

Ouvrir `http://localhost:5173`. Le vendeur doit être accepté dans le
backoffice et refusé par `user-service`.

Le scénario automatisé se lance depuis la racine, après chargement de `.env` :

```bash
./services/user-service/scripts/test-local-seller-flow.sh
```

## 12. Mot de passe oublié

1. ouvrir la connexion Keycloak depuis une interface ;
2. choisir « Mot de passe oublié » ;
3. saisir l'e-mail du compte ;
4. ouvrir `http://localhost:8025` ;
5. suivre le lien reçu et définir un nouveau mot de passe.

Mailpit capture seulement les courriels locaux et n'envoie rien sur Internet.

## 13. Vérifications automatisées avant validation

Backend et base isolée Testcontainers :

```bash
cd services/user-service
mvn verify
```

Application acheteur :

```bash
cd apps/mobile-client
npm ci
npm test
npm run lint
npm run typecheck
npx expo-doctor
```

Backoffice vendeur :

```bash
cd apps/seller-backoffice
npm ci
npm run lint
npm run typecheck
npm run build
```

Validation finale depuis la racine :

```bash
docker compose config --quiet
git diff --check
git status
```

## 14. URLs locales

| Composant | URL |
| --- | --- |
| Client Expo Web | `http://localhost:8081` |
| Backoffice vendeur | `http://localhost:5173` |
| Keycloak | `http://localhost:8080` |
| Administration Keycloak | `http://localhost:8080/admin/` |
| Mailpit | `http://localhost:8025` |
| API Gateway | `http://localhost:8083` |
| `user-service` direct | `http://localhost:8082` |

## 15. Arrêt et dépannage

Arrêter sans perdre les comptes ni les profils :

```bash
docker compose down
```

Ne pas utiliser `docker compose down --volumes` : cette commande supprime les
comptes Keycloak, les profils PostgreSQL et les courriels Mailpit.

Logs utiles :

```bash
docker compose logs --tail=200 user-service
docker compose logs --tail=200 api-gateway
docker compose logs --tail=200 keycloak
docker compose logs --tail=200 postgres
```

Si un changement Keycloak n'apparaît pas, relancer le script de réconciliation
au lieu de supprimer les volumes. Si PostgreSQL refuse le mot de passe,
restaurer dans `.env` la valeur utilisée lors de la création du volume.

## 16. Documents de référence

- [`services/user-service/README.md`](services/user-service/README.md)
- [`docs/api/user-service.openapi.yaml`](docs/api/user-service.openapi.yaml)
- [`docs/architecture/user-service-data-model.md`](docs/architecture/user-service-data-model.md)
- [`docs/auth/keycloak.md`](docs/auth/keycloak.md)
- [`docs/mobile/auth-profile-flow.md`](docs/mobile/auth-profile-flow.md)
- [`docs/functional/user-addresses.md`](docs/functional/user-addresses.md)
- [`docs/decisions/0003-user-service-boundary.md`](docs/decisions/0003-user-service-boundary.md)
- [`docs/decisions/0006-buyer-access-to-user-service.md`](docs/decisions/0006-buyer-access-to-user-service.md)
- [`docs/decisions/0008-user-addresses.md`](docs/decisions/0008-user-addresses.md)
