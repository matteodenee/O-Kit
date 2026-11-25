# Contrat MCP O'Kit

## Transport et endpoint

- protocole : Model Context Protocol ;
- transport : Streamable HTTP ;
- endpoint local direct : `http://localhost:8089/mcp` ;
- endpoint via Gateway : `http://localhost:8080/mcp` ;
- authentification : `Authorization: Bearer <JWT Keycloak>` ;
- rôles acceptés : `BUYER` ou `SELLER`, selon l'outil.

Les clients doivent suivre l'initialisation MCP, conserver l'en-tête de session
retourné par le serveur et envoyer la notification `notifications/initialized`
avant d'appeler `tools/list` ou `tools/call`.

## Outils

### `search_products`

Recherche les produits actifs par mots-clés et prix TTC maximal facultatif.
Retourne au maximum huit résultats synthétiques. Rôles : `BUYER`, `SELLER`.

Entrée :

```json
{
  "query": "maillot PSG",
  "maxPriceTtc": 100
}
```

### `get_product_details`

Retourne la fiche d'un produit actif et la disponibilité de ses tailles.
Rôles : `BUYER`, `SELLER`.

Entrée :

```json
{"productId": 12}
```

### `get_product_stock`

Retourne la disponibilité des variantes demandées. Rôles : `BUYER`, `SELLER`.

Entrée :

```json
{"variantIds": [120, 121]}
```

### `find_similar_products`

Recommande des produits actifs de la même équipe, puis de la même marque, en
privilégiant les produits disponibles. Cet outil maintient la fonctionnalité de
recommandation déjà proposée par le chatbot. Rôles : `BUYER`, `SELLER`.

Entrée :

```json
{"productId": 12}
```

### `list_my_orders`

Liste les dernières commandes hors panier de l'acheteur authentifié. Rôle :
`BUYER` uniquement.

Entrée : objet vide.

### `get_order_status`

Retourne le statut et le résumé d'une commande appartenant à l'acheteur
authentifié. Le JWT reçu est relayé à Commande, qui recherche la commande par
identifiant **et** par `sub`. Une commande appartenant à un autre acheteur reste
donc introuvable. Rôle : `BUYER` uniquement.

Entrée :

```json
{"orderId": 42}
```

## Erreurs et sécurité

- JWT absent ou invalide : HTTP `401` ;
- rôle non autorisé : appel d'outil en erreur, sans donnée métier ;
- ressource absente ou non possédée : résultat MCP en erreur ;
- microservice indisponible : résultat MCP en erreur, sans détail sensible.

Les outils ne retournent jamais le JWT, les credentials Keycloak, les données
de paiement ou les données privées du profil.

## Démonstration pour la soutenance

### Avec le script fourni

Prérequis : la stack Docker démarrée, `curl`, `jq` et un JWT Keycloak `BUYER`
valide. Le token reste dans une variable locale et n'est jamais affiché.

```bash
export MCP_TOKEN='JWT_ACHETEUR_ICI'
./backend/mcp-service/scripts/demo-mcp.sh
```

Le script réalise, dans l'ordre :

1. `initialize` et récupération de la session Streamable HTTP ;
2. `notifications/initialized` ;
3. `tools/list` ;
4. `tools/call` sur `search_products` avec les données Catalogue réelles ;
5. fermeture de la session.

Pour montrer une commande réelle appartenant à l'acheteur :

```bash
ORDER_ID=42 ./backend/mcp-service/scripts/demo-mcp.sh
```

Pour démontrer l'isolation, rejouer avec le JWT d'un autre acheteur et le même
`ORDER_ID` : l'outil doit répondre que la commande est introuvable.

### Avec MCP Inspector

```bash
npx @modelcontextprotocol/inspector
```

Dans Inspector :

- transport : **Streamable HTTP** ;
- URL : `http://localhost:8080/mcp` ;
- header : `Authorization: Bearer <JWT BUYER>` ;
- connecter, ouvrir **Tools**, actualiser la liste puis appeler
  `search_products` et `get_order_status`.

Ne jamais enregistrer le JWT dans le dépôt ou dans une capture destinée à être
publiée.
