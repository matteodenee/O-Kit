# Infrastructure Kubernetes — O'kit

Ce répertoire contient l'ensemble des fichiers de configuration, manifests et documentations nécessaires au déploiement de la plateforme e-commerce **O'kit** sur le cluster Kubernetes de Polytech.

Pour prendre connaissance des limites strictes de ressources imposées par le cluster, consultez impérativement le document de référence :
👉 **[CONSTRAINTS.md](./CONSTRAINTS.md)**

---

## 1. Organisation Cible des Manifests

Pour faciliter la maintenance entre l'équipe DevOps et les développeurs de chaque composant, nous préconisons une structure modulaire basée sur **Kustomize** :

```text
k8s/
├── CONSTRAINTS.md                 # Spécifications des quotas, ressources et contraintes réseau
├── README.md                      # Guide d'organisation et d'exploitation (ce fichier)
├── base/                          # Ressources transverses et partagées
│   ├── kustomization.yaml         # Kustomization racine ou transverse
│   ├── namespace.yaml             # Définition du namespace polytech-dams5-07 (si géré par nos soins)
│   ├── ingress.yaml               # Ingress unique Traefik (routage de tous les services)
│   ├── storage/                   # PVC partagées (Postgres, Keycloak)
│   │   └── postgres-pvc.yaml
│   └── monitoring/                # Objets Prometheus Operator globaux
├── services/                      # Découpage modulaire par microservice / composant
│   ├── keycloak/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── configmap.yaml
│   ├── database/
│   │   ├── deployment.yaml
│   │   └── service.yaml
│   ├── backoffice/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── servicemonitor.yaml
│   ├── ms-user/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── servicemonitor.yaml
│   ├── ms-commande/
│   │   └── ...
│   └── ...                        # Autres microservices (stock, catalogue, avis, etc.)
└── overlays/                      # (Optionnel) Variantes d'environnements (staging, prod)
    └── production/
        └── kustomization.yaml
```

---

## 2. Découpage : Globaux vs Par Microservice

### Ressources Globales (`k8s/base/`)
- **Ingress (`ingress.yaml`)** : Afin de respecter le domaine unique (`dams5-07.students.polytech.lange.xyz`) et le certificat TLS émis par cert-manager, l'Ingress est centralisé dans les manifests globaux et achemine vers les différents Services via des préfixes d'URL (`/`, `/auth`, `/api/...`).
- **Stockage Persistant (`storage/`)** : Les PVC sont limitées à 5 au total (20 Go). Elles sont gérées au niveau global pour éviter toute sur-allocation accidentelle par un microservice.

### Ressources Dédiées (`k8s/services/<composant>/`)
Chaque sous-dossier de microservice contient uniquement :
- `deployment.yaml` : Définition des réplicas (1 par défaut), conteneur, probes (`livenessProbe`, `readinessProbe`) et ressources CPU/RAM (`requests` et `limits` obligatoires).
- `service.yaml` : Service de type `ClusterIP` avec port nommé (ex: `name: http`).
- `configmap.yaml` / `secret.yaml` : Variables d'environnement non sensibles ou références de secrets.
- `servicemonitor.yaml` : Configuration de scraping pour Prometheus sur `/metrics`.

---

## 3. Conventions de Nommage et Labels Standard

Tous les manifests doivent adopter les labels recommandés par Kubernetes pour faciliter le filtrage et l'observabilité :

```yaml
metadata:
  labels:
    app.kubernetes.io/name: ms-user
    app.kubernetes.io/instance: okit
    app.kubernetes.io/part-of: okit
    app.kubernetes.io/component: backend
    app.kubernetes.io/managed-by: kustomize
```

---

## 4. Guide des Commandes Utiles

### Vérification locale avant commit (Dry-run / Validation syntaxique)
```bash
# Vérifier la validité des manifests sans appliquer
kubectl apply --dry-run=client -f k8s/services/ms-user/

# Avec Kustomize
kubectl kustomize k8s/services/ms-user/
```

### Vérification de l'état du cluster et des quotas
```bash
# Vérifier les pods en cours d'exécution
kubectl get pods -n polytech-dams5-07

# Vérifier la consommation par rapport aux quotas
kubectl describe resourcequota -n polytech-dams5-07

# Vérifier les services actifs (rappel : 10 max !)
kubectl get svc -n polytech-dams5-07

# Inspecter l'Ingress et les certificats TLS
kubectl get ingress,certificates,certificaterequests -n polytech-dams5-07
```

---

## 5. Intégration Continue (CI/CD)

Le workflow GitHub Actions `.github/workflows/k8s-check.yml` valide à chaque push et sur déclenchement manuel :
1. La connectivité au cluster Kubernetes de Polytech.
2. L'accès au namespace `polytech-dams5-07`.
3. L'état actuel des pods, des services et de l'Ingress.
4. Le respect des limites du `ResourceQuota`.
