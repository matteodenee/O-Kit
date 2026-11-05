# Déploiement du microservice Catalogue

Le service écoute sur le port 8082 et utilise la base logique
`okit_catalogue` de l'instance PostgreSQL mutualisée.

Le secret `catalogue-db-credentials` doit être provisionné hors Git avant le
déploiement avec les clés suivantes :

- `username` : compte PostgreSQL propriétaire de `okit_catalogue` ;
- `password` : mot de passe fourni par le gestionnaire de secrets du cluster.

Le secret, le kubeconfig et les valeurs réelles ne doivent jamais être ajoutés
au dépôt. La base et son propriétaire doivent exister avant le démarrage du
Deployment. Les manifests ne créent ni instance PostgreSQL, ni PVC, ni secret.

Validation locale :

```bash
kubectl kustomize k8s/services/ms-catalogue
```
