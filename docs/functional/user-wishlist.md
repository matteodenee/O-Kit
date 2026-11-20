# Wishlist de l'acheteur

## Objectif

Un acheteur authentifié peut conserver une liste de produits favoris et la
retrouver lors de ses prochaines sessions. La wishlist appartient au profil
métier du service Utilisateur. Le Catalogue reste la source de vérité des
produits.

## Parcours backend

1. l'acheteur crée son profil métier ;
2. il ajoute un identifiant produit avec un `PUT` idempotent ;
3. il consulte la liste de ses identifiants favoris et leurs dates d'ajout ;
4. il retire un favori avec un `DELETE` idempotent ;
5. lorsqu'un vendeur désactive un produit, Catalogue publie un événement ;
6. le service Utilisateur retire ce produit de toutes les wishlists.

## Parcours frontend acheteur

1. le Catalogue charge en parallèle les produits actifs et les identifiants
   déjà présents dans la wishlist ;
2. chaque carte produit indique si le produit est favori ;
3. l'acheteur ajoute ou retire un favori sans quitter le Catalogue ;
4. un accès « Mes favoris » ouvre une page dédiée ;
5. cette page enrichit chaque identifiant avec les données courantes du
   Catalogue, sans les persister dans le service Utilisateur ;
6. un produit momentanément introuvable reste retirable et est présenté comme
   indisponible jusqu'à la synchronisation asynchrone ;
7. si le profil métier n'existe pas encore, l'ajout guide l'acheteur vers la
   page de profil.

L'interface gère explicitement les états chargement, erreur, liste vide et
mutation en cours. Elle utilise la Gateway et le token Keycloak courant pour
les deux microservices.

## Règles métier

- seul un JWT portant exclusivement le rôle `BUYER` accède à la wishlist ;
- l'identité est toujours déduite du claim `sub`, jamais d'un identifiant
  utilisateur fourni par le client ;
- un profil métier existant est requis pour ajouter un produit ;
- un même produit ne peut apparaître qu'une fois dans la wishlist d'un profil ;
- ajouter deux fois le même produit réussit sans créer de doublon ;
- retirer un produit absent réussit sans erreur ;
- la lecture est triée de l'ajout le plus récent au plus ancien ;
- les données descriptives et commerciales du produit ne sont pas copiées ;
- la désactivation asynchrone est idempotente.

## Critères d'acceptation

- un acheteur peut ajouter, lister et retirer ses favoris ;
- un visiteur anonyme reçoit `401` ;
- un compte vendeur reçoit `403` ;
- un acheteur sans profil reçoit `409` lors de l'ajout ;
- deux acheteurs ne voient jamais les favoris l'un de l'autre ;
- un événement `DESACTIVE` retire toutes les occurrences du produit ;
- un événement `ACTIF` ne modifie aucune wishlist ;
- un message invalide ou durablement non traitable rejoint la DLQ.
- le Catalogue affiche l'état favori et permet de le modifier immédiatement ;
- la page « Mes favoris » affiche les données Catalogue actuelles ;
- un échec réseau ne modifie pas visuellement la wishlist comme si l'opération
  avait réussi ;
- une mutation déjà en cours pour un produit ne peut pas être déclenchée une
  seconde fois.
