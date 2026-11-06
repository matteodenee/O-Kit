# ADR 0010 — Wishlist acheteur et événements Catalogue

## Statut

Acceptée.

## Contexte

Un acheteur authentifié doit pouvoir conserver des produits favoris. La
wishlist est une préférence propre à l'utilisateur, tandis que le Catalogue
reste propriétaire des produits et de leur statut. Les opérations d'ajout, de
suppression et de consultation exigent une réponse immédiate. Un changement de
statut Catalogue doit être propagé sans coupler la transaction du vendeur à la
base Utilisateur.

## Décision

- `user-service` possède la wishlist et stocke uniquement l'identifiant `Long`
  du produit Catalogue ;
- une entrée appartient au profil métier identifié à partir du `sub` du JWT ;
- le profil doit exister avant l'ajout d'un favori ;
- le CRUD est exposé en REST sous `/api/v1/users/me/wishlist` et reste
  synchrone ;
- l'ajout et la suppression sont idempotents ;
- la réponse de lecture contient les identifiants et dates d'ajout, sans copie
  des données Catalogue ;
- Catalogue publie `product.status.changed.v1` après validation de sa
  transaction de changement de statut ;
- `user-service` consomme cet événement et retire le produit de toutes les
  wishlists lorsque le nouveau statut vaut `DESACTIVE` ;
- l'exchange, la file principale et la file d'erreur sont durables ; le
  consommateur réessaie les erreurs transitoires avant dead-lettering ;
- le traitement est idempotent : supprimer à nouveau un produit déjà absent ne
  produit pas d'erreur.

## Alternatives envisagées

### Stocker la wishlist dans Catalogue

Écartée : Catalogue ne doit pas devenir propriétaire des préférences privées
des acheteurs.

### Appeler Catalogue à chaque ajout

Écartée pour cette tranche : l'identifiant provient d'un produit déjà présenté
par l'API Catalogue. Une validation synchrone ajouterait une dépendance réseau
sans être nécessaire à la persistance de la préférence.

### Utiliser RabbitMQ pour le CRUD

Écartée : le client doit recevoir immédiatement le résultat d'un ajout ou
d'une suppression. RabbitMQ est réservé à la propagation asynchrone du
changement de statut.

### Copier les détails produit dans la base Utilisateur

Écartée : les titres, prix et disponibilités appartiennent au Catalogue et
deviendraient rapidement obsolètes.

## Conséquences

- aucune clé étrangère cross-service n'est créée ;
- un identifiant inexistant peut être enregistré si un client contourne le
  parcours normal ; il sera inutilisable lors de l'enrichissement Catalogue ;
- la future interface devra charger les détails depuis Catalogue ;
- l'événement est versionné et documenté ;
- la livraison RabbitMQ intervient après commit. Les reprises de publication
  couvrent les indisponibilités transitoires ; une stratégie outbox pourra être
  ajoutée si une garantie de livraison stricte devient nécessaire.
