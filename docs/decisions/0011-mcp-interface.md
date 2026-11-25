# ADR 0011 — Interface MCP O'Kit

## Statut

Acceptée pour l'implémentation initiale en lecture seule.

## Contexte

Le projet doit fournir une véritable interface Model Context Protocol (MCP)
utilisable par un client externe. Le chatbot acheteur existe déjà dans
`backend/ms-chatbot` et possède des outils Spring AI internes qui interrogent
Catalogue, Stock et Commande. Ces outils ne constituent pas une interface MCP :
ils ne sont ni découvrables par `tools/list`, ni appelables par `tools/call`.

Les microservices existants restent propriétaires de leur domaine et de leurs
données. Commande filtre déjà les lectures acheteur avec le `sub` du JWT ; Stock
et Commande contrôlent les rôles Keycloak. Aucun service KPI n'est présent dans
la branche de référence du chatbot.

## Décision

Créer `backend/mcp-service`, un microservice Spring Boot dédié utilisant Spring
AI MCP et le transport Streamable HTTP sur `/mcp`.

Le service :

- expose uniquement des outils de lecture dans cette première version ;
- valide le JWT Keycloak de chaque requête MCP ;
- accepte les rôles `BUYER` et `SELLER` pour les lectures Catalogue/Stock ;
- réserve les commandes au rôle `BUYER` et relaie son JWT vers Commande ;
- appelle les API HTTP existantes et n'accède à aucune base de données ;
- n'expose ni secret, ni token, ni donnée de profil ;
- est joignable localement sur le port 8089 et via la Gateway sur `/mcp`.

Les outils initiaux sont :

- `search_products` ;
- `get_product_details` ;
- `get_product_stock` ;
- `find_similar_products` ;
- `list_my_orders` ;
- `get_order_status`.

`get_shop_kpis` n'est pas exposé tant qu'un service KPI et son contrat
d'autorisation ne sont pas disponibles.

Le chatbot existant conserve son endpoint, son écran, son modèle et ses deux
modes. Il consomme les outils à travers MCP au lieu d'appeler directement les
microservices métier.

## Alternatives envisagées

### Ajouter MCP directement dans `ms-chatbot`

Écarté : cela confond l'interface protocolaire de la plateforme avec le domaine
conversationnel et rend le serveur MCP dépendant du modèle IA.

### Dupliquer les outils dans `mcp-service`

Écarté : deux implémentations pourraient diverger. `mcp-service` devient le
propriétaire unique des adaptateurs métier exposés comme outils ; le chatbot les
consomme via MCP.

### Accéder directement aux bases Catalogue, Stock ou Commande

Écarté : cela violerait la propriété des données et contournerait les contrôles
d'accès des microservices.

### Conserver Spring AI 1.0.3 et le transport SSE

Écarté pour le nouveau service : la ligne 1.0.x ne fournit pas le transport
Streamable HTTP. Le service MCP utilise une ligne Spring AI compatible avec
Spring Boot 3.x qui fournit ce transport, sans imposer une migration globale aux
autres microservices.

## Conséquences

- Un service supplémentaire doit être construit, déployé et supervisé.
- Un client MCP externe doit fournir un JWT Keycloak valide.
- Une indisponibilité MCP empêche les fonctions métier du chatbot, mais ne
  modifie pas les API Catalogue, Stock ou Commande.
- Les contrôles d'accès restent appliqués deux fois : à l'entrée de
  `mcp-service`, puis par le microservice métier destinataire.
- Toute future écriture MCP nécessitera une décision et des tests dédiés ; elle
  n'est pas incluse dans cette première version.
