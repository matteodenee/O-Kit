# mcp-service

Interface Model Context Protocol d'O'Kit. Ce microservice expose en lecture
seule les fonctionnalités Catalogue, Stock et Commande à un client MCP externe.
Il ne possède aucune base et ne contient aucune logique métier des services
appelés.

## Architecture

```text
Client MCP ── JWT ──▶ Gateway ──▶ mcp-service ── JWT ──▶ Catalogue / Stock / Commande
                                      ▲
Chatbot existant ── JWT ──────────────┘
```

- transport : Streamable HTTP ;
- endpoint : `POST|GET|DELETE /mcp` ;
- port local : `8089` ;
- authentification : JWT Keycloak ;
- rôles : `BUYER` et `SELLER`, avec les outils Commande réservés à `BUYER`.

## Outils

| Outil | Source | Rôles |
| --- | --- | --- |
| `search_products` | Catalogue | BUYER, SELLER |
| `get_product_details` | Catalogue + Stock | BUYER, SELLER |
| `get_product_stock` | Stock | BUYER, SELLER |
| `find_similar_products` | Catalogue | BUYER, SELLER |
| `list_my_orders` | Commande | BUYER |
| `get_order_status` | Commande | BUYER |

Le JWT de l'appelant est relayé. Commande filtre le détail par le `sub` du JWT,
ce qui empêche la lecture de la commande d'un autre acheteur.

## Démarrage et tests

```bash
docker compose up --build -d mcp-service api-gateway
curl --fail http://localhost:8089/actuator/health
```

```bash
cd backend/mcp-service
mvn test
```

Le contrat détaillé et les appels de démonstration sont documentés dans
[`../../docs/api/mcp-service.md`](../../docs/api/mcp-service.md).

La démonstration protocolaire automatisée utilise un vrai échange
`initialize` / `tools/list` / `tools/call` :

```bash
MCP_TOKEN='JWT_BUYER' ./scripts/demo-mcp.sh
```
