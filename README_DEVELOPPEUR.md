# O'Kit — Guide développeur local (Ubuntu, Android USB et backoffice vendeur)

> **But : copier-coller les commandes de ce guide pour utiliser O'Kit sur un téléphone Android physique et ouvrir le site vendeur.**
>
> **Configuration de référence validée le 2 novembre 2026 :** API Gateway **8080**, microservice Utilisateur **8081**, Keycloak **8090**, Expo/Metro **19006**, PostgreSQL **5432**, Mailpit **8025**, backoffice vendeur **5173**. **Ne pas réutiliser les anciennes instructions qui indiquent Metro 8081, Keycloak 8080 ou Gateway 8083.**
>
> Guide fondé sur le parcours Android effectivement réalisé avec succès. Les commandes du backoffice reposent sur la documentation du dépôt disponible ; le parcours vendeur n'a pas été retesté dans cette session. Les ports et scripts doivent rester cohérents avec la branche réellement utilisée.

## 1. Comprendre le démarrage en 30 secondes

- **Docker** lance PostgreSQL, Keycloak, le service Utilisateur et l'API Gateway.
- **Android/ADB** relie le téléphone au PC par USB et redirige les adresses `localhost` du téléphone vers le PC.
- **Expo/Metro** sert le code JavaScript de l'application mobile sur **19006**.
- **Le backoffice vendeur** est un serveur web distinct, accessible dans le navigateur du PC sur **5173**.
- **Une seule instance de Metro doit écouter sur 19006.** Si Expo propose un autre port, ne pas accepter automatiquement : diagnostiquer l'occupant de 19006.

`user-service` est propriétaire du profil métier acheteur et de ses adresses de
livraison sauvegardées. Il ne possède ni credentials, ni e-mail persistant, ni
rôle, ni donnée vendeur.

| Composant | Adresse sur le PC | Pour le téléphone en USB |
|---|---|---|
| API Gateway | `http://localhost:8080` | `adb reverse tcp:8080 tcp:8080` |
| Service Utilisateur | `http://localhost:8081` | Accès **via la Gateway**, pas d'ADB reverse direct nécessaire |
| Keycloak | `http://localhost:8090` | `adb reverse tcp:8090 tcp:8090` |
| Expo / Metro | `http://127.0.0.1:19006` | `adb reverse tcp:19006 tcp:19006` |
| PostgreSQL | `localhost:5432` | Pas d'accès direct depuis Android |
| Mailpit | `http://localhost:8025` | Accessible dans le navigateur du PC |
| Backoffice vendeur | `http://localhost:5173` | Site à ouvrir dans le navigateur du PC |

**Important :** `localhost` désigne normalement le téléphone **quand le code tourne sur Android**. Les commandes `adb reverse` font fonctionner ces mêmes URL en redirigeant le trafic vers le PC **tant que la connexion USB est active**. Cette procédure n'est pas une procédure « Wi-Fi sans câble ».

Les adresses internationales sont gérées dans une table dédiée, avec une seule
adresse active par défaut et un archivage logique. La wishlist reste différée
jusqu'à la définition du contrat d'identification des produits du catalogue.

## 2. Préparation du PC — à faire une seule fois

### 2.1 Versions et outils

Environnement Ubuntu utilisé pendant la reproduction : Ubuntu 24.04.5 LTS, JDK **21**, Node.js 24, npm 11, Docker + Compose, Git, ADB, SDK Android API 36, NDK **27.1.12297006**, CMake **3.22.1**. Les versions Node/npm ont été observées sur un autre PC de préparation, mais leur compatibilité avec **cette branche précise** doit être confirmée lors de `npm ci` et du build.

Installer les utilitaires disponibles dans les dépôts Ubuntu :

```bash
sudo apt update
sudo apt install -y git curl adb unzip wget openjdk-21-jdk iproute2 psmisc
```

Vérifier les autres prérequis **avant** de continuer :

```bash
git --version
java -version
node -v
npm -v
docker --version
docker compose version
docker info >/dev/null && echo 'Docker opérationnel'
adb version
```

Si `node`, `npm`, `docker` ou `docker compose` manquent, les installer avant de continuer, depuis leurs distributions officielles. Les commandes ci-dessus **ne les installent pas**. Docker doit être accessible depuis ce compte utilisateur, sans `sudo` pour chaque commande.

