# Parcours mobile/web d'authentification et de profil

## Objectif

Le client React Native avec aperçu Expo Web permet à un acheteur de :

1. se connecter à un compte existant ;
2. créer un compte dans Keycloak ;
3. revenir dans l'application avec Authorization Code et PKCE S256 ;
4. consulter son profil métier ;
5. compléter ou modifier son prénom, son nom, son téléphone et sa date de
   naissance ;
6. enregistrer ces informations dans `user-service` ;
7. gérer ses adresses de livraison actives et son adresse par défaut.

Le rôle realm Keycloak `BUYER` est obligatoire. Un compte ne portant pas ce
rôle est refusé par le client.

## Sources de vérité

Keycloak reste propriétaire :

- de l'identifiant de connexion ;
- de l'adresse e-mail ;
- du mot de passe ;
- des sessions et tokens.

`user-service` reste propriétaire :

- du lien avec le claim `sub` Keycloak ;
- du prénom et du nom utilisés par le domaine métier ;
- du téléphone ;
- de la date de naissance facultative ;
- des adresses de livraison sauvegardées et de leur état actif ou masqué.

L'e-mail affiché par l'API provient du JWT courant et n'est pas dupliqué dans la
table `user_profiles`.

## Comportement attendu

### Utilisateur non authentifié

- l'écran affiche « Se connecter » et « Créer un compte » ;
- les deux actions ouvrent l'interface Keycloak, jamais un formulaire de mot de
  passe propre à l'application ;
- le client OIDC utilisé est `okit-mobile` sans secret ;
- l'inscription publique reçoit le rôle `BUYER` par défaut ;
- sur l'application native, Keycloak revient vers `okit://auth`. Expo Router
  associe ce deep link à la route `/auth`, qui redirige immédiatement vers
  l'accueil pendant que `expo-auth-session` valide la réponse et échange le
  code d'autorisation ;
- une erreur ou une annulation ramène à un état exploitable.

### Utilisateur authentifié

- le client refuse un token ne portant pas exclusivement `BUYER`, notamment un
  compte vendeur ou un compte mal provisionné avec `BUYER` et `SELLER` ;
- avant chaque appel protégé, le client réutilise l'access token tant qu'il est
  encore valable avec une marge de sécurité ;
- à l'approche de l'expiration, le client renouvelle automatiquement l'access
  token avec le refresh token, sans secret client ;
- le client appelle `GET /api/v1/users/me` avec le bearer token ;
- une réponse `200` préremplit le formulaire depuis la base métier ;
- une réponse `404` affiche un formulaire vide, prérempli avec le prénom et le
  nom disponibles dans Keycloak ;
- l'enregistrement appelle `PUT /api/v1/users/me` puis affiche les données
  retournées par le service ;
- une réponse `401` déclenche un seul renouvellement suivi d'une seule nouvelle
  tentative ; un second `401` ou un refresh refusé invalide la session locale
  et demande une reconnexion ;
- la déconnexion supprime d'abord les tokens locaux, puis demande à Keycloak de
  terminer la session distante ;
- sur Android et iOS, les tokens sont conservés dans SecureStore ; pour
  l'aperçu web, ils restent limités à `sessionStorage` et disparaissent à la
  fermeture de l'onglet.

### Gestion des adresses

- l'accès à l'écran d'adresses est proposé après l'enregistrement du profil ;
- l'écran liste uniquement les adresses actives, avec celle par défaut en
  premier ;
- une liste vide propose directement la création de la première adresse ;
- le même formulaire sert à créer ou modifier une adresse internationale ;
- le code pays utilise deux lettres ISO majuscules ;
- la première adresse créée devient automatiquement celle par défaut ;
- une action explicite permet de choisir une autre adresse par défaut ;
- masquer une adresse demande confirmation ;
- si l'adresse est celle par défaut et que d'autres adresses existent, le
  client demande d'abord d'en sélectionner une nouvelle ;
- une adresse masquée disparaît définitivement des interfaces clientes ;
- les états de chargement, erreur et liste vide restent exploitables sur
  mobile et dans l'aperçu web Expo.

## Environnements

Les URL sont configurées avec des variables Expo publiques qui ne contiennent
aucun secret :

```text
EXPO_PUBLIC_KEYCLOAK_URL
EXPO_PUBLIC_API_URL
```

Valeurs locales par défaut :

```text
http://localhost:8090
http://localhost:8080
```

Sur Android physique, ces adresses restent volontairement identiques. Le mode
de développement supporté utilise le build `com.okit.mobile` et `adb reverse`
pour transporter les ports `8090`, `8080` et `19006` sur le câble USB. Ainsi,
le navigateur Android, l'application et les resource servers utilisent tous
l'issuer `http://localhost:8090/realms/okit`.

Expo Go n'est pas utilisé pour l'authentification OIDC, car il ne permet pas de
garantir le schéma de callback `okit://auth`. Un accès direct par l'adresse
Wi-Fi du poste demanderait de modifier ensemble l'issuer Keycloak, les resource
servers et les URL du client ; il n'est pas activé par défaut en HTTP.

Pour l'aperçu web local, Spring Cloud Gateway autorise uniquement l'origine
`http://localhost:19006` et transmet les requêtes à `user-service`. En
Kubernetes, le client, la gateway et les APIs sont destinés à partager l'hôte
HTTPS du groupe.

## Réinitialisation du mot de passe

La réinitialisation est proposée et exécutée par Keycloak. En développement,
les courriels sont capturés par Mailpit et consultables sur
`http://localhost:8025`. En environnement déployé, un serveur SMTP réel doit
être fourni par la configuration sécurisée de Keycloak.

Aucun mot de passe ou token ne doit apparaître dans les journaux.
