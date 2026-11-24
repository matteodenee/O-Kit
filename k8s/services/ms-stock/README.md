# ms-stock sur Kubernetes

Gestion du stock : consultation et mise à jour vendeur (`SELLER`), et
endpoints `/internal` appelés par ms-commande (rôle `SVC_COMMANDE`). Exposé via
l'API Gateway (`STOCK_SERVICE_URL=http://ms-stock:8083`) ; ms-commande l'appelle
directement en interne.

| Ressource | Nom | Détail |
| :--- | :--- | :--- |
| Deployment | `ms-stock` | 1 réplica, non-root (uid 10001), FS en lecture seule |
| Service | `ms-stock` | ClusterIP, port `8083` nommé `http` |
| ConfigMap | `ms-stock-config` | URL DB, issuer OIDC, port, Swagger désactivé |
| Secret (hors Git) | `ms-stock-secrets` | `database-password` |
| ServiceMonitor | `ms-stock` | `/actuator/prometheus` toutes les 30 s |

Ressources : 100m / 256Mi demandés, 500m / 512Mi max.

## Dépendances

- **PostgreSQL mutualisé `postgres-db`** : base `okit_stock_db`, rôle
  `okit_stock`. Flyway crée le schéma au démarrage (`ddl-auto: validate`).
- **Keycloak** : vérification des JWT (`SELLER`, et `SVC_COMMANDE` pour le
  service account de ms-commande). ms-stock n'a pas de client Keycloak propre :
  il valide seulement les tokens.

## Prérequis à faire une seule fois

Toutes les commandes s'exécutent dans le namespace `polytech-dams5-07`.

1. **Base de données** : créer le rôle et la base, avec un mot de passe généré
   sans l'afficher :

   ```bash
   export NS=polytech-dams5-07
   DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"
   kubectl -n $NS exec -i deploy/postgres-db -- psql -U postgres -v ON_ERROR_STOP=1 \
     -v pwd="$DB_PASSWORD" <<'SQL'
   CREATE ROLE okit_stock LOGIN PASSWORD :'pwd';
   CREATE DATABASE okit_stock_db OWNER okit_stock;
   SQL
   ```

   Si le rôle et la base existent déjà (ancien déploiement), remplacer le
   `CREATE` par `ALTER ROLE okit_stock PASSWORD :'pwd';`, ou récupérer le mot
   de passe actuel :
   `kubectl -n $NS get secret stock-db-credentials -o jsonpath='{.data.password}' | base64 -d`.
   Adapter `deploy/postgres-db` et l'utilisateur admin à l'instance réelle.

2. **Secret Kubernetes** :

   ```bash
   kubectl -n $NS create secret generic ms-stock-secrets \
     --from-literal=database-password="$DB_PASSWORD"
   unset DB_PASSWORD
   ```

3. **Secret de pull** : `gitlab-registry` doit exister dans le namespace
   (déjà utilisé par ms-commande).

## Construire et publier l'image

Tag immuable = SHA court du commit (le tag `latest` est interdit) :

```bash
TAG="$(git rev-parse --short HEAD)"
docker build --pull -t registry.gitlab.com/iwa-group2/o-kit/ms-stock:$TAG backend/ms-stock
docker push registry.gitlab.com/iwa-group2/o-kit/ms-stock:$TAG
```

La CI le fait automatiquement sur `main` ; le tag n'est jamais commité
(`newTag: replace-me` dans Git).

## Déployer

```bash
TAG="$(git rev-parse --short HEAD)"
kubectl kustomize k8s/services/ms-stock | sed "s/replace-me/$TAG/" > /tmp/ms-stock.yaml
kubectl apply --dry-run=server -f /tmp/ms-stock.yaml
kubectl diff -f /tmp/ms-stock.yaml
kubectl apply -f /tmp/ms-stock.yaml
kubectl -n $NS rollout status deploy/ms-stock --timeout=5m
```

Puis redéployer la gateway pour qu'elle prenne `STOCK_SERVICE_URL` :

```bash
kubectl apply -k infra/kubernetes/gateway
kubectl -n $NS rollout restart deploy/api-gateway
```

Nettoyage de l'ancien Secret une fois le nouveau déploiement validé :
`kubectl -n $NS delete secret stock-db-credentials`.

## Vérifications

```bash
kubectl -n $NS logs deploy/ms-stock | grep -i "flyway\|started"
# sans JWT : 401 attendu
curl -i https://dams5-07.students.polytech.lange.xyz/api/v1/stock
```

Avec un JWT `SELLER`, les routes vendeur répondent `200`. La cible `ms-stock`
doit être `UP` dans Prometheus. Une validation de commande dans ms-commande ne
doit plus renvoyer 503.

## Retour arrière

`kubectl -n $NS rollout undo deploy/ms-stock`, ou
`kubectl delete -k k8s/services/ms-stock` pour retirer le service. Ne jamais
supprimer la base `okit_stock_db`.