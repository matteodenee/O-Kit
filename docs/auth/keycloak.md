# Authentification locale avec Keycloak

Ce document décrit la configuration de l'authentification O'kit. Keycloak porte
les identités, les mots de passe et les rôles acheteur et vendeur. L'application
acheteur et le backoffice React utilisent chacun un client public OIDC avec
Authorization Code et PKCE S256.

Cette configuration est réservée au développement local. Le mode `start-dev`, HTTP et les URI localhost ne conviennent pas à la production.

## Configuration fournie

Le fichier `infra/keycloak/import/okit-realm.json` crée au premier démarrage :

- le realm `okit` ;
- le client public `okit-mobile` ;
- le client public `okit-seller-web` ;
- le flux Authorization Code ;
- l'obligation d'utiliser PKCE S256 ;
- le client scope Keycloak `basic`, qui ajoute le claim stable `sub` au token
  d'accès utilisé par les microservices ;
- l'inscription utilisateur ;
- la réinitialisation du mot de passe ;
- l'envoi local des courriels de réinitialisation vers Mailpit ;
- le rôle realm `BUYER` ;
- le rôle realm `SELLER` ;
- l'attribution de `BUYER` par défaut aux inscriptions publiques.

Le client ne possède pas de secret, car une application installée sur un appareil ne peut pas conserver un secret de manière fiable. Les flux implicite, Resource Owner Password Credentials et service account sont désactivés.

## Prérequis

- Docker avec la commande `docker compose` ;
- le port local `8080` disponible ;
- une copie locale du fichier d'environnement d'exemple.

Créer le fichier local `.env` puis remplacer le mot de passe factice :

```bash
cp .env.example .env
```

Le fichier `.env` est ignoré par Git. Il ne doit jamais être ajouté au dépôt.

## Démarrage

Depuis la racine du dépôt :

```bash
docker compose up -d keycloak
```

La console d'administration est ensuite accessible à l'adresse `http://localhost:8090/admin/`. Le document de découverte OIDC du realm est exposé à l'adresse suivante :

```text
http://localhost:8090/realms/okit/.well-known/openid-configuration
```

Vérifier les journaux si le service ne démarre pas :

```bash
docker compose logs keycloak
```

Arrêter le service sans supprimer les données :

```bash
docker compose down
```

La commande suivante supprime aussi le volume et toutes les données locales de Keycloak. Elle permet de rejouer l'import initial après une modification du realm :

```bash
docker compose down --volumes
```

Cette suppression est destructive pour les comptes de développement créés localement.

## Paramètres du client mobile

| Paramètre | Valeur de développement |
| --- | --- |
| Realm | `okit` |
| Client | `okit-mobile` |
| Type | Client public |
| Flux | Authorization Code |
| PKCE | S256 obligatoire |
| URI native | `okit://auth` |
| URI web | `http://localhost:19006` |
| Scopes attendus | `openid profile email` |

Le client mobile et `user-service` exigent exclusivement le rôle `BUYER`. Les
scope mappings exposent `BUYER` et `SELLER` afin qu'un compte mal provisionné
avec les deux rôles soit refusé explicitement.

Dans Kubernetes, le même client accepte également le domaine HTTPS du groupe :

```text
https://dams5-07.students.polytech.lange.xyz
```

L'étape d'implémentation mobile devra calculer l'URI de redirection avec Expo plutôt que la coder en dur. La valeur obtenue devra correspondre exactement à une URI autorisée dans Keycloak.

## Paramètres du client vendeur

| Paramètre | Valeur de développement |
| --- | --- |
| Realm | `okit` |
| Client | `okit-seller-web` |
| Type | Client public |
| Flux | Authorization Code |
| PKCE | S256 obligatoire |
| URI web | `http://localhost:5173/*` |
| Scopes attendus | `openid profile email` |

Le backoffice accepte exclusivement `SELLER`. Il ne propose aucune inscription
vendeur et conserve ses tokens en mémoire uniquement. L'inscription générique
du realm reste réservée aux acheteurs et ne fournit jamais `SELLER`.

Le client vendeur sélectionne le thème de connexion `okit-seller`. Ce thème
masque le lien d'inscription publique pour ce client uniquement. Le lien
« Mot de passe oublié » reste disponible aux vendeurs comme aux acheteurs.

## Accès depuis Android ou un appareil physique

L'adresse `localhost:8090` fonctionne uniquement depuis la machine qui exécute Keycloak. Depuis un émulateur Android, l'hôte de développement est généralement accessible avec `10.0.2.2`. Depuis un appareil physique, il faut utiliser une adresse réseau ou un domaine de développement accessible par l'appareil.

Le port du conteneur est actuellement lié à `127.0.0.1` afin de ne pas exposer involontairement Keycloak sur le réseau local. Toute ouverture réseau doit être volontaire et documentée avant de modifier `compose.yaml`.

## Persistance et import

