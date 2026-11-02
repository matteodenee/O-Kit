# Gateway O'kit sur Kubernetes

Ce dossier déploie la gateway applicative Spring Cloud Gateway et l'Ingress
Traefik commun dans le namespace `polytech-dams5-07`.

## Prérequis

- Keycloak déployé avec le Service interne `keycloak` ;
- `user-service` déployé avec son Service interne ;
- image `api-gateway` publiée dans un registre accessible au cluster ;
- IngressClass `traefik` disponible ;
- certificat du domaine du groupe disponible dans le Secret `nginx-tls` ;
- CRD `ServiceMonitor` disponible pour la ressource d'observabilité.

Avant le déploiement, remplacer dans `kustomization.yaml` :

```yaml
newName: registry.example.invalid/okit/api-gateway
newTag: replace-me
```

par le registre et le tag immuable produits par le pipeline.

## Validation et déploiement

```bash
kubectl kustomize infra/kubernetes/gateway
kubectl apply --dry-run=server -k infra/kubernetes/gateway
kubectl apply -k infra/kubernetes/gateway
kubectl rollout status deployment/api-gateway \
  --namespace polytech-dams5-07
```

Le nginx initial peut continuer à traiter `/`. Ne le supprimez pas avant que le
frontend React possède son propre Deployment et son propre Service.

## Contrôles

```bash
curl --fail \
  https://dams5-07.students.polytech.lange.xyz/realms/okit/.well-known/openid-configuration

# Sans JWT, le statut attendu est 401, émis par api-gateway.
curl --output /dev/null --write-out '%{http_code}\n' \
  https://dams5-07.students.polytech.lange.xyz/api/v1/users/me
```

Avec un JWT valide, `GET /api/v1/users/me` est routé vers `user-service`, qui
valide à nouveau le token. Les détails d'architecture sont documentés dans
[`docs/devops/edge-gateway-kubernetes.md`](../../../docs/devops/edge-gateway-kubernetes.md).