### 2.2 Java 21 (ne pas compiler avec un autre Java par inadvertance)

Une développeuse a rencontré des erreurs `configureCMakeDebug` avec Java 25 ; le retour vers Java 21 lui a permis de poursuivre. Ce constat **ne prouve pas** que Java 25 est systématiquement incompatible, mais **Java 21 est notre version de référence**.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Pour conserver le réglage dans les nouveaux terminaux Bash, ajouter **une seule fois** à `~/.bashrc` (ne pas dupliquer ces lignes si elles existent déjà) :

```bash
grep -qxF 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' ~/.bashrc || echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' >> ~/.bashrc
```

Après le clonage du dépôt, vérifier aussi `./gradlew --version` depuis `apps/mobile-client/android` : **la JVM Gradle** doit être Java 21.

### 2.3 SDK Android : SDK, NDK, CMake et sdkmanager

Le SDK est généralement installé dans `~/Android/Sdk`. Vérifier :

```bash
ls -la "$HOME/Android/Sdk"
ls "$HOME/Android/Sdk/platforms" "$HOME/Android/Sdk/build-tools"
```

Si le SDK ou les Android SDK Command-line Tools n'existent pas, les installer par **Android Studio → SDK Manager**, ou télécharger les **Command-line Tools pour Linux** depuis https://developer.android.com/studio#command-tools, puis disposer `sdkmanager` dans `~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager`. **Ne pas utiliser aveuglément une ancienne URL de ZIP : elle peut changer.**

Une fois `sdkmanager` présent :

```bash
"$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$HOME/Android/Sdk" \
  'platforms;android-36' 'build-tools;36.0.0' 'ndk;27.1.12297006' 'cmake;3.22.1' 'platform-tools'
```

> La compilation antérieure annonçait Build Tools **36.0.0** et API **36** ; un autre PC possédait aussi 36.1.0. Installer les composants exactement exigés par Gradle si celui-ci demande une variante spécifique. Accepter les licences demandées par `sdkmanager`.

