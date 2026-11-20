# ms-commande sur Kubernetes

Panier, flocage et commandes (acheteur `/api/v1/panier`, `/api/v1/commandes`,
vendeur `/api/v1/seller/commandes`). Exposé uniquement via l'API Gateway
(`COMMANDE_SERVICE_URL=http://ms-commande:8085`) : aucune route Ingress dédiée.

| Ressource | Nom | Détail |
| :--- | :--- | :--- |
| Deployment | `ms-commande` | 1 réplica, non-root (uid 10001), FS en lecture seule |
| Service | `ms-commande` | ClusterIP, port `8085` nommé `http` |
| ConfigMap | `ms-commande-config` | URLs, issuer OIDC, base `okit_commande` |
| Secret (hors Git) | `ms-commande-secrets` | `database-password`, `stock-client-secret` |
| ServiceMonitor | `ms-commande` | `/actuator/prometheus` toutes les 30 s |

Ressources : 100m / 256Mi demandés, 500m / 512Mi max.

## Dépendances

- **PostgreSQL mutualisé `postgres-db`** : base logique `okit_commande`, rôle
  `okit_commande`. Flyway crée le schéma au démarrage.
- **Keycloak** : vérification des JWT (`BUYER`, `SELLER`) et client confidentiel
  `ms-commande` (service account avec le rôle `SVC_COMMANDE`) pour appeler
  ms-stock `/internal`.
- **ms-catalogue** (`ms-catalogue:8082`) : prix et disponibilité à l'ajout au
  panier et à la validation.
- **ms-stock** (`ms-stock:8083`) : contrôle, décrément et libération du stock.
  Tant qu'il n'est pas déployé, la consultation et le panier fonctionnent mais
  **la validation de commande échoue** (503).
- Paiement : simulé (`MockPaiementClient`), aucune dépendance.

## Prérequis à faire une seule fois

Toutes les commandes s'exécutent dans le namespace `polytech-dams5-07`.

1. **Base de données** : créer le rôle et la base sur `postgres-db`, avec un
   mot de passe généré sans l'afficher :

   ```bash
   DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"
   kubectl exec -i deploy/postgres-db -- psql -U postgres -v ON_ERROR_STOP=1 \
     -v pwd="$DB_PASSWORD" <<'SQL'
   CREATE ROLE okit_commande LOGIN PASSWORD :'pwd';
   CREATE DATABASE okit_commande OWNER okit_commande;
   SQL
   ```

   Adapter `deploy/postgres-db` et l'utilisateur admin à l'instance réelle.

2. **Client Keycloak** : le realm du cluster a peut-être été importé avant
   l'ajout du client `ms-commande`. Dans la console d'administration, vérifier
   que le client existe (sinon le créer comme dans
   `infra/keycloak/import/okit-realm.json`), puis **régénérer son secret** : le
   secret `ms-commande-dev-secret` de l'import est réservé au poste local.

3. **Secret Kubernetes** :

   ```bash
   kubectl create secret generic ms-commande-secrets \
     --from-literal=database-password="$DB_PASSWORD" \
     --from-literal=stock-client-secret="<secret régénéré du client ms-commande>"
   ```

## Construire et publier l'image

Tag immuable = SHA court du commit (le tag `latest` est interdit) :

```bash
TAG="$(git rev-parse --short HEAD)"
docker build --pull -t registry.gitlab.com/iwa-group2/o-kit/ms-commande:$TAG backend/ms-commande
docker push registry.gitlab.com/iwa-group2/o-kit/ms-commande:$TAG
```

Puis reporter ce tag dans `kustomization.yaml` (`newTag`).

## Déployer

```bash
kubectl kustomize k8s/services/ms-commande > /tmp/ms-commande.yaml
kubectl apply --dry-run=server -f /tmp/ms-commande.yaml
kubectl diff -f /tmp/ms-commande.yaml
kubectl apply -f /tmp/ms-commande.yaml
kubectl rollout status deploy/ms-commande --timeout=5m
```

Puis redéployer la gateway pour qu'elle prenne `COMMANDE_SERVICE_URL` :

```bash
kubectl apply -k infra/kubernetes/gateway
kubectl rollout restart deploy/api-gateway
```

## Vérifications

```bash
kubectl logs deploy/ms-commande | grep -i "flyway\|started"
# sans JWT : 401 attendu
curl -i https://dams5-07.students.polytech.lange.xyz/api/v1/panier
```

Avec un JWT `BUYER`, `GET /api/v1/panier` renvoie `200`. Avec un JWT `SELLER`,
`GET /api/v1/seller/commandes` renvoie `200`. Enfin, la cible `ms-commande`
doit être `UP` dans Prometheus.

## Retour arrière

`kubectl rollout undo deploy/ms-commande`, ou
`kubectl delete -k k8s/services/ms-commande` pour retirer le service. Ne jamais
supprimer la base `okit_commande`.
