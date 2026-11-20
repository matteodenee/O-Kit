# Synchronisation Catalogue vers wishlist avec RabbitMQ

## Objectif

Les opérations de wishlist restent synchrones en REST. RabbitMQ transporte
uniquement les changements de statut produit afin que `user-service` retire un
produit devenu indisponible sans appeler Catalogue pendant une requête client.

Le contrat fonctionnel est décrit dans
[`user-wishlist.md`](../functional/user-wishlist.md), la décision dans
[l'ADR 0010](../decisions/0010-user-wishlist.md) et le schéma JSON versionné
dans [`product-status-changed.v1.schema.json`](../events/product-status-changed.v1.schema.json).

## Topologie

| Élément | Valeur |
| --- | --- |
| Exchange | `okit.catalogue.events` (`topic`, durable) |
| Routing key | `product.status.changed.v1` |
| File Utilisateur | `user-service.product-status.changed.v1` |
| Dead-letter exchange | `okit.catalogue.events.dlx` (`direct`, durable) |
| Dead-letter queue | `user-service.product-status.changed.v1.dlq` |

Catalogue publie l'événement après la validation de sa transaction de mise à
jour. Le consommateur Utilisateur tente trois traitements avec un délai
croissant. Après épuisement des tentatives, RabbitMQ déplace le message vers la
dead-letter queue. La suppression par `productId` est idempotente : rejouer un
événement déjà traité ne change pas le résultat.

## Exécution locale

Depuis la racine du dépôt :

```bash
docker compose up -d --build rabbitmq postgres catalogue-service user-service api-gateway
docker compose ps
```

L'interface RabbitMQ est accessible sur
[`http://localhost:15672`](http://localhost:15672). Les identifiants de
développement viennent de `RABBITMQ_USERNAME` et `RABBITMQ_PASSWORD`. Compose
utilise des valeurs locales de repli ; un environnement partagé ou de
production doit fournir ses propres secrets hors du dépôt.

Pour observer le flux sans afficher de secret :

```bash
docker compose logs -f catalogue-service user-service rabbitmq
```

La publication et la consommation sont activées dans Compose par
`CATALOGUE_MESSAGING_ENABLED=true` et `WISHLIST_MESSAGING_ENABLED=true`. Elles
restent désactivées par défaut lorsqu'un service est lancé isolément, afin que
ses tests et ses usages sans broker restent autonomes.

## Exploitation

- surveiller la profondeur de la file principale et de la dead-letter queue ;
- analyser puis corriger la cause avant de rejouer un message en erreur ;
- ne jamais supprimer automatiquement la dead-letter queue en production ;
- ne pas utiliser cet événement pour les ajouts, suppressions ou lectures REST
  de wishlist.
