# ADR 0002 — Backoffice vendeur en React

## Statut

Acceptée.

## Contexte

Les documents fonctionnels historiques se contredisent sur la technologie du
backoffice vendeur : certains indiquent React, d'autres Angular. Le porteur du
projet confirme que l'interface vendeur doit rester dans l'écosystème React.

Le backoffice est destiné aux employés O'kit. Il ne constitue ni une application
acheteur, ni un microservice métier supplémentaire.

## Décision

Le backoffice vendeur est une SPA distincte développée avec React, TypeScript et
Vite. Son authentification utilise un client public Keycloak dédié avec
Authorization Code et PKCE S256.

Le backoffice fournit uniquement l'accès authentifié vendeur dans le périmètre
du service utilisateur. Les fonctionnalités catalogue, commande ou paiement
appartiendront à leurs domaines respectifs et ne sont pas implémentées ici.

## Alternatives envisagées

### Angular

Écartée à la suite de la décision explicite de conserver React.

### Ajouter les écrans vendeurs à l'application acheteur

Écartée : les deux interfaces ont des utilisateurs, des clients OIDC, des URI de
redirection et des cycles de livraison distincts.

## Conséquences

- `apps/seller-backoffice` est indépendant de `apps/mobile-client` ;
- le client Keycloak `okit-seller-web` est réservé au backoffice ;
- aucun service catalogue n'est créé par cette décision ;
- toute future API appelée par le backoffice devra vérifier `SELLER` côté
  serveur.
