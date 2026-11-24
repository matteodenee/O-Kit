# Préparation du parcours utilisateur sur Kubernetes

## Périmètre

Cet overlay déploie uniquement le parcours utilisateur :

- Keycloak et sa base PostgreSQL dédiée ;
- `user-service`, connecté à la base logique `okit_users` de PostgreSQL mutualisé ;
- `api-gateway` ;
- les routes Ingress `/api`, `/realms` et `/resources` ;
- les `ServiceMonitor` de ces trois composants.

Il ne contient ni catalogue ni backoffice. Les ressources historiques `nginx`,
`okit-backend`, `postgres-db`, `pgdata-pvc` et le Secret TLS `nginx-tls` ne sont
ni supprimés. `postgres-db` doit héberger `okit_users` avant le déploiement.

## Images

L'overlay `infra/kubernetes/overlays/user-staging` utilise les chemins complets :

- `registry.gitlab.com/iwa-group2/o-kit/keycloak:<sha-main>` ;
- `registry.gitlab.com/iwa-group2/o-kit/user-service:<sha-main>` ;
- `registry.gitlab.com/iwa-group2/o-kit/api-gateway:<sha-main>`.

Le tag est injecté au rendu par le pipeline ou par
`infra/kubernetes/scripts/deploy-user-stack.sh`. Il correspond au SHA court de
`main`, reste immuable et remplace tout tag flottant ; le tag `latest` est
interdit. Le placeholder versionné `replace-me` empêche une application
accidentelle sans sélection explicite de version.

Les Deployments utilisent le Secret `gitlab-registry` via `imagePullSecrets`.
Ce Secret est créé hors Git et ses valeurs ne doivent jamais apparaître dans
les logs ou les manifests versionnés.

## Bases et persistance

Keycloak conserve sa base et son PVC dédiés. Le service Utilisateur réutilise
l'instance physique `postgres-db` avec une base logique indépendante
`okit_users` et des credentials propres. Les autres bases de cette instance ne
doivent pas être lues, modifiées ou supprimées.

Les Secrets requis sont :

- `keycloak-secrets` avec `database-password`, `bootstrap-admin-username`,
  `bootstrap-admin-password` et `ms-commande-client-secret` ;
- `user-service-secrets` avec `database-password` ;
- `gitlab-registry` avec les credentials de lecture du registre.

Les valeurs sont générées et appliquées directement au cluster, sans fichier
intermédiaire persistant et sans affichage dans les logs.

## Préparation locale des images

Depuis la racine du dépôt, en remplaçant `<sha-main>` par le SHA court exact :

```bash
docker build --pull -t registry.gitlab.com/iwa-group2/o-kit/keycloak:<sha-main> infra/keycloak
docker build --pull -t registry.gitlab.com/iwa-group2/o-kit/user-service:<sha-main> backend/ms-utilisateur
docker build --pull -t registry.gitlab.com/iwa-group2/o-kit/api-gateway:<sha-main> services/api-gateway

docker push registry.gitlab.com/iwa-group2/o-kit/keycloak:<sha-main>
docker push registry.gitlab.com/iwa-group2/o-kit/user-service:<sha-main>
docker push registry.gitlab.com/iwa-group2/o-kit/api-gateway:<sha-main>
```

La publication des images ne modifie pas Kubernetes.

## Prévisualisation sans déploiement

Rendre l'overlay versionné sans le modifier :

```bash
kubectl kustomize infra/kubernetes/overlays/user-staging > /tmp/okit-user-staging.yaml
kubectl apply --dry-run=server -f /tmp/okit-user-staging.yaml
kubectl diff -f /tmp/okit-user-staging.yaml
```

`kubectl diff` peut retourner le code `1` lorsqu'il existe des différences
attendues. Le script de déploiement refuse les erreurs de diff, vérifie les
Secrets requis, provisionne `okit_users`, réconcilie le realm existant et
attend chaque rollout.

Le provisionnement PostgreSQL est idempotent : il crée la base et le rôle s'ils
n'existent pas, puis synchronise uniquement le mot de passe du rôle depuis le
Secret `user-service-secrets`. Il ne supprime aucune base, table ou donnée.

La réconciliation Keycloak utilise le script embarqué dans l'image. Elle met à
jour le realm existant sans supprimer les comptes, crée ou synchronise les
rôles `BUYER`, `SELLER` et `SVC_COMMANDE`, ainsi que les clients
`okit-mobile`, `okit-seller-web` et `ms-commande`. Le secret confidentiel du
client machine provient exclusivement de `keycloak-secrets`.

## Ordre de déploiement prévu

Après validation du diff :

1. créer directement dans Kubernetes les Secrets manquants ;
2. appliquer PostgreSQL Keycloak et attendre sa disponibilité ;
3. appliquer Keycloak et vérifier son rollout, son issuer OIDC et ses métriques ;
4. créer ou synchroniser de manière idempotente `okit_users` et son compte sur
   PostgreSQL mutualisé ;
5. appliquer `user-service`, vérifier Flyway, les probes et les métriques ;
6. appliquer `api-gateway`, puis l'Ingress ;
7. vérifier `/realms`, `/resources`, le `401` sans JWT, le succès avec un JWT
   `BUYER`, le refus sans ce rôle et les cibles Prometheus.

Un retour arrière restaure uniquement les workloads applicatifs ou retire les
routes ajoutées. Aucun PVC ni aucune base ne doit être supprimé.

Les actions à reprendre lors de l'intégration finale sont regroupées dans la
[checklist d'intégration du service Utilisateur](user-service-integration-checklist.md).
