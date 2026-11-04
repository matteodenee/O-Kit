# Gestion du Catalogue dans le backoffice vendeur

## Périmètre livré

Le backoffice vendeur réutilise les endpoints existants du microservice
Catalogue à travers l'API Gateway. Il permet à un compte vendeur autorisé de :

- consulter tous les produits, actifs ou désactivés ;
- rechercher localement dans les produits chargés ;
- filtrer par statut et disponibilité ;
- consulter le détail d'un produit ;
- modifier les champs scalaires acceptés par l'API ;
- activer ou désactiver un produit ;
- modifier sa disponibilité globale.

Le backoffice ne propose ni suppression définitive ni fausse gestion de
référentiels.

## Sécurité

Les lectures Catalogue restent disponibles à tout JWT O'kit authentifié. Les
requêtes `POST` et `PATCH` sont routées uniquement lorsque le token contient le
claim signé `okit_direct_realm_roles` avec `SELLER` et sans `BUYER`. Un compte
acheteur reçoit donc `403 Forbidden` avant que la Gateway ne contacte le
microservice Catalogue.

Le token reste géré en mémoire par `keycloak-js`. Le backoffice le renouvelle
avant chaque appel et le transmet dans l'en-tête `Authorization`.

## Limites des API existantes

Le contrôleur Catalogue expose bien `POST /api/v1/products`, mais sa requête
exige les identifiants d'une saison, d'une marque et d'un taux de TVA. Aucun
endpoint REST ne permet actuellement de lister ces référentiels. Le détail
d'un produit expose le taux de TVA mais pas son identifiant.

La création n'est donc pas affichée dans le backoffice : demander des
identifiants techniques à l'utilisateur ou coder une valeur en dur rendrait
l'interface fragile. La modification des relations marque, saison, équipe et
TVA reste également indisponible, car `PATCH /api/v1/products/{id}` n'accepte
pas ces champs.

## Démarrage local

Depuis la racine du dépôt :

```bash
docker compose up -d --build postgres catalogue-service api-gateway keycloak
docker compose exec postgres /opt/okit/seed-catalogue-demo.sh
```

Puis :

```bash
cd apps/seller-backoffice
cp .env.example .env.local
npm ci
npm run dev
```

Ouvrir `http://localhost:5173` et se connecter avec un compte auquel `SELLER`
est attribué directement, sans attribution directe de `BUYER`.

## Évolutions à coordonner avec le propriétaire du Catalogue

Pour rendre la création et la modification des relations utilisables, le
Catalogue doit fournir au minimum des endpoints de lecture pour les marques,
les saisons, les équipes et les taux de TVA. Leur accès en écriture doit faire
l'objet d'une décision métier distincte.
