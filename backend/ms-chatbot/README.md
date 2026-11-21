# ms-chatbot

Assistant conversationnel de l'acheteur O'Kit : ventes (ses commandes), retours et annulations,
informations produit, recommandations.

Le LLM est fourni par la **plateforme IA Polytech**, via son adresse compatible OpenAI
(`https://ollama.polytech.lange.xyz/openai/v1`), avec Spring AI.

## Fonctionnement

```
App mobile ──JWT──▶ api-gateway ──POST /api/v1/chat──▶ ms-chatbot ──clé pia_…──▶ plateforme IA
                                                           │
                                                           └──JWT de l'acheteur──▶ catalogue / stock / commande
```

Le LLM n'accède à aucune base. Il appelle des **outils** (`tools/OkitTools.java`) qui interrogent
les autres services avec le JWT de l'acheteur. Il ne voit donc que les commandes de cet acheteur.

| Outil | Service appelé |
| --- | --- |
| `rechercherProduits(recherche, prixMaxTtc)` | catalogue |
| `detailProduit(idProduit)` | catalogue + stock |
| `produitsSimilaires(idProduit)` | catalogue |
| `mesCommandes()` / `detailCommande(id)` | commande |

Les retours et annulations sont décrits dans le prompt système
(`src/main/resources/prompts/systeme.txt`). **La politique de retour y est provisoire et doit
être validée par l'équipe.**

## API

`POST /api/v1/chat`, rôle `BUYER`. Le service est sans état : le client renvoie les derniers messages.

```json
{
  "message": "Le maillot 12 existe en M ?",
  "historique": [
    {"role": "UTILISATEUR", "contenu": "Bonjour"},
    {"role": "ASSISTANT", "contenu": "Bonjour ! Comment puis-je vous aider ?"}
  ]
}
```

Réponse : `{"reponse": "..."}`. Erreurs : `400` (message vide ou trop long), `401`/`403`,
`429` (limite du jour atteinte), `502`/`503` (plateforme IA ou service O'Kit indisponible).

## Modes

| `CHATBOT_MODE` | Effet |
| --- | --- |
| `mock` (défaut) | Réponses simulées par mots-clés, **aucune requête LLM consommée**, vrais appels aux services O'Kit. Pour développer l'écran de chat. |
| `polytech` | Vrai LLM. Chaque question coûte 1 à 3 requêtes sur le quota de la clé (10/jour par défaut). |

En mode `polytech`, chaque acheteur est limité à `CHATBOT_QUESTIONS_PAR_JOUR` questions par jour
(3 par défaut, compteur en mémoire).

## Configuration

Dans `.env` à la racine (jamais versionné), ou exporté dans le shell :

```bash
CHATBOT_MODE=polytech
POLYTECH_API_KEY=pia_...
LLM_MODEL=souverain-llm-qwen   # le prof demande de privilégier Qwen
```

```bash
docker compose up --build -d chatbot-service api-gateway
```

## Tests

Les tests n'appellent jamais le LLM.

```bash
mvn verify
```
