# Déploiement Kubernetes du service utilisateur

## Périmètre

Cette première tranche Kubernetes déploie :

- `user-service` comme Deployment sans état ;
- son Service interne ;
- les probes de démarrage, disponibilité et vivacité ;
- un `ServiceMonitor` pour collecter `/metrics`.

La base logique `okit_users` est hébergée par l'instance PostgreSQL mutualisée
du projet. Aucun StatefulSet ni PVC PostgreSQL n'est créé par ces manifests.

Le namespace cible est `polytech-dams5-07`.

## Exposition réseau

`user-service` écoute sur 8081 et reste un Service interne. Traefik publie `/api/**` vers Spring
Cloud Gateway, qui route ensuite `/api/v1/users/**` vers le service sur l'hôte du
cluster :

```text
https://dams5-07.students.polytech.lange.xyz
```

Cette séparation évite de multiplier les Ingress. Spring Cloud Gateway valide
le JWT et centralise le CORS, puis `user-service` valide à nouveau le token
avant d'accéder aux données métier.

## Secrets

Le mot de passe PostgreSQL n'est pas versionné. Un Secret nommé
`user-service-secrets`, contenant la clé `database-password`, doit exister
avant le déploiement. L'hôte, le port, la base et l'utilisateur sont injectés
par `USER_DB_HOST`, `USER_DB_PORT`, `USER_DB_NAME` et `USER_DB_USERNAME`.

L'image du service est publiée sous
`registry.gitlab.com/iwa-group2/o-kit/user-service:<sha-git>`. L'overlay conserve
un tag volontairement invalide jusqu'à la sélection du commit applicatif
canonique. Le Deployment utilise le Secret `gitlab-registry`.

## Ressources et stockage

Les ressources sont dimensionnées pour rester raisonnables dans le quota du namespace :

| Composant | CPU demandé | Mémoire demandée | CPU maximum | Mémoire maximum |
| --- | ---: | ---: | ---: | ---: |
| `user-service` | 75m | 192Mi | 200m | 384Mi |

La JVM limite son heap à 65 % de la mémoire visible afin de préserver une marge
pour Metaspace, les threads et les buffers natifs. Le budget doit être confirmé
par un démarrage sous limite de 384 Mi et réévalué à partir de mesures si les
probes ou la charge réelle révèlent une pression mémoire.

La migration d'une ancienne base dédiée vers `okit_users` doit être sauvegardée
et validée séparément. Aucun manifest de cette tranche ne supprime un
StatefulSet, un volume ou une base existante.

## Dépendances

Les manifests Keycloak sont fournis dans `infra/keycloak`. Ils créent le Service
interne `keycloak`, importent le realm `okit` et publient l'issuer HTTPS attendu.

La gateway applicative et l'Ingress sont fournis dans
`infra/kubernetes/gateway`. Traefik conserve l'hôte et le certificat du cluster
et route `/api/**` vers `api-gateway`, qui conserve le chemin
`/api/v1/users/**` lors de l'appel au service.
