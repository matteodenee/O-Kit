# user-service sur Kubernetes

Ces manifests ciblent exclusivement le namespace `polytech-dams5-07`. Ils ne
créent ni Ingress, ni Service public, ni instance PostgreSQL : le trafic externe
passe par la [gateway d'entrée](../../../infra/kubernetes/gateway/README.md) et
la base logique `okit_users` utilise l'instance PostgreSQL mutualisée.

## Prérequis

- une image `user-service` publiée dans le registre retenu par l'équipe ;
- un kubeconfig conservé hors du dépôt ;
- le Secret `user-service-secrets` ;
- le Service PostgreSQL mutualisé `postgres-db` et la base logique
  `okit_users`, provisionnée sans supprimer les bases existantes ;
- les CRD Prometheus Operator pour le `ServiceMonitor`.

## Configuration obligatoire

Remplacer dans `kustomization.yaml` :

```yaml
images:
  - name: user-service
    newName: registre-retenu/okit/user-service
    newTag: tag-immuable
```

Le registre et le tag ne sont pas choisis ici, car leur gestion appartient à la chaîne CI de l'équipe.

Les variables non sensibles se trouvent dans `user-service-configmap.yaml` :
`USER_DB_HOST`, `USER_DB_PORT`, `USER_DB_NAME` et `USER_DB_USERNAME`. Adapter
ces valeurs au Service PostgreSQL réellement fourni par le cluster.

## Création du Secret

Saisir le mot de passe sans l'écrire dans le dépôt :

```bash
read -rs OKIT_USER_DB_PASSWORD
kubectl -n polytech-dams5-07 create secret generic user-service-secrets \
  --from-literal=database-password="${OKIT_USER_DB_PASSWORD}"
unset OKIT_USER_DB_PASSWORD
```

Si le Secret existe déjà, sa rotation doit être coordonnée avec PostgreSQL ; le recréer avec une nouvelle valeur ne change pas automatiquement le mot de passe d'une base déjà initialisée.

## Validation et déploiement

```bash
kubectl kustomize k8s/services/ms-utilisateur
kubectl apply --dry-run=server -k k8s/services/ms-utilisateur
kubectl apply -k k8s/services/ms-utilisateur
kubectl -n polytech-dams5-07 rollout status deployment/user-service
```

## Contrôles

```bash
kubectl -n polytech-dams5-07 get pods,services
kubectl -n polytech-dams5-07 port-forward service/user-service 8081:8081
curl --fail http://127.0.0.1:8081/health
curl --fail http://127.0.0.1:8081/metrics
```

Un appel à `/api/v1/users/me` sans JWT doit retourner `401`.

Ces manifests ne suppriment ni l'ancien StatefulSet `user-postgres`, ni son
PVC. Une éventuelle migration des données vers `okit_users`, puis le retrait
des anciennes ressources, constituent une opération séparée à valider avec une
sauvegarde. Ne pas utiliser `kubectl apply --prune` pour cette transition.

La conception et les dépendances restantes sont détaillées dans [`docs/devops/user-service-kubernetes.md`](../../../docs/devops/user-service-kubernetes.md).