Définir `ANDROID_HOME` pour la session :

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
```

Pour les prochains terminaux Bash :

```bash
grep -qxF 'export ANDROID_HOME="$HOME/Android/Sdk"' ~/.bashrc || echo 'export ANDROID_HOME="$HOME/Android/Sdk"' >> ~/.bashrc
```

Vérifier :

```bash
printf 'JAVA_HOME=%s\nANDROID_HOME=%s\n' "$JAVA_HOME" "$ANDROID_HOME"
ls "$ANDROID_HOME/ndk/27.1.12297006" "$ANDROID_HOME/cmake/3.22.1" >/dev/null && echo 'NDK et CMake trouvés'
```

## 3. Cloner le dépôt et choisir la bonne branche

Le dépôt est privé : utiliser **son propre accès GitLab**. La branche de
référence fonctionnelle est `feature/user-addresses`. Vérifier auprès de
l'équipe que la branche d'intégration validée a bien été fusionnée et poussée
avant de préparer un nouveau poste.

```bash
mkdir -p "$HOME/IG5/iwa"
cd "$HOME/IG5/iwa"
git clone https://gitlab.com/iwa-group2/o-kit.git O-Kit
cd O-Kit
git status -sb
```

Pour utiliser la branche fonctionnelle utilisateur :

```bash
git fetch origin
git switch feature/user-addresses
git pull --ff-only origin feature/user-addresses
```

**Toutes les commandes suivantes qui utilisent `cd` sont à exécuter depuis la racine `O-Kit`**, sauf si le texte indique explicitement le contraire. Le répertoire doit contenir `compose.yaml`.

## 4. Fichiers d'environnement — première fois uniquement

### 4.1 Docker

Depuis **la racine `O-Kit`** :

```bash
cp -n .env.example .env
```

Ouvrir `.env` et renseigner **les champs requis par `.env.example`**, notamment les identifiants locaux d'administration Keycloak et les informations `USER_DB_*`. Ne jamais coller de mots de passe dans Git ou dans le README.

```bash
nano .env
```

> `cp -n` évite d'écraser un `.env` existant. Si PostgreSQL possède déjà un volume initialisé, changer le mot de passe dans `.env` ne remplace pas automatiquement celui de la base existante.

### 4.2 Mobile

```bash
cd apps/mobile-client
cp -n .env.example .env.local
```

Vérifier les deux paramètres du **mode USB** :

```bash
grep -E '^EXPO_PUBLIC_(KEYCLOAK_URL|API_URL)=' .env.local
```

Ils doivent être **exactement** :

```dotenv
EXPO_PUBLIC_KEYCLOAK_URL=http://localhost:8090
EXPO_PUBLIC_API_URL=http://localhost:8080
```

Si nécessaire, modifier `.env.local` :

```bash
nano .env.local
```

Ne mettre **aucun secret** dans les variables `EXPO_PUBLIC_*` : elles sont accessibles depuis l'application cliente. Revenir à la racine :

```bash
cd ../..
```

### 4.3 Backoffice vendeur

Ne pas recopier mécaniquement les URLs du mobile : le backoffice tourne **dans le navigateur du PC**. Ses variables doivent correspondre à `apps/seller-backoffice/.env.example`, notamment à Keycloak **8090** et à l'API Gateway **8080** si le frontend les utilise.

```bash
cd apps/seller-backoffice
cp -n .env.example .env.local
cat .env.example
nano .env.local
cd ../..
```

La configuration complète et les noms exacts des variables vendeur doivent être lus depuis ce `.env.example` : ils n'étaient pas fournis dans les logs de la reproduction Android.

## 5. Démarrer le backend Docker — à chaque session

**Depuis la racine du dépôt** :

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

Attendre la fin du démarrage (les statuts `health: starting` juste après `up` sont normaux). La procédure validée a donné : Gateway `healthy` sur **8080**, service Utilisateur `healthy` sur **8081**, Keycloak sur **8090**, PostgreSQL et Mailpit `healthy`.

Vérifications simples **sans suites de tests automatisés** :

```bash
curl -sS -o /dev/null -w 'Keycloak HTTP %{http_code}\n' http://localhost:8090/realms/okit/.well-known/openid-configuration
curl -sS -o /dev/null -w 'Gateway HTTP %{http_code}\n' http://localhost:8080/health
```

Les deux réponses attendues sont **HTTP 200**. Pour vérifier la déclaration PKCE de Keycloak :

```bash
curl -sS http://localhost:8090/realms/okit/.well-known/openid-configuration | python3 -c 'import json,sys; d=json.load(sys.stdin); print("Issuer:",d.get("issuer")); print("PKCE:",d.get("code_challenge_methods_supported"))'
```

Attendu : `issuer` = `http://localhost:8090/realms/okit`, et la liste PKCE contient **`S256`**.

Si le realm n'est pas encore configuré **et si le script existe sur votre branche**, procéder à la réconciliation locale **après avoir préparé `.env`** :

```bash
set -a
source .env
set +a
./infra/keycloak/reconcile-local-realm.sh
```

Si un service ne répond pas :

```bash
docker compose ps
docker compose logs --tail=100 keycloak
docker compose logs --tail=100 api-gateway
docker compose logs --tail=100 user-service
```

**Ne jamais utiliser `docker compose down --volumes` pour résoudre un problème de démarrage** : cela peut effacer des données locales. L'avertissement Docker « Bake / buildx isn't installed » n'a pas empêché les images de se construire lors de la reproduction.

### 5.1 Lancer l'application acheteur dans un navigateur

Toutes les commandes de cette procédure partent de la racine du dépôt `O-Kit`.

#### Démarrer les services

```bash
docker compose up -d --build
```

Attendre que PostgreSQL, Keycloak, les microservices Utilisateur et Catalogue,
ainsi que l'API Gateway soient opérationnels. Leur état peut être contrôlé avec
`docker compose ps`.

#### Configurer Keycloak si nécessaire

Si le realm local n'a pas encore été réconcilié, charger les variables du
fichier `.env`, puis exécuter le script prévu à cet effet :

```bash
set -a
source .env
set +a

./infra/keycloak/reconcile-local-realm.sh
```

#### Remplir le catalogue avec les produits de démonstration

Après le démarrage du microservice Catalogue et la création de son schéma,
exécuter :

```bash
docker compose exec postgres /opt/okit/seed-catalogue-demo.sh
```

