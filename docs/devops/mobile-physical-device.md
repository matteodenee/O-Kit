# Test de l'application O'kit sur un téléphone Android physique

## Objectif

Cette procédure permet de tester le development build Android avec Keycloak et
la gateway Docker du poste de développement, sans publier ces services sur le
réseau local ni modifier les règles JWT `BUYER`/`SELLER`.

## Diagnostic réseau

Lors de la vérification du 2 novembre 2026, l'interface Wi-Fi active était
`wlp2s0` avec l'adresse `10.247.88.82/16` et une route par défaut via
`10.247.0.1`. Cette adresse est une adresse DHCP locale : elle peut changer et
sa présence ne garantit pas que le point d'accès autorise les communications
directes entre clients Wi-Fi.

Docker Compose publie volontairement Keycloak et la gateway sur la boucle
locale du PC :

| Service | Publication hôte | Accès Android par USB |
| --- | --- | --- |
| Keycloak | `127.0.0.1:8090` | `adb reverse tcp:8090 tcp:8090` |
| API Gateway | `127.0.0.1:8080` | `adb reverse tcp:8080 tcp:8080` |
| Metro | `localhost:19006` | `adb reverse tcp:19006 tcp:19006` |

PostgreSQL, Mailpit et `user-service` ne sont pas exposés au téléphone.

## Pourquoi conserver `localhost`

Keycloak annonce l'issuer suivant quand il est appelé avec l'hôte
`localhost:8090` :

```text
http://localhost:8090/realms/okit
```

La gateway et `user-service` valident exactement cet issuer, tout en récupérant
les clés de signature depuis le réseau Docker interne. Utiliser uniquement
`http://10.247.88.82:8090` dans le mobile produirait un issuer différent et des
JWT refusés. Publier Keycloak en HTTP sur le Wi-Fi exposerait également les
échanges d'authentification aux autres équipements du réseau.

Le tunnel ADB conserve donc un nom d'hôte unique et la publication Docker sur
`127.0.0.1`. Pour un futur test sans câble, la solution cohérente consiste à
mettre Keycloak et la gateway derrière un nom d'hôte commun en HTTPS, puis à
mettre à jour ensemble l'issuer des resource servers et les URL du mobile.

## Prérequis Android

1. activer les options développeur et le débogage USB ;
2. brancher le téléphone et accepter l'empreinte RSA du PC ;
3. vérifier `adb devices -l` ;
4. installer le development build O'kit, et non Expo Go.

Expo Go ne permet pas de tester de manière fiable le callback OIDC stable
`okit://auth`. Le build Android doit déclarer le package `com.okit.mobile` et le
schéma `okit`, comme dans `app.json`.

## Configuration

Le fichier ignoré `apps/mobile-client/.env.local` doit contenir :

```ini
EXPO_PUBLIC_KEYCLOAK_URL=http://localhost:8090
EXPO_PUBLIC_API_URL=http://localhost:8080
```

Ne pas y inscrire de mot de passe, token ou autre secret.

## Démarrage

Depuis la racine du dépôt :

```bash
set -a
source .env
set +a
docker compose up -d --build keycloak postgres user-service api-gateway
docker compose ps
```

Pour une première installation du development build :

```bash
cd apps/mobile-client
npm ci
npm run android
```

Pour les lancements suivants :

```bash
cd apps/mobile-client
npm run start:android:usb
```

La commande vérifie la découverte OIDC, la présence de PKCE S256, la gateway,
le téléphone autorisé et le package installé. Elle installe ensuite les trois
tunnels et lance Expo avec `--dev-client --localhost`.

## Vérifications

Sur le PC :

```bash
curl --fail http://localhost:8090/realms/okit/.well-known/openid-configuration
curl --fail http://localhost:8080/health
adb reverse --list
```

Les tunnels attendus sont `tcp:8090`, `tcp:8080` et `tcp:19006`. Sur le
téléphone, la connexion doit ouvrir Keycloak puis revenir dans O'kit avec
`okit://auth`. Un compte `BUYER` peut ensuite appeler `/api/v1/users/me`. Les
comptes `SELLER` et les doubles rôles restent refusés.

Le backoffice continue à utiliser `http://localhost:8090` depuis le PC. Aucun
changement n'est apporté à son client OIDC ni à ses redirections
`http://localhost:5173/*`.

Les tunnels disparaissent après une reconnexion USB ou un redémarrage d'ADB ;
relancer `npm run start:android:usb` les recrée.
