# Contraintes Techniques du Cluster Kubernetes (O'kit)

Ce document répertorie l'ensemble des contraintes d'infrastructure, de dimensionnement et de sécurité imposées sur le cluster Kubernetes distant de l'école pour le namespace de notre projet **O'kit**.

Tous les développeurs, contributeurs Docker et responsables CI/CD doivent impérativement s'y conformer. Tout déploiement ne respectant pas ces directives sera **rejeté par l'admission controller** du cluster ou entraînera un blocage de l'infrastructure (`ResourceQuotaExceeded`).

---

## 1. Informations Générales du Cluster

| Paramètre | Valeur | Description |
| :--- | :--- | :--- |
| **Namespace dédié** | `polytech-dams5-07` | Namespace unique alloué à notre équipe |
| **Domaine public** | `dams5-07.students.polytech.lange.xyz` | Point d'entrée DNS public sécurisé |
| **Ingress Controller** | `traefik` | `ingressClassName: traefik` |
| **Gestionnaire TLS / Cert-Manager** | `cert-manager` | ClusterIssuer obligatoire : `letsencrypt-etudiants` |
| **Accès CI/CD** | Secret GitHub `KUBE_CONFIG` | Kubeconfig complet encodé en base64 |

---

## 2. Quotas de Ressources (`ResourceQuota`)

Le namespace est bridé par des quotas stricts appliqués par Kubernetes. Voici le détail des limites maximales cumulées pour l'ensemble du projet :

```yaml
ResourceQuota Summary:
  services: "10"
  pods: "20"
  persistentvolumeclaims: "5"
  requests.cpu: "2"      # 2000m
  limits.cpu: "4"        # 4000m
  requests.memory: "4Gi" # 4096Mi
  limits.memory: "8Gi"   # 8192Mi
```

---

## 3. Gestion Critique des Services (Max 10)

> [!CAUTION]
> **Contrainte critique** : Le cluster interdit la création de plus de **10 objets `Service`** au total dans le namespace `polytech-dams5-07`.

### Règles impératives
1. **Types de services autorisés** :
   - Seul le type `ClusterIP` est autorisé.
   - Les types `LoadBalancer` et `NodePort` sont **strictement interdits** (rejet automatique par les politiques du cluster). L'accès externe se fait impérativement via l'Ingress Traefik.
2. **Architecture ciblée (10 services max)** :
   Notre architecture prévoit à terme :
   - 9 microservices métier : User, Commande, Stock, Catalogue, Avis, Chatbot, Paiement, Retour, KPI.
   - 1 instance Keycloak (authentification).
   - 1 Backoffice web.
   - Bases de données / caches.
   
   Le total brut dépasse 10 services si chaque composant instancie son propre Service Kubernetes.
   
### Stratégie d'optimisation
- **Mutualisation de la base de données** : Ne **jamais** créer un Service K8s de base de données par microservice. Utiliser une instance PostgreSQL centralisée (ou cluster mutualisé) avec un seul Service K8s `postgres` exposant les bases logiques de chaque microservice.
- **Microservices asynchrones sans Service K8s** : Tout microservice qui consomme uniquement des messages d'une file (ex: Worker de synchronisation, générateur de KPI asynchrone) ne doit **pas** avoir d'objet `Service` s'il n'a pas besoin d'être interrogé directement par HTTP/gRPC.
- **Passerelle / Ingress centralisé** : L'Ingress Traefik achemine directement le trafic vers les Services `ClusterIP` des applications exposées.
- **Budget d'allocation des 10 Services** :

| # | Composant | Nom du Service | Type | Port(s) |
|---|---|---|---|---|
| 1 | Authentification | `keycloak` | ClusterIP | `8080` (`http`) |
| 2 | Backoffice Web | `backoffice` | ClusterIP | `80` (`http`) |
| 3 | Base de Données mutualisée | `postgres` | ClusterIP | `5432` (`postgres`) |
| 4 | Microservice User | `ms-user` | ClusterIP | `3000` (`http`) |
| 5 | Microservice Commande | `ms-commande` | ClusterIP | `3000` (`http`) |
| 6 | Microservice Catalogue | `ms-catalogue` | ClusterIP | `3000` (`http`) |
| 7 | Microservice Stock | `ms-stock` | ClusterIP | `3000` (`http`) |
| 8 | Microservice Paiement | `ms-paiement` | ClusterIP | `3000` (`http`) |
| 9 | Microservice Avis | `ms-avis` | ClusterIP | `3000` (`http`) |
| 10 | Microservice Chatbot / Retours | `ms-gateway` / partagé | ClusterIP | `3000` (`http`) |

