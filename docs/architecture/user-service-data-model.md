# Modèle de données du service utilisateur

## Propriété des données

`user-service` est seul propriétaire de la table `user_profiles`. Aucun autre service ne doit lire ou modifier cette table directement.

Keycloak reste propriétaire de l'identité. Le champ `identity_subject` référence logiquement le claim `sub` du JWT, sans clé étrangère vers la base Keycloak.

## Table user_profiles

| Colonne | Type | Contraintes | Rôle |
| --- | --- | --- | --- |
| `id` | UUID | clé primaire | Identifiant métier technique |
| `identity_subject` | VARCHAR(255) | unique, non nul | Claim Keycloak `sub` |
| `first_name` | VARCHAR(100) | non nul | Prénom du profil |
| `last_name` | VARCHAR(100) | non nul | Nom du profil |
| `phone_number` | VARCHAR(32) | non nul | Téléphone |
| `birth_date` | DATE | facultatif, date passée ou présente | Date de naissance |
| `created_at` | TIMESTAMP WITH TIME ZONE | non nul | Création du profil |
| `updated_at` | TIMESTAMP WITH TIME ZONE | non nul | Dernière modification |
| `version` | BIGINT | non nul | Verrouillage optimiste des mises à jour concurrentes |

## Table user_addresses

Une adresse appartient à un profil métier. Toutes les recherches clientes
joignent le profil propriétaire et le claim JWT `sub` ; un identifiant
d'adresse seul ne suffit jamais à autoriser l'accès.

| Colonne | Type | Contraintes | Rôle |
| --- | --- | --- | --- |
| `id` | UUID | clé primaire | Identifiant technique de l'adresse |
| `user_profile_id` | UUID | clé étrangère non nulle vers `user_profiles` | Profil propriétaire |
| `label` | VARCHAR(80) | facultatif | Libellé comme « Maison » |
| `recipient_first_name` | VARCHAR(100) | non nul | Prénom du destinataire |
| `recipient_last_name` | VARCHAR(100) | non nul | Nom du destinataire |
| `phone_number` | VARCHAR(32) | non nul | Téléphone de livraison |
| `address_line_1` | VARCHAR(200) | non nul | Adresse principale |
| `address_line_2` | VARCHAR(200) | facultatif | Complément d'adresse |
| `city` | VARCHAR(100) | non nul | Ville ou localité |
| `region` | VARCHAR(100) | facultatif | Région, province ou État |
| `postal_code` | VARCHAR(32) | facultatif | Code postal selon le pays |
| `country_code` | VARCHAR(2) | non nul, deux lettres majuscules | Code ISO 3166-1 alpha-2 |
| `is_default` | BOOLEAN | non nul | Adresse active choisie par défaut |
| `created_at` | TIMESTAMP WITH TIME ZONE | non nul | Création |
| `updated_at` | TIMESTAMP WITH TIME ZONE | non nul | Dernière modification |
| `archived_at` | TIMESTAMP WITH TIME ZONE | facultatif | Date de masquage logique |
| `version` | BIGINT | non nul | Verrouillage optimiste |

Un index unique PostgreSQL partiel sur `user_profile_id` lorsque
`is_default = TRUE` et `archived_at IS NULL` garantit qu'un profil ne possède
jamais deux adresses actives par défaut. Le service garantit en plus que la
première adresse active est celle par défaut.

## Données volontairement absentes

- mot de passe et credentials ;
- access token et refresh token ;
- rôles et permissions ;
- e-mail du compte comme copie persistée ;
- données d'un autre domaine métier.

## Évolutions envisagées

La table `user_profiles` couvre le modèle complet du MVP actuel. Elle
n'anticipe ni les adresses de livraison ni la wishlist avec des colonnes
inutilisées.

La tranche adresses appartient à `user-service` conformément à l'ADR 0008. Le
futur service commande recevra un instantané d'expédition et ne lira jamais
cette base.

La wishlist ne sera modélisée qu'après stabilisation des identifiants et du
contrat de consultation du catalogue.
