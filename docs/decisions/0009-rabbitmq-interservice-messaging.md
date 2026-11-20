# ADR 0009 — RabbitMQ pour les échanges interservices asynchrones

## Statut

Acceptée pour les futurs échanges asynchrones entre microservices.

## Contexte

L'équipe a retenu RabbitMQ pour découpler les microservices lorsqu'un domaine
doit publier un événement métier destiné à un ou plusieurs autres domaines.
La première intégration entre Utilisateur et Catalogue consiste toutefois à
afficher le catalogue après l'authentification d'un acheteur. Ce parcours est
une navigation du client suivie d'une lecture HTTP et ne constitue pas un
échange métier entre les deux microservices.

## Décision

- RabbitMQ sera le broker des futurs événements métier asynchrones O'kit.
- Aucun broker, exchange, queue ou message n'est ajouté pour le parcours
  immédiat d'authentification vers le catalogue.
- Le client mobile appelle la gateway en HTTP pour consulter
  `GET /api/v1/products` avec le bearer token de l'acheteur.
- La gateway valide le JWT et transmet la lecture au microservice Catalogue.
- Les microservices Utilisateur et Catalogue gardent des bases logiques
  indépendantes et ne lisent jamais directement les tables l'un de l'autre.
- Avant le premier événement RabbitMQ, son propriétaire, son schéma versionné,
  sa clé d'idempotence, sa politique de reprise et sa file d'erreur devront être
  documentés.

## Alternatives envisagées

### Publier un message lors de chaque connexion

Écartée pour ce parcours : un message ne permet pas au client d'obtenir la
liste des produits et ajouterait une dépendance sans besoin métier.

### Faire appeler Catalogue directement par le client

Écartée : la gateway reste le point d'entrée public et centralise la validation
du token ainsi que le routage.

### Utiliser des appels directs entre Utilisateur et Catalogue

Écartée pour cette tranche : le Catalogue n'a besoin d'aucune donnée du profil
Utilisateur pour retourner la liste des produits.

## Conséquences

- l'intégration actuelle reste synchrone et simple ;
- RabbitMQ pourra être ajouté lorsqu'un événement métier concret le justifiera ;
- aucun couplage réseau ou de données n'est créé entre les deux microservices ;
- l'indisponibilité du Catalogue est affichée comme une erreur de chargement au
  client sans affecter l'authentification ni le profil Utilisateur.

## Première application événementielle

L'[ADR 0010](0010-user-wishlist.md) introduit le premier événement métier :
Catalogue publie `product.status.changed.v1` et `user-service` le consomme pour
retirer des wishlists les produits désactivés. Son schéma versionné se trouve
dans `docs/events/product-status-changed.v1.schema.json`.

Le CRUD de wishlist reste synchrone en REST. La file du service Utilisateur est
durable, utilise des tentatives bornées et redirige les échecs définitifs vers
une DLQ durable.