Le volume Docker `keycloak_data` conserve les utilisateurs et la configuration locale. Au démarrage, l'option `--import-realm` importe les fichiers JSON présents dans `/opt/keycloak/data/import` seulement si le realm n'existe pas déjà.

Une modification de `okit-realm.json` ne remplace donc pas automatiquement un
realm déjà présent. Le script idempotent suivant applique les paramètres
fonctionnels du realm, des rôles `BUYER`/`SELLER` et des deux clients publics
sans supprimer les utilisateurs :

```bash
KEYCLOAK_ADMIN_USERNAME=admin \
KEYCLOAK_ADMIN_PASSWORD='mot-de-passe-local' \
./infra/keycloak/reconcile-local-realm.sh
```

Le mot de passe est transmis par l'environnement et n'est jamais écrit dans le
dépôt. Le compte fourni doit déjà pouvoir administrer le realm `master`.

Si l'accès administrateur d'un volume local a été perdu, utiliser la commande
officielle `bootstrap-admin` avec tous les nœuds Keycloak arrêtés afin de créer
un compte temporaire, exécuter la réconciliation, puis supprimer immédiatement
ce compte. Ne pas supprimer un volume contenant des utilisateurs pour récupérer
un accès administrateur.

## Inscription et mot de passe oublié

L'inscription et la réinitialisation sont activées dans le realm. En local,
Keycloak envoie les courriels à Mailpit sur le réseau Docker. La boîte de test
est accessible uniquement depuis la machine de développement :

```text
http://localhost:8025
```

Mailpit n'envoie aucun message sur Internet. Il capture les courriels afin de
permettre l'ouverture du lien de réinitialisation dans un navigateur. Pour
tester le parcours :

1. ouvrir la connexion Keycloak depuis l'application acheteur ou le backoffice ;
2. sélectionner « Mot de passe oublié » ;
3. saisir l'adresse du compte ;
4. ouvrir Mailpit et sélectionner le courriel reçu ;
5. ouvrir le lien et définir un nouveau mot de passe.

Le script `infra/keycloak/reconcile-local-realm.sh` configure Mailpit par
défaut. Il accepte aussi les variables suivantes pour un serveur SMTP externe :

```text
KEYCLOAK_SMTP_HOST
KEYCLOAK_SMTP_PORT
KEYCLOAK_SMTP_FROM
KEYCLOAK_SMTP_FROM_DISPLAY_NAME
KEYCLOAK_SMTP_AUTH
KEYCLOAK_SMTP_STARTTLS
KEYCLOAK_SMTP_SSL
KEYCLOAK_SMTP_USER
KEYCLOAK_SMTP_PASSWORD
```

Les identifiants d'un fournisseur SMTP réel doivent être injectés par
l'environnement ou un Secret Kubernetes. Ils ne doivent jamais être inscrits
dans le JSON du realm ou dans le dépôt.

Le client ouvre l'endpoint d'inscription utilisateur Keycloak avec le même flux
Authorization Code et PKCE que la connexion. Le mot de passe reste donc saisi
uniquement dans l'interface Keycloak.

## Création d'un compte vendeur

La création d'un vendeur est une opération administrative. Dans la console
Keycloak du realm `okit`, l'administrateur crée l'utilisateur, lui impose si
nécessaire un changement de mot de passe, retire `default-roles-okit` puis
attribue `SELLER`. Il vérifie enfin que les rôles effectifs ne contiennent pas
`BUYER`.

La procédure détaillée et les contrôles attendus sont décrits dans
[`docs/seller/authentication-flow.md`](../seller/authentication-flow.md).

## Limites connues

- Mailpit est réservé au développement local et n'achemine aucun courriel réel ;
- le fournisseur SMTP de l'environnement déployé doit encore être choisi et
  configuré par l'équipe en charge du déploiement ;
- la validation de l'adresse e-mail est désactivée en développement ;
- l'aperçu web local dépend de Keycloak sur `8090`, d'Expo sur `19006` et de la
  gateway sur `8080` ; `user-service` reste exposé sur `8081` uniquement pour
  le diagnostic local ;
- le backoffice vendeur local utilise `http://localhost:5173`.

## Déploiement Kubernetes

Le dossier `infra/keycloak` fournit le déploiement Keycloak, sa base PostgreSQL,
les probes, les métriques et l'Ingress des routes OIDC. Les identifiants
d'administration et le mot de passe de base sont fournis par un Secret qui ne
doit jamais être ajouté au dépôt.

La procédure complète se trouve dans
[`docs/devops/keycloak-kubernetes.md`](../devops/keycloak-kubernetes.md).

## Références

- [Exécuter Keycloak dans un conteneur](https://www.keycloak.org/server/containers)
- [Importer et exporter des realms](https://www.keycloak.org/server/importExport)
- [Guide d'administration Keycloak](https://www.keycloak.org/docs/latest/server_admin/)
