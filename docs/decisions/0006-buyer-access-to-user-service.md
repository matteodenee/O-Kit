# ADR 0006 — Séparation des accès acheteur et vendeur

## Statut

Acceptée.

## Contexte

`user-service` conserve le profil métier utilisé par l'application acheteur.
L'isolation par le claim `sub` empêche un utilisateur de choisir l'identifiant
d'un autre profil, mais elle ne distingue pas à elle seule les différents
types de comptes Keycloak.

O'kit utilise le rôle realm `BUYER` pour identifier les comptes autorisés à
utiliser l'application acheteur. Les employés autorisés à utiliser le
backoffice portent le rôle realm `SELLER`. Aucune donnée vendeur n'est stockée
dans `user-service`.

## Décision

- Keycloak expose deux clients publics distincts, `okit-mobile` et
  `okit-seller-web`, utilisant Authorization Code avec PKCE S256 ;
- l'inscription publique attribue uniquement `BUYER` ;
- les comptes vendeurs sont créés et gérés par un administrateur Keycloak, qui
  retire le rôle hérité `BUYER` puis attribue `SELLER` ;
- toutes les routes `/api/v1/users/**` exigent un JWT valide portant `BUYER` et ne
  portant pas `SELLER` ;
- le rôle est lu depuis `realm_access.roles` et converti en autorité Spring
  `ROLE_BUYER` ;
- un token absent ou invalide produit `401 Unauthorized` ;
- un token valide sans `BUYER`, avec `SELLER`, ou avec les deux rôles produit
  `403 Forbidden` ;
- les routes de santé et de métriques restent accessibles sans JWT, car le
  Service Kubernetes n'est pas exposé directement par l'Ingress ;
- toute autre route est refusée par défaut ;
- l'application acheteur accepte exclusivement `BUYER` ;
- le backoffice React accepte exclusivement `SELLER` ;
- les contrôles des interfaces ne remplacent jamais l'autorisation côté API ;
- aucune table ni endpoint de profil vendeur n'est créé : le nom et l'adresse
  e-mail affichés proviennent des claims Keycloak.

## Alternatives envisagées

### Accepter tout compte authentifié

Écartée : un compte technique ou destiné à une autre interface ne doit pas
pouvoir créer un profil acheteur uniquement parce qu'il possède un token.

### Créer un second microservice utilisateur pour les vendeurs

Écartée : aucun profil métier vendeur n'est requis. Keycloak reste propriétaire
de leur identité et les futurs services métier appliqueront leurs propres
autorisations.

### Autoriser l'inscription publique des vendeurs

Écartée : un compte vendeur est un compte employé privilégié. Seul un
administrateur peut créer, désactiver et attribuer ce rôle.

### Utiliser le même client OIDC pour les deux interfaces

Écartée : des clients séparés isolent les origines, les URI de redirection et
les cycles de vie des applications.

### Faire confiance uniquement au contrôle du client mobile

Écartée : un client peut être contourné. Le service propriétaire des données
doit toujours prendre la décision finale.

## Conséquences

- le realm Keycloak crée `BUYER`, `SELLER`, `okit-mobile` et
  `okit-seller-web` ;
- les tokens des deux clients exposent les rôles nécessaires au contrôle de
  séparation ;
- les tests couvrent les réponses `401`, `403`, le succès avec `BUYER` et le
  refus d'un compte vendeur ou mal provisionné avec les deux rôles ;
- l'ajout d'un nouveau type de compte ne donne aucun accès implicite au profil
  acheteur ;
- aucune table, migration ou API vendeur n'est créée dans `user-service`.
