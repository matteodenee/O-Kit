# O'kit user-service

`user-service` conserve le profil métier de l'acheteur authentifié. Keycloak
reste propriétaire de l'identité, de l'e-mail, du mot de passe, des sessions
et des rôles.

Les routes `/api/v1/users/**` exigent un JWT valide portant le rôle realm
`BUYER`. Le service ne permet d'accéder qu'au profil associé au claim `sub` du
token courant.

## Fonctionnalités disponibles

- consulter son profil avec `GET /api/v1/users/me` ;
- créer ou remplacer son profil avec `PUT /api/v1/users/me` ;
- résoudre son UUID métier sans exposer le profil avec
  `GET /api/v1/users/me/id` ;
- conserver le prénom, le nom, le téléphone et la date de naissance ;
- exécuter les migrations PostgreSQL avec Flyway ;
- exposer `/health` et `/metrics` pour l'exploitation.

Le contrat complet se trouve dans
[`docs/api/user-service.openapi.yaml`](../../docs/api/user-service.openapi.yaml).

Ce périmètre constitue le MVP complet d'authentification et de profil. Les
adresses de livraison et la wishlist sont envisagées pour de nouvelles tranches
fonctionnelles, mais leur rattachement éventuel à `user-service` nécessite une
décision d'architecture et des contrats dédiés. Aucune donnée vendeur ne sera
stockée dans ce service.

## Prérequis

- Docker et Docker Compose ;
- Node.js et npm pour lancer l'aperçu Expo Web ;
- un JDK 21 ou supérieur et Maven pour exécuter les tests Java hors Docker.

Toutes les commandes Docker ci-dessous doivent être lancées depuis la racine
du dépôt `O-Kit`.

## Démarrage rapide

### 1. Préparer la configuration locale

```bash
cp .env.example .env
```

Modifier ensuite `.env` et remplacer les valeurs `change-me-local-only` par
des mots de passe de développement. Le fichier `.env` est ignoré par Git et ne
doit jamais être ajouté au dépôt.

Le mot de passe `USER_DB_PASSWORD` doit rester identique après la première
initialisation du volume PostgreSQL. Le modifier sans mettre à jour la base
existante empêchera `user-service` de démarrer.

### 2. Démarrer l'environnement utilisateur

```bash
docker compose up --build -d keycloak postgres user-service api-gateway
docker compose ps
```

Au premier démarrage, attendre que PostgreSQL soit indiqué comme `healthy` et
que les applications Spring aient fini de démarrer :

```bash
docker compose logs -f keycloak user-service api-gateway
```

Quitter le suivi des logs avec `Ctrl+C` ; les conteneurs restent actifs.

### 3. Vérifier les services

```bash
curl --fail http://127.0.0.1:8090/realms/okit/.well-known/openid-configuration
curl --fail http://127.0.0.1:8081/health
curl --fail http://127.0.0.1:8080/health
curl --include http://127.0.0.1:8080/api/v1/users/me
```

Les trois premières commandes doivent réussir. La dernière doit retourner
`401 Unauthorized`, car aucun token n'est fourni.

| Composant | URL locale | Utilité |
| --- | --- | --- |
| Keycloak | `http://localhost:8090` | inscription et connexion |
| user-service | `http://localhost:8081` | diagnostic direct du service |
| API Gateway | `http://localhost:8080` | point d'entrée des API |
| Expo Web | `http://localhost:19006` | interface acheteur |

## Tester le parcours complet avec l'interface web

Garder les conteneurs précédents actifs, puis ouvrir un deuxième terminal :

```bash
cd apps/mobile-client
npm ci
npm run web
```

Ouvrir `http://localhost:19006`, puis vérifier le scénario suivant :

1. cliquer sur **Créer un compte** ;
2. terminer l'inscription dans Keycloak ;
3. revenir dans l'application ;
4. renseigner prénom, nom, téléphone et éventuellement date de naissance ;
5. cliquer sur **Enregistrer mon profil** ;
6. recharger la page et vérifier que les informations sont relues depuis
   PostgreSQL ;
7. se déconnecter puis se reconnecter avec le même compte.

Une inscription publique doit recevoir le rôle `BUYER`. Un compte sans ce rôle
doit être refusé par le client et recevoir `403 Forbidden` sur l'API
utilisateur.

Un compte vendeur porte directement `SELLER` et ne possède aucun profil dans
ce service. Son rôle technique `default-roles-okit` peut lui faire hériter de
`BUYER`, mais la présence effective de `SELLER` suffit toujours à refuser
l'accès au profil acheteur avec `403 Forbidden`. Un token mal provisionné
portant directement `BUYER` et `SELLER` est également refusé.

### Automatiser le parcours local de bout en bout

Avec les quatre conteneurs actifs, un administrateur Keycloak local peut lancer
le scénario reproductible suivant depuis la racine du dépôt :

```bash
KEYCLOAK_ADMIN_USERNAME=admin \
KEYCLOAK_ADMIN_PASSWORD='mot-de-passe-local' \
./backend/ms-utilisateur/scripts/test-local-user-flow.sh
```

