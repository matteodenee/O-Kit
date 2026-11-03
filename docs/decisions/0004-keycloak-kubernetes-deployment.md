# ADR 0004 Déploiement Kubernetes de Keycloak

## Statut

Acceptée pour l'environnement Kubernetes du projet, amendée par l'ADR 0007
pour le stockage du service Utilisateur.

## Contexte

L'ADR 0001 retient Keycloak comme fournisseur d'identité et impose une
configuration reproductible sans secret versionné. Le `user-service` valide les
JWT du realm `okit` et attend un issuer HTTPS public, tout en récupérant les clés
de signature par le réseau interne Kubernetes.

Le mode local `start-dev` et la base embarquée de Keycloak ne conviennent pas au
cluster. Les données d'identité doivent survivre aux redémarrages et rester
séparées de la base métier du service utilisateur.

## Décision

- Keycloak est déployé dans le namespace `polytech-dams5-07` avec une image
  optimisée construite à partir de la version `26.0.5` déjà validée en local.
  L'optimisation Quarkus, le support PostgreSQL, les probes et les métriques
  sont intégrés pendant le build afin de conserver un système de fichiers
  racine en lecture seule à l'exécution.
- Keycloak utilise le mode `start`, HTTP derrière l'Ingress TLS Traefik et les
  en-têtes `X-Forwarded-*` fournis par le proxy.
- Le nom d'hôte public est
  `https://dams5-07.students.polytech.lange.xyz` afin que le claim `iss` des JWT
  corresponde à l'issuer attendu par les services.
- Les routes publiques `/realms` et `/resources` sont envoyées directement vers
  Keycloak. La console d'administration n'est pas publiée par l'Ingress.
- Les données Keycloak sont stockées dans une instance PostgreSQL dédiée avec
  son propre volume persistant.
- Le realm versionné est monté par ConfigMap et importé uniquement lors de la
  création initiale du realm.
- Le mot de passe PostgreSQL et le compte administrateur initial sont fournis
  par un Secret Kubernetes créé hors Git.
- Les endpoints de santé et de métriques sont servis sur le port de management
  interne de Keycloak et collectés par un `ServiceMonitor`.

## Alternatives envisagées

### Conserver la base embarquée de développement

Écartée : elle n'est pas adaptée à un environnement Kubernetes persistant et ne
sépare pas correctement le cycle de vie de l'identité du conteneur.

### Partager la base logique de Keycloak avec le service utilisateur

Écartée : les identités et les profils métier ont des propriétaires distincts.
Keycloak conserve son stockage dédié. L'ADR 0007 autorise en revanche le
service Utilisateur à partager une instance physique avec d'autres services,
dans une base logique et avec des credentials séparés.

### Publier la console d'administration

Écartée pour cette étape. L'administration peut être réalisée par
`port-forward` et ne nécessite pas une surface publique permanente.

## Conséquences

- un volume de 2 Gio reste dédié à la base Keycloak ; le service Utilisateur
  n'ajoute aucun volume PostgreSQL ;
- une modification du JSON du realm ne met pas à jour automatiquement un realm
  déjà importé ; une procédure de migration ou une réinitialisation volontaire
  est nécessaire ;
- la perte du Secret empêche les nouveaux pods de se connecter à la base ;
- le changement du nom d'hôte public invaliderait l'issuer attendu et devrait
  être coordonné avec les clients et les resource servers.
