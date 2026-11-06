# Gestion des adresses de livraison

## Objectif

Un acheteur authentifié doit pouvoir gérer les adresses de livraison associées
à son compte depuis l'application Expo, sur mobile comme dans l'aperçu web.

## User story

En tant qu'acheteur O'kit, je veux enregistrer plusieurs adresses, choisir celle
utilisée par défaut et masquer celles que je n'utilise plus afin de préparer
plus rapidement une future commande.

## Périmètre

- un profil métier doit exister avant la création d'une adresse ;
- le nombre d'adresses n'est pas limité fonctionnellement ;
- les adresses peuvent être internationales ;
- un libellé facultatif permet de les reconnaître, par exemple « Maison » ;
- la première adresse active devient automatiquement l'adresse par défaut ;
- une seule adresse active peut être définie par défaut ;
- une adresse masquée n'apparaît plus et ne peut plus être consultée, modifiée,
  sélectionnée ou restaurée par les API clientes ;
- si l'adresse par défaut n'est pas la dernière adresse active, l'acheteur doit
  d'abord en choisir une autre avant de pouvoir la masquer ;
- la dernière adresse active peut être masquée sans remplacement.

## Données d'une adresse

- libellé facultatif ;
- prénom et nom du destinataire ;
- numéro de téléphone ;
- première ligne d'adresse ;
- complément d'adresse facultatif ;
- ville ;
- région ou État facultatif ;
- code postal facultatif pour supporter les pays qui n'en utilisent pas ;
- code pays ISO 3166-1 alpha-2 obligatoire.

## Critères d'acceptation

1. Un compte sans rôle `BUYER` ne peut accéder à aucune adresse.
2. Un acheteur ne peut voir et modifier que ses propres adresses, identifiées à
   partir du claim JWT `sub`.
3. La création est refusée tant que le profil métier n'existe pas.
4. La première adresse créée est retournée comme adresse par défaut.
5. L'ajout d'une autre adresse ne change pas l'adresse par défaut.
6. Choisir une nouvelle adresse par défaut retire ce statut à l'ancienne dans
   la même transaction.
7. Masquer l'adresse par défaut est refusé tant qu'une autre adresse active
   existe et n'a pas été choisie par défaut.
8. Une adresse masquée ne figure plus dans la liste et son identifiant répond
   comme une adresse inexistante.
9. Une adresse appartenant à un autre acheteur répond `404 Not Found` afin de
   ne pas révéler son existence.
10. L'écran Expo gère les états de chargement, liste vide, erreur, création,
    modification, sélection par défaut et masquage.

## Utilisation future par les commandes

Le service commande ne doit ni lire la base de `user-service`, ni conserver une
clé étrangère vers une adresse sauvegardée. Au passage de commande, il recevra
et conservera un instantané immuable des coordonnées de livraison. Une
modification ou un masquage ultérieur de l'adresse sauvegardée ne changera donc
jamais l'historique de commande.

## Hors périmètre

- calcul des frais et choix du mode de livraison ;
- option de livraison rapide ;
- validation postale externe, géocodage ou normalisation par pays ;
- restauration d'une adresse masquée ;
- adresse d'une commande ou intégration au service commande ;
- wishlist, documentée séparément dans
  [`user-wishlist.md`](user-wishlist.md).
