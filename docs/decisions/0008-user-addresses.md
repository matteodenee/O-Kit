# ADR 0008 — Adresses de livraison du compte acheteur

## Statut

Acceptée.

## Contexte

L'application acheteur doit permettre d'enregistrer plusieurs adresses de
livraison, d'en choisir une par défaut et de masquer celles qui ne sont plus
utilisées. `user-service` possède déjà le profil métier de l'acheteur, sa base
PostgreSQL dédiée et l'isolation des données par le claim Keycloak `sub`.

Créer un service autonome uniquement pour ce carnet d'adresses ajouterait un
déploiement, une base et des appels réseau sans former aujourd'hui un domaine
métier suffisamment indépendant.

## Décision

- `user-service` est propriétaire des adresses sauvegardées de l'acheteur ;
- une table `user_addresses` distincte référence `user_profiles` ;
- un profil métier existant est obligatoire avant toute création ;
- toutes les routes restent sous `/api/v1/users/me/**` et exigent exclusivement le
  rôle realm `BUYER` ;
- la propriété d'une adresse est toujours vérifiée avec le claim JWT `sub` ;
- plusieurs adresses actives sont autorisées sans limite fonctionnelle ;
- la première adresse active devient automatiquement l'adresse par défaut ;
- au plus une adresse active est définie par défaut par profil ;
- une adresse supprimée par le client est archivée avec `archived_at` et devient
  inaccessible par les API normales ;
- masquer l'adresse par défaut exige d'abord un nouveau choix lorsqu'une autre
  adresse active existe ;
- la livraison rapide et les frais de livraison n'appartiennent pas à l'adresse
  sauvegardée ;
- aucune validation postale externe ni géocodage n'est ajouté dans cette
  tranche.

## Partage avec le domaine commande

Une commande conservera son propre instantané immuable de l'adresse de
livraison. Elle ne référencera pas directement `user_addresses` et aucun autre
service ne lira la base de `user-service`. Le contrat du futur service commande
définira les données d'expédition qu'il accepte.

## Alternatives envisagées

### Créer un microservice d'adresses

Écartée pour cette tranche. Cette séparation deviendrait pertinente si la
validation postale, le géocodage, les transporteurs ou le partage d'adresses
formaient plus tard un domaine autonome.

### Stocker les adresses dans Keycloak

Écartée. Une adresse de livraison est une donnée métier, pas une donnée
d'identité nécessaire à l'authentification.

### Référencer une adresse vivante depuis une commande

Écartée. Une ancienne commande ne doit jamais changer lorsqu'un acheteur
modifie ou masque une adresse sauvegardée.

### Supprimer physiquement une adresse

Écartée pour l'action cliente. L'archivage permet de conserver une trace métier
interne sans rendre l'adresse de nouveau accessible au client.

## Conséquences

- `user-service` reçoit une migration Flyway et un agrégat d'adresse distinct ;
- l'API et le client Expo prennent en charge le CRUD, le choix par défaut et
  l'archivage ;
- les données d'adresse sont des informations personnelles et ne doivent pas
  être écrites intégralement dans les journaux ;
- le futur service commande reste découplé du stockage utilisateur ;
- une extraction ou suppression réglementaire des données devra prendre en
  compte les adresses archivées.