Le script réconcilie d'abord le realm, inscrit un acheteur temporaire via le
flux Authorization Code avec PKCE, vérifie le rôle `BUYER`, crée puis relit son
profil par la gateway, se déconnecte, se reconnecte, renouvelle l'access token
et nettoie le compte et le profil de test. Les tokens et mots de passe générés
ne sont jamais affichés.

## Exécuter les vérifications automatisées

### Backend Java

Depuis `backend/ms-utilisateur` :

```bash
mvn verify
```

Cette commande compile le service, exécute les tests et construit le JAR dans
`target/`. Docker doit être démarré : les tests d'intégration lancent une base
PostgreSQL isolée avec Testcontainers. La construction de l'image exécute les
tests unitaires et reporte ces tests d'intégration à la vérification Maven sur
l'hôte, où le daemon Docker est accessible.

### Application React Native / Expo

Depuis `apps/mobile-client` :

```bash
npm ci
npm test
npm run lint
npm run typecheck
npx expo-doctor
```

`npm ci` réinstalle exactement les versions présentes dans
`package-lock.json`.

### Vérification Docker Compose

Depuis la racine du dépôt :

```bash
docker compose config --quiet
docker compose ps
```

## Arrêter l'environnement

Arrêter les conteneurs en conservant les comptes et profils locaux :

```bash
docker compose down
```

Les volumes PostgreSQL et Keycloak sont conservés et seront réutilisés au
prochain démarrage.

## Dépannage

### `password authentication failed for user "okit_user"`

Ce message indique généralement que `USER_DB_PASSWORD` a changé après la
création du volume PostgreSQL.

- si les données doivent être conservées, remettre dans `.env` le mot de passe
  utilisé lors de l'initialisation ;
- si les données locales sont jetables, réinitialiser entièrement
  l'environnement avec la commande destructive ci-dessous.

```bash
docker compose down --volumes
docker compose up --build -d keycloak postgres user-service api-gateway
```

`docker compose down --volumes` supprime les profils, les comptes Keycloak et
les sessions de développement. Ne jamais l'utiliser sur un environnement
partagé ou contenant des données à conserver.

### Une modification de `okit-realm.json` n'apparaît pas

Keycloak importe le realm seulement s'il n'existe pas encore. Un volume
Keycloak déjà initialisé conserve donc l'ancienne configuration. Utiliser une
migration Keycloak ou, uniquement pour des données locales jetables, la
réinitialisation des volumes décrite ci-dessus.

### Le mot de passe administrateur Keycloak est refusé

`KC_BOOTSTRAP_ADMIN_PASSWORD` sert uniquement à créer le premier compte
administrateur d'un stockage Keycloak vide. Modifier
`KEYCLOAK_ADMIN_PASSWORD` dans `.env` ne change pas le mot de passe d'un compte
déjà conservé dans le volume. Réutiliser le mot de passe d'origine ou suivre
la procédure d'administration Keycloak prévue par l'équipe ; ne pas supprimer
un volume partagé pour contourner ce problème.

### Consulter les logs d'un composant

```bash
docker compose logs --tail=200 user-service
docker compose logs --tail=200 postgres
docker compose logs --tail=200 keycloak
docker compose logs --tail=200 api-gateway
```

Ne jamais copier un token complet ou un secret dans un ticket, un commit ou un
message partagé.

## Variables de configuration

| Variable | Description |
| --- | --- |
| `USER_DB_URL` | URL JDBC PostgreSQL complète, facultative et prioritaire |
| `USER_DB_HOST` | hôte PostgreSQL, `localhost` par défaut |
| `USER_DB_PORT` | port PostgreSQL, `5432` par défaut |
| `USER_DB_NAME` | base logique PostgreSQL, `okit_users` par défaut |
| `USER_DB_USERNAME` | utilisateur PostgreSQL |
| `USER_DB_PASSWORD` | mot de passe PostgreSQL obligatoire |
| `OIDC_ISSUER_URI` | issuer du realm Keycloak |
| `OIDC_JWK_SET_URI` | endpoint des clés publiques Keycloak |
| `SERVER_PORT` | port HTTP du service, `8081` par défaut |

## Kubernetes

Les manifests et la procédure de déploiement se trouvent dans
[`k8s/services/ms-utilisateur/README.md`](../../k8s/services/ms-utilisateur/README.md).
Le Service Kubernetes reste interne au cluster : Spring Cloud Gateway publie
`/api/v1/users/**` et centralise le CORS.

## Documentation associée

- [Contrat OpenAPI](../../docs/api/user-service.openapi.yaml)
- [Modèle de données](../../docs/architecture/user-service-data-model.md)
- [Parcours mobile et profil](../../docs/mobile/auth-profile-flow.md)
- [Configuration Keycloak](../../docs/auth/keycloak.md)
- [Déploiement Kubernetes](../../docs/devops/user-service-kubernetes.md)
