# Parcours d'authentification du backoffice vendeur

## Objectif

Le backoffice React permet à un employé O'kit autorisé de se connecter avec un
compte créé par un administrateur. Il fournit l'authentification, le contrôle
du rôle et l'accès aux fonctions Catalogue déjà exposées par le microservice
propriétaire. Il ne crée aucun microservice métier supplémentaire.

## Sources de vérité

Keycloak conserve l'identité, le mot de passe, les sessions et le rôle realm
`SELLER`. `user-service` ne conserve aucune donnée vendeur.

## Accès autorisés

| Interface | Rôle obligatoire | Rôle entraînant un refus |
| --- | --- | --- |
| Application acheteur | `BUYER` | `SELLER` |
| Backoffice React | `SELLER` attribué directement | `BUYER` attribué directement |

Un compte portant directement les deux rôles métier est refusé par les deux
interfaces et par l'API de profil acheteur. Le rôle technique
`default-roles-okit` reste attribué à tous les comptes. Comme il hérite de
`BUYER` pour permettre l'inscription publique des acheteurs, le backoffice ne
peut pas déduire le type de compte depuis les seuls rôles effectifs.

Le client vendeur reçoit donc aussi le claim signé
`okit_direct_realm_roles`, produit par l'extension Keycloak O'kit. Ce claim
contient uniquement les rôles realm attribués directement à l'utilisateur,
sans développer les composites. Le backoffice exige `SELLER` et refuse
`BUYER` dans ce claim. Les API continuent de lire les rôles effectifs depuis
`realm_access.roles` pour leurs autorisations propres.

## Gestion administrative des vendeurs

Il n'existe aucune inscription publique donnant le rôle `SELLER`. Depuis la
console d'administration Keycloak, un administrateur :

1. crée l'utilisateur dans le realm `okit` ;
2. définit un mot de passe temporaire ou l'action `UPDATE_PASSWORD` ;
3. conserve le mapping technique `default-roles-okit` ;
4. attribue directement le rôle realm `SELLER` ;
5. vérifie que `BUYER` n'est pas aussi attribué directement ;
6. désactive ou supprime le compte lors du départ de l'employé.

Le mot de passe vendeur n'est jamais ajouté au dépôt. L'inscription publique
du realm reste disponible pour les acheteurs, mais elle n'accorde jamais
`SELLER` et ne permet donc pas d'entrer dans le backoffice.

Le client `okit-seller-web` utilise le thème Keycloak `okit-seller` : le lien
d'inscription publique est masqué sur sa page de connexion, tandis que le lien
de réinitialisation du mot de passe reste disponible. Le client acheteur
continue d'afficher l'inscription publique.

En développement local, la même opération peut être exécutée de manière
reproductible par un administrateur :

```bash
set -a
source .env
set +a
SELLER_EMAIL='vendeur@example.test' \
SELLER_TEMPORARY_PASSWORD='mot-de-passe-temporaire' \
SELLER_FIRST_NAME='Camille' \
SELLER_LAST_NAME='Martin' \
./infra/keycloak/create-local-seller.sh
```

Le script refuse d'écraser un compte existant et ne journalise jamais le mot de
passe. Par défaut, Keycloak demande au vendeur de remplacer ce mot de passe à
la première connexion.

## Client web

Le client public `okit-seller-web` utilise :

- Authorization Code avec PKCE S256 ;
- `http://localhost:5173/*` en développement ;
- le domaine HTTPS O'kit sous `/backoffice/*` pour la cible déployée ;
- les scopes `openid profile email roles` ;
- `keycloak-js`, avec tokens en mémoire uniquement et renouvellement avant
  expiration.

## Critères d'acceptation

- un compte avec `SELLER` directement attribué ouvre le backoffice tout en
  conservant `default-roles-okit` ;
- un compte `BUYER` est refusé par le backoffice ;
- un vendeur est refusé par l'application et l'API de profil acheteur ;
- un compte portant directement les deux rôles est refusé partout ;
- une inscription publique ne peut pas obtenir `SELLER` ;
- la connexion vendeur ne présente aucun lien d'inscription publique ;
- un vendeur peut demander la réinitialisation de son mot de passe par e-mail ;
- aucune donnée vendeur n'est créée dans PostgreSQL.
- les écritures Catalogue sont refusées par la Gateway pour un compte sans
  `SELLER` directement attribué ou portant aussi directement `BUYER`.
