# Déploiement Kubernetes de mcp-service

Le service expose MCP Streamable HTTP sur le port interne 8089. Il est publié
uniquement par l'Ingress existant, via la Gateway et le chemin exact `/mcp`.
Aucun `NodePort` ou `LoadBalancer` n'est créé.

```bash
kubectl kustomize k8s/services/mcp-service
kubectl apply -k k8s/services/mcp-service
```

Le déploiement ne contient aucun secret. Il relaie le JWT reçu vers Stock ou
Commande et utilise les URLs internes configurées dans `configmap.yaml`.