Le script insère 18 produits actifs et 1 produit désactivé. Il est idempotent :
il peut être exécuté plusieurs fois sans créer de doublons. Il est réservé aux
environnements de développement et de démonstration et ne doit pas être lancé
automatiquement en production.

#### Démarrer Expo Web

Depuis la racine du dépôt :

```bash
cd apps/mobile-client
npm ci
npm run web
```

Ouvrir ensuite [http://localhost:19006](http://localhost:19006) dans le
navigateur. L'utilisateur peut créer un compte ou se connecter via Keycloak,
puis consulter le catalogue. Le serveur Expo doit rester actif dans son
terminal pendant toute l'utilisation de l'application.

## 6. Première installation de l'application sur le téléphone Android

**Cette section n'est nécessaire que si O'Kit n'est pas encore installé, ou si un changement natif exige un nouveau build.**

### 6.1 Activer le débogage USB

Sur le téléphone : activer les **options développeur**, puis **Débogage USB** ; brancher un câble **données** et accepter la demande d'autorisation RSA. Sur certains Xiaomi/Redmi, l'option « Installer via USB » et une confirmation sur le téléphone peuvent être nécessaires.

```bash
adb devices -l
```

Le téléphone doit apparaître avec l'état `device`. `unauthorized` signifie qu'il faut accepter la demande sur le téléphone. Une liste vide invite à vérifier câble, prise et réglages USB.

### 6.2 Installer les dépendances mobiles

**Depuis la racine `O-Kit`** :

```bash
cd apps/mobile-client
npm ci
```

`npm ci` installe les dépendances sur le PC ; **il ne produit pas le QR code** et **n'installe pas l'APK**. Ne pas lancer `npm audit fix --force` pour résoudre un problème Expo.

Vérifier que Gradle tourne avec Java 21 :

```bash
cd android
./gradlew --version
cd ..
```

### 6.3 Compiler et installer le development build

```bash
npm run android
```

Cette commande est fournie par le `package.json` du client mobile dans la branche documentée. Elle doit sélectionner le téléphone, construire l'APK et installer **`com.okit.mobile`**. Si elle démarre aussi Metro, **ne pas lancer une seconde instance Expo en parallèle**. Quand le build est terminé, arrêter le Metro éventuellement lancé avec `Ctrl+C` avant la section 7.

Signaux attendus : `BUILD SUCCESSFUL`, puis installation d'un `app-debug.apk`. La première compilation peut être longue. Si Android affiche **`Install cancelled by user`**, garder l'écran déverrouillé et accepter les autorisations d'installation USB ; il ne s'agit pas d'un problème Keycloak.

Contrôler l'installation :

```bash
adb shell pm list packages com.okit.mobile
```

Attendu : `package:com.okit.mobile`.

Revenir à la racine :

```bash
cd ../..
```

## 7. Démarrer le mobile sur Android — à chaque session

> **Cette séquence a été exécutée avec succès le 2 novembre 2026 sur un Redmi branché en USB :** Docker OK, Keycloak HTTP 200, Gateway HTTP 200, script ADB OK, Metro `127.0.0.1:19006`, bundle Android compilé et interface O'Kit fonctionnelle.

### 7.1 Libérer **proprement** le port 19006

Dans le terminal qui exécute déjà Expo, faire **`Ctrl+C`**. Vérifier ensuite :

```bash
ss -ltnp '( sport = :19006 )'
```

**Aucune ligne `LISTEN`** = port libre. Si un processus est affiché, vérifier qu'il s'agit bien de **votre ancien Metro O'Kit**, puis l'arrêter avec `Ctrl+C` dans son terminal ; s'il est orphelin, utiliser `kill <PID>` **après identification**. Ne pas tuer aveuglément un processus d'un autre projet.

> Ne pas accepter qu'Expo bascule sur **19007**, **19008** ou un autre port : le script et les tunnels USB sont configurés pour **19006**. Une commande `fuser -k 19006/tcp` tuerait **tout processus** occupant le port ; elle n'est donc pas automatisée dans ce guide.

### 7.2 Préparer le téléphone et les tunnels

Brancher et déverrouiller le téléphone. **Depuis la racine du dépôt** :

```bash
adb devices -l
./apps/mobile-client/scripts/prepare-android-usb.sh
```

Le script existant vérifie `adb`, `curl`, `node`, les valeurs de `.env.local`, l'installation de `com.okit.mobile`, l'issuer Keycloak, PKCE S256 et la santé de la Gateway. Il crée ensuite :

```text
UsbFfs tcp:8090 tcp:8090
UsbFfs tcp:8080 tcp:8080
UsbFfs tcp:19006 tcp:19006
```

**Si vous voyez la confirmation seulement pour certains ports**, vérifier **`adb reverse --list`** : seule la présence effective des trois tunnels compte.

**Si le script refuse de fonctionner parce que l'application n'est pas installée**, revenir à la section 6 : il est volontairement destiné aux démarrages après installation.

### 7.3 Démarrer Metro sur une adresse accessible par le câble USB

**Depuis la racine `O-Kit`** :

```bash
cd apps/mobile-client
npx expo start --dev-client --localhost --port 19006
```

**Laisser ce terminal ouvert.** Le QR code doit indiquer une adresse de type :

```text
exp+mobile://expo-development-client/?url=http%3A%2F%2F127.0.0.1%3A19006
```

Si Expo annonce une adresse **`172.x.x.x`** ou une autre IP du PC, vérifier que l'ancien Metro est bien arrêté et que la commande contient **`--localhost`**. Si Expo propose un autre port, revenir à la section 7.1.

Sur le téléphone : ouvrir **l'application O'Kit installée**, pas Expo Go ; dans le *development launcher*, sélectionner le serveur courant ou scanner son **nouveau QR code**. Ne pas rouvrir une ancienne session utilisant une IP réseau obsolète.

Indicateur positif dans le terminal :

```text
Android Bundled ... node_modules/expo-router/entry.js
```

### 7.4 Si l'application affiche « Failed to connect »

**Dans un deuxième terminal du PC**, pendant que Metro fonctionne :

```bash
curl -sS http://127.0.0.1:19006/status
adb reverse --list
```

La première commande doit afficher `packager-status:running` ; la seconde doit contenir **8090, 8080, 19006**.

**Sur le téléphone, dans Chrome**, ouvrir :

```text
http://127.0.0.1:19006/status
```

- **PC et téléphone OK** : Metro/USB fonctionnent. Revenir dans O'Kit et sélectionner le serveur **127.0.0.1:19006**, pas une session mémorisée en `172.x.x.x`.
- **PC OK, téléphone KO** : vérifier le câble, l'état `device`, puis refaire les tunnels via `prepare-android-usb.sh` ; ne pas oublier qu'ils peuvent disparaître après débranchement ou redémarrage ADB.
- **PC KO** : Metro n'écoute pas au bon port ; vérifier le terminal Expo et les conflits du port 19006.

> L'erreur Linux `React Native DevTools ... chrome-sandbox` a été observée alors que Metro continuait de servir correctement l'application. Si le QR code et `Android Bundled` apparaissent, **ne pas changer les permissions système/SUID au hasard** pour corriger DevTools.

### 7.5 Vérifier l'authentification mobile

Sur l'application : inscription ou connexion **BUYER** → page Keycloak → retour vers O'Kit via **`okit://auth`** → accès au profil. L'application doit communiquer avec **Keycloak 8090** et **Gateway 8080** via ADB reverse.

Si Keycloak ne s'ouvre pas :

```bash
curl -sS -o /dev/null -w '%{http_code}\n' http://localhost:8090/realms/okit/.well-known/openid-configuration
adb reverse --list
```

Si le login fonctionne mais pas le profil, vérifier `.env.local`, les logs `api-gateway` et `user-service`, et la route actuelle **`/api/v1/users/**`**. Une réponse **401 sans JWT** ou **403 avec un rôle non autorisé** n'est pas une erreur Metro.

### 7.6 Vérifier la gestion des adresses

Après avoir enregistré le profil de l'acheteur :

1. ouvrir **Gérer mes adresses** ;
2. créer une première adresse et vérifier qu'elle devient celle par défaut ;
3. créer une seconde adresse et la choisir comme nouvelle adresse par défaut ;
4. modifier une adresse ;
5. masquer l'ancienne adresse et vérifier qu'elle disparaît ;
6. recharger l'application, puis se déconnecter et se reconnecter ;
7. vérifier que les adresses actives et le choix par défaut sont conservés.

Masquer l'adresse par défaut doit être refusé lorsqu'une autre adresse active
existe : choisir d'abord cette autre adresse comme nouvelle valeur par défaut.
Un second compte `BUYER` ne doit jamais voir les adresses du premier compte.

Le même parcours peut être vérifié dans l'aperçu web. Arrêter d'abord le Metro
Android qui occupe `19006`, puis depuis `apps/mobile-client` :

```bash
npm run web
```

Ouvrir `http://localhost:19006`.

## 8. Lancer le site vendeur (backoffice)

**Ouvrir un deuxième terminal ; ne pas arrêter Docker ni Metro pour cela.** Depuis la racine du dépôt :

```bash
cd apps/seller-backoffice
cp -n .env.example .env.local
npm ci
npm run dev
```

Ouvrir dans le navigateur du PC : **http://localhost:5173**. Laisser le terminal Vite ouvert.

Après connexion, ouvrir **Catalogue** dans la barre latérale. La liste doit
contenir les produits actifs et désactivés. La fiche permet de modifier les
champs pris en charge, le statut et la disponibilité globale. La création reste
désactivée tant que le microservice ne fournit pas les référentiels nécessaires.

**Compte vendeur :** un compte `SELLER` doit être provisionné par l'administrateur ; un compte `BUYER` ne suffit pas pour accéder au backoffice. Pour créer un vendeur local, **dans un autre terminal depuis la racine `O-Kit`** :

```bash
set -a
source .env
set +a
SELLER_EMAIL='vendeur@example.test' \
SELLER_TEMPORARY_PASSWORD='A_REMPLACER_PAR_UN_MOT_DE_PASSE_LOCAL' \
SELLER_FIRST_NAME='Camille' \
SELLER_LAST_NAME='Martin' \
./infra/keycloak/create-local-seller.sh
```

Remplacer les données d'exemple et le mot de passe par les valeurs locales souhaitées. Lire `infra/keycloak/README.md` si le script demande des conditions supplémentaires. Cette procédure ne doit pas être utilisée pour créer un vrai compte de production.

Pour les sessions suivantes, **`npm ci` est inutile** si les dépendances n'ont pas changé :

```bash
cd apps/seller-backoffice
npm run dev
```

Si Vite propose **5174** parce que **5173** est occupé, vérifier l'ancien serveur avec `ss -ltnp '( sport = :5173 )'`, arrêter uniquement l'ancienne instance connue, puis relancer `npm run dev`. Ne pas changer les URLs de redirection Keycloak à l'aveugle.

**Mailpit**, pour consulter les mails locaux (notamment la récupération de mot de passe) : **http://localhost:8025**.

## 9. Démarrage rapide — après la première installation

Voici les commandes à recopier **les jours suivants**.

### Terminal 1 — Docker

```bash
cd "$HOME/IG5/iwa/O-Kit"
docker compose up -d
docker compose ps
```

### Terminal 2 — Mobile Android USB

Brancher et déverrouiller le téléphone, puis :

```bash
cd "$HOME/IG5/iwa/O-Kit"
ss -ltnp '( sport = :19006 )'
./apps/mobile-client/scripts/prepare-android-usb.sh
cd apps/mobile-client
npx expo start --dev-client --localhost --port 19006
```

**Si 19006 est déjà occupé**, ne pas lancer la dernière commande avant d'avoir arrêté l'ancienne instance Metro identifiée. Ensuite, ouvrir O'Kit sur le téléphone.

> **Alternative prévue par le projet :** `npm run start:android:usb` depuis `apps/mobile-client` réalise les vérifications USB et démarre normalement Metro. Ce script est documenté dans les changements Codex, mais **la séquence manuelle ci-dessus est celle validée pas à pas dans cette session**. Ne pas exécuter les deux lancements simultanément.

### Terminal 3 — Site vendeur

```bash
cd "$HOME/IG5/iwa/O-Kit/apps/seller-backoffice"
npm run dev
```

Ouvrir **http://localhost:5173**.

## 10. Dépannage express

| Problème observé | Action |
|---|---|
| `package.json does not exist` | Exécuter les commandes Expo depuis **`apps/mobile-client`**, pas la racine. |
| `npm ci` : pas de `package-lock.json` | Vérifier le répertoire courant (`pwd`) et les fichiers du mobile. |
| Erreur `configureCMakeDebug` | Vérifier **JDK 21 dans Gradle**, NDK `27.1.12297006`, CMake, SDK et les logs Gradle détaillés. |
| Téléphone absent d'ADB / `unauthorized` | Câble données, débogage USB, écran déverrouillé, demande RSA. |
| `Install cancelled by user` | Accepter la demande d'installation USB sur Android ; vérifier les paramètres Xiaomi/Redmi. |
| Expo propose un nouveau port | Un serveur occupe déjà **19006** : identifier puis arrêter **le bon processus**. |
| Expo annonce `172.x.x.x` | Arrêter l'ancien Metro et relancer avec **`--localhost --port 19006`**. |
| `Failed to connect` à `127.0.0.1:19006` | Tester `/status` depuis **PC puis téléphone**, refaire ADB reverse, sélectionner le nouveau serveur. |
| Redirection ADB disparue | Reconnecter le téléphone puis relancer `prepare-android-usb.sh`. |
| Page Keycloak inaccessible | Vérifier HTTP 200 sur **8090** et tunnel ADB 8090. |
| Login OK, profil KO | Vérifier Gateway **8080**, `.env.local`, routes **`/api/v1/users/**`**, logs et rôle BUYER. |
| Site vendeur inaccessible | Vérifier `npm run dev`, port **5173**, configuration `.env.local` du vendeur et Keycloak 8090. |
| Warning DevTools `chrome-sandbox` | Si Metro charge le bundle, poursuivre ; ne pas changer arbitrairement les permissions SUID. |

Pour un diagnostic natif approfondi :

```bash
cd "$HOME/IG5/iwa/O-Kit/apps/mobile-client/android"
./gradlew app:assembleDebug --stacktrace --info
```

## 11. Arrêter les applications sans effacer les données

- **Expo/Metro :** `Ctrl+C` dans son terminal.
- **Backoffice/Vite :** `Ctrl+C` dans son terminal.
- **Docker**, depuis la racine :

```bash
cd "$HOME/IG5/iwa/O-Kit"
docker compose down
```

Cette commande arrête et retire les conteneurs/réseau Compose en conservant les volumes nommés. Pour simplement arrêter les conteneurs en les conservant, utiliser `docker compose stop`.

**NE PAS lancer `docker compose down --volumes`** : cela pourrait supprimer les données locales PostgreSQL/Keycloak.

## 12. Repères et limites

Si un changement Keycloak n'apparaît pas, relancer le script de réconciliation
au lieu de supprimer les volumes. Si PostgreSQL refuse le mot de passe,
restaurer dans `.env` la valeur utilisée lors de la création du volume.

Les scripts d'installation et de démarrage fonctionnent pour **Ubuntu avec un
téléphone Android physique connecté en USB**. Un émulateur, Expo Go ou un accès
Wi-Fi sans câble nécessitent une autre procédure. Les contrôles de démarrage
(`curl`, `docker compose ps`, `adb reverse --list`) restent indispensables au
diagnostic local.

## 13. Documents de référence

- [`backend/ms-utilisateur/README.md`](backend/ms-utilisateur/README.md)
- [`docs/api/user-service.openapi.yaml`](docs/api/user-service.openapi.yaml)
- [`docs/api/user-service-integration.md`](docs/api/user-service-integration.md)
- [`docs/architecture/user-service-data-model.md`](docs/architecture/user-service-data-model.md)
- [`docs/auth/keycloak.md`](docs/auth/keycloak.md)
- [`docs/mobile/auth-profile-flow.md`](docs/mobile/auth-profile-flow.md)
- [`docs/functional/user-addresses.md`](docs/functional/user-addresses.md)
- [`docs/decisions/0003-user-service-boundary.md`](docs/decisions/0003-user-service-boundary.md)
- [`docs/decisions/0006-buyer-access-to-user-service.md`](docs/decisions/0006-buyer-access-to-user-service.md)
- [`docs/decisions/0007-user-service-harmonization.md`](docs/decisions/0007-user-service-harmonization.md)
- [`docs/decisions/0008-user-addresses.md`](docs/decisions/0008-user-addresses.md)
