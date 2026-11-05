# ADR 0001 Authentification mobile avec Keycloak et PKCE

## Statut

Acceptée pour l'environnement de développement du login mobile.

## Contexte

O'kit doit proposer la connexion, l'inscription et la réinitialisation du mot de passe dans l'application React Native. Le contexte projet prévoit Keycloak pour centraliser les identités, les mots de passe et les clients OIDC. La configuration locale précédente persistait les données, mais ne permettait pas de recréer automatiquement le realm et son client.

Une application mobile est un client public : elle ne peut pas protéger durablement un secret client. Le flux d'authentification doit aussi fonctionner avec un navigateur système et revenir dans l'application sans exposer les identifiants à celle-ci.

## Décision

- Keycloak est le fournisseur d'identité du login mobile.
- L'application utilise OpenID Connect Authorization Code avec PKCE S256.
- Le client Keycloak `okit-mobile` est public et ne contient aucun secret.
- Les flux implicite, mot de passe direct et service account sont désactivés.
- Keycloak gère les mots de passe, l'inscription et la réinitialisation.
- Le realm de développement est importé depuis un fichier JSON versionné.
- Les données locales sont conservées dans un volume Docker.
- Les URI de redirection sont restreintes aux valeurs nécessaires au web local et au schéma natif O'kit.
- Le rôle acheteur et la protection de `user-service` sont définis dans
  l'[ADR 0006](0006-buyer-access-to-user-service.md).

## Alternatives envisagées

### Collecter directement le mot de passe dans l'application

Cette solution a été écartée. Elle augmenterait l'exposition des identifiants et ferait porter à l'application une responsabilité déjà couverte par Keycloak.

### Utiliser un client confidentiel avec un secret embarqué

Cette solution a été écartée, car un secret inclus dans une application mobile peut être extrait du paquet applicatif.

### Utiliser le flux implicite

Cette solution a été écartée au profit du flux Authorization Code avec PKCE, adapté aux clients publics et limitant l'exploitation d'un code d'autorisation intercepté.

### Utiliser le flux de mot de passe direct

Cette solution a été écartée. Elle obligerait l'application à manipuler directement le mot de passe et contournerait le parcours d'authentification hébergé par Keycloak.

## Conséquences

- L'application ouvrira la page Keycloak dans le navigateur système.
- L'application calcule les URI de redirection par plateforme et gère les
  tokens sans secret client.
- Le client renouvelle l'access token avec le refresh token avant expiration
  et retente une seule fois un appel API ayant reçu `401`. Un échec du
  renouvellement supprime la session locale et impose une nouvelle connexion.
- L'inscription et la réinitialisation bénéficient des parcours Keycloak. Un
  serveur Mailpit local capture les courriels de développement ; un fournisseur
  SMTP réel et ses secrets restent une responsabilité de l'environnement
  déployé.
- L'import JSON crée un realm neuf. Un script d'administration idempotent
  réconcilie un realm local déjà initialisé sans supprimer ses utilisateurs.
- La configuration de production devra utiliser HTTPS, un stockage adapté et des secrets fournis par l'environnement de déploiement.

## Références

- [Sécurisation des applications avec Keycloak](https://www.keycloak.org/securing-apps/overview)
- [Guide d'administration Keycloak](https://www.keycloak.org/docs/latest/server_admin/)
