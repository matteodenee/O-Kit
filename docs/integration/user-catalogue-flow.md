# Parcours Utilisateur vers Catalogue

## Objectif

Après une authentification Keycloak réussie, l'application acheteur ouvre la
page Catalogue. Cette page charge les produits actifs via la gateway sans faire
communiquer directement le microservice Utilisateur et le microservice
Catalogue.

## Parcours

1. l'acheteur s'authentifie avec Authorization Code et PKCE ;
2. le client vérifie que le token porte exclusivement le rôle `BUYER` ;
3. la route d'accueil redirige la session authentifiée vers `/catalogue` ;
4. le client appelle `GET /api/v1/products?statut=ACTIF` sur la gateway ;
5. la gateway valide le JWT, conserve le chemin et transmet la requête au
   microservice Catalogue sur le port 8082 ;
6. le client affiche la liste, un état vide ou une erreur exploitable.

Le profil et les adresses restent disponibles depuis la page Catalogue. Le
profil expose un accès explicite au Catalogue et l'écran des adresses propose
des accès directs au profil et au Catalogue, sans dépendre de l'historique de
navigation du navigateur ou de l'application native. Le parcours Utilisateur
continue d'utiliser `/api/v1/users/**` sur le port 8081.

## Contrats et sécurité

- le contrat de lecture est défini dans
  [`docs/api/catalogue-service.openapi.yaml`](../api/catalogue-service.openapi.yaml) ;
- le client n'appelle jamais directement le port 8082 ;
- les écritures Catalogue ne sont pas exposées par cette route acheteur ;
- le bearer token n'est ni journalisé ni persisté par le Catalogue ;
- RabbitMQ n'intervient pas dans ce parcours, conformément à
  [l'ADR 0009](../decisions/0009-rabbitmq-interservice-messaging.md).

## Exécution locale

Le Compose démarre une instance PostgreSQL physique avec deux bases logiques :

- `okit_users`, possédée par le compte du microservice Utilisateur ;
- `okit_catalogue`, possédée par un compte dédié au microservice Catalogue.

Le script d'initialisation est exécuté automatiquement sur un volume PostgreSQL
neuf. Sur un volume existant, il doit être relancé explicitement sans supprimer
les données :

```bash
docker compose exec postgres /docker-entrypoint-initdb.d/10-ensure-catalogue-db.sh
```

Les mots de passe sont fournis uniquement par `.env` à partir des placeholders
de `.env.example`.

Un jeu de 18 produits actifs et un produit désactivé peut être ajouté
explicitement, sans suppression ni écrasement des données existantes :

```bash
docker compose up -d postgres catalogue-service
docker compose exec postgres /opt/okit/seed-catalogue-demo.sh
```

Ce seed est réservé au développement, n'est jamais lancé automatiquement et
est documenté dans
[`docs/devops/catalogue-demo-data.md`](../devops/catalogue-demo-data.md).

## Critères d'acceptation

- un visiteur non authentifié reste sur l'écran de connexion ;
- un acheteur authentifié arrive sur `/catalogue` ;
- la gateway refuse une lecture Catalogue sans JWT valide ;
- une lecture authentifiée est transmise sans réécriture du chemin ;
- la page gère les états chargement, erreur et catalogue vide ;
- le profil et les adresses offrent un retour explicite vers le Catalogue ;
- les services Utilisateur et Catalogue démarrent avec leurs bases logiques
  indépendantes ;
- le seed de développement est idempotent et préserve les produits existants ;
- aucune ressource RabbitMQ n'est requise pour ce parcours.