*(Tout ajout de Service nécessite la validation de l'équipe DevOps pour ne pas saturer le quota).*

---

## 4. Quotas de Pods & Stratégie de Déploiement (Max 20)

- **Nombre maximum de pods simultanés** : 20.
- **Nombre de réplicas** :
  - Chaque microservice et composant doit être configuré avec `replicas: 1` par défaut.
  - Les déploiements doivent utiliser une stratégie de mise à jour progressive contrôlée :
    ```yaml
    strategy:
      type: RollingUpdate
      rollingUpdate:
        maxSurge: 1
        maxUnavailable: 0
    ```
    *Attention : Lors d'un déploiement simultané de plusieurs services, veiller à ce que le nombre temporaire de pods (pods courants + pods créés par le `maxSurge`) ne dépasse pas 20.*

---

## 5. Quotas de Stockage Persistant (Max 5 PVC / 20 Go)

- **Nombre maximum de PVC** : 5.
- **Capacité totale maximale** : 20 Go.
- **Règles d'or** :
  - **100% Stateless pour les microservices** : Aucun microservice d'application ne doit réclamer de PVC.
  - Réservation stricte des PVC pour les composants avec persistance :
    1. `postgres-pvc` : 10 Go (données relationnelles mutualisées).
    2. `keycloak-data-pvc` : 2 Go (données Keycloak si besoin).
    3. `redis-pvc` (si cache persistant nécessaire) : 2 Go.
    4. Réserve disponible : 2 PVC (max 6 Go cumulés restants).
  - Utiliser la `storageClassName` standard du cluster.

---

## 6. Dimensionnement CPU et Mémoire par Conteneur

> [!IMPORTANT]
> **Politique d'admission stricte** :
> Tout pod dont les conteneurs ne spécifient pas **explicitement** à la fois `resources.requests` et `resources.limits` (CPU et mémoire) sera **immédiatement rejeté** lors du déploiement.

### Enveloppe Globale
- **CPU** :
  - Somme des `requests` $\le$ **2 cœurs (2000m)**
  - Somme des `limits` $\le$ **4 cœurs (4000m)**
- **Mémoire** :
  - Somme des `requests` $\le$ **4 Go (4096Mi)**
  - Somme des `limits` $\le$ **8 Go (8192Mi)**

### Profils de dimensionnement types

#### Profil A — Microservice standard (Node.js / Go / Python léger)
```yaml
resources:
  requests:
    cpu: 100m
    memory: 192Mi
  limits:
    cpu: 250m
    memory: 384Mi
```

#### Profil B — Composant Java / Keycloak
```yaml
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: 500m
    memory: 1024Mi
```

#### Profil C — Base de données mutualisée (PostgreSQL)
```yaml
resources:
  requests:
    cpu: 200m
    memory: 384Mi
  limits:
    cpu: 500m
    memory: 768Mi
```

#### Profil D — Frontend web (Nginx / Next.js SSR / Backoffice)
```yaml
resources:
  requests:
    cpu: 50m
    memory: 96Mi
  limits:
    cpu: 150m
    memory: 192Mi
```

### Bilan prévisionnel du dimensionnement

| Composant | Type | CPU Req | CPU Lim | RAM Req | RAM Lim |
| :--- | :--- | :--- | :--- | :--- | :--- |
| Keycloak | Auth | 250m | 500m | 512Mi | 1024Mi |
| PostgreSQL | DB | 200m | 500m | 384Mi | 768Mi |
| Backoffice | Web | 50m | 150m | 96Mi | 192Mi |
| 7 Microservices actifs | Métier ($7 \times 100\text{m}$) | 700m | 1750m | 1344Mi ($7 \times 192$) | 2688Mi ($7 \times 384$) |
| Marge de manœuvre | Buffer | 800m | 1100m | 1760Mi | 3528Mi |
| **Total Alloué (Req / Lim)** | | **1200m / 2000m** | **2900m / 4000m** | **2336Mi / 4096Mi** | **4672Mi / 8192Mi** |
| **Quota Namespace** | | **2000m max** | **4000m max** | **4096Mi max** | **8192Mi max** |

Le budget respecte scrupuleusement les plafonds du `ResourceQuota`.

---

## 7. Ingress, Routage et Certificats TLS

L'accès externe au cluster s'effectue exclusivement au travers de Traefik avec terminaison TLS automatisée par Let's Encrypt.

### Spécifications obligatoires pour les Ingress
- **Ingress Controller** : `traefik` via `spec.ingressClassName: traefik`.
- **Annotations requises** :
  ```yaml
  apiVersion: networking.k8s.io/v1
  kind: Ingress
  metadata:
    name: okit-ingress
    namespace: polytech-dams5-07
    annotations:
      cert-manager.io/cluster-issuer: letsencrypt-etudiants
      traefik.ingress.kubernetes.io/router.entrypoints: websecure
      traefik.ingress.kubernetes.io/router.tls: "true"
  spec:
    ingressClassName: traefik
    tls:
      - hosts:
          - dams5-07.students.polytech.lange.xyz
        secretName: okit-tls-cert
    rules:
      - host: dams5-07.students.polytech.lange.xyz
        http:
          paths:
            - path: /auth
              pathType: Prefix
              backend:
                service:
                  name: keycloak
                  port:
                    number: 8080
            - path: /api/users
              pathType: Prefix
              backend:
                service:
                  name: ms-user
                  port:
                    number: 3000
            - path: /
              pathType: Prefix
              backend:
                service:
                  name: backoffice
                  port:
                    number: 80
  ```

---

## 8. Monitoring & Observabilité (Prometheus / Grafana)

Prometheus et Grafana sont préinstallés sur le cluster pour collecter et visualiser les métriques de santé des applications.

### Exigences pour les microservices
1. **Endpoint `/metrics`** :
   - Chaque application backend doit exposer un endpoint HTTP `/metrics` au format texte standard Prometheus / OpenMetrics.
2. **Ports de Service explicitement nommés** :
   - Dans le manifest `Service`, le port HTTP/métriques **doit obligatoirement avoir un attribut `name`** (ex: `name: http` ou `name: metrics`). C'est indispensable pour que le CRD `ServiceMonitor` puisse résoudre le bon port.
   ```yaml
   spec:
     ports:
       - name: http
         port: 3000
         targetPort: 3000
         protocol: TCP
   ```
3. **Objets `ServiceMonitor`** :
   - Un `ServiceMonitor` sera associé à chaque service monitoré avec les labels adéquats :
   ```yaml
   apiVersion: monitoring.coreos.com/v1
   kind: ServiceMonitor
   metadata:
     name: ms-user-monitor
     namespace: polytech-dams5-07
   spec:
     selector:
       matchLabels:
         app.kubernetes.io/name: ms-user
     endpoints:
       - port: http
         path: /metrics
         interval: 30s
   ```

---

## 9. Checklist de Conformité avant déploiement

Avant d'intégrer ou déployer un nouveau manifest Kubernetes :
- [ ] Le namespace est explicitement `polytech-dams5-07` (ou géré via Kustomize).
- [ ] Le nombre total de `Service` ne dépasse pas 10.
- [ ] Aucun Service n'est de type `LoadBalancer` ou `NodePort`.
- [ ] Chaque conteneur possède `requests.cpu`, `requests.memory`, `limits.cpu`, `limits.memory`.
- [ ] Les valeurs de ressources respectent les profils de dimensionnement.
- [ ] Aucun conteneur applicatif ne revendique de PVC (microservices stateless).
- [ ] Le port du service est explicitement nommé (ex: `name: http`).
- [ ] L'Ingress utilise `ingressClassName: traefik` et l'annotation `cert-manager.io/cluster-issuer: letsencrypt-etudiants`.
- [ ] L'application expose un endpoint `/metrics` pour Prometheus.
