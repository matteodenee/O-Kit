# ADR 0007 — Harmonisation du microservice Utilisateur

## Statut

Acceptée.

## Contexte

Le microservice Utilisateur a été développé sous `services/user-service`, sur
le port interne 8080, avec les routes `/api/users/**` et une instance PostgreSQL
Kubernetes dédiée. Les conventions communes d'intégration fixent désormais un
port, un préfixe REST, une arborescence et un modèle PostgreSQL homogènes entre
microservices.

Le service possède déjà un UUID métier indépendant du claim Keycloak `sub`.
Les autres domaines, notamment Avis, doivent référencer cet UUID sans accéder à
la base Utilisateur ni recevoir les informations privées du profil.

## Décision

- le code du service est placé dans `backend/ms-utilisateur/` ;
- ses manifests sont placés dans `k8s/services/ms-utilisateur/` ;
- le service écoute sur le port 8081 dans l'application, l'image, Compose et
  Kubernetes ;
- ses routes sont versionnées sous `/api/v1/users/**` ;
- la gateway reste le point d'entrée public sur le port 8080 et transmet le
  chemin sans réécriture ;
- la base logique du service est `okit_users` ;
- l'hôte, le port, le nom, l'utilisateur et le mot de passe PostgreSQL sont
  fournis par variables d'environnement ;
- Kubernetes réutilise l'instance PostgreSQL physique mutualisée du projet et
  ne crée plus de StatefulSet ni de volume propres au service ;
- Flyway reste seul responsable du schéma métier dans `okit_users` ;
- `GET /api/v1/users/me/id` résout l'UUID métier à partir du `sub` du JWT
  transmis, sans paramètre d'identité et sans exposer le profil complet ;
- le budget Kubernetes initial est d'un réplica, 75m/192Mi en requests et
  200m/384Mi en limits. Il doit être revu si un test sous contrainte démontre
  qu'il est insuffisant.

## Alternatives envisagées

### Conserver les chemins et ports historiques

Écartée : cela maintiendrait une exception coûteuse à intégrer dans la gateway,
les scripts, les manifests et la documentation commune.

### Exposer le profil complet aux autres services

Écartée : les consommateurs ont besoin de l'UUID métier, pas du téléphone, de
la date de naissance ou de l'e-mail courant.

### Utiliser directement le claim Keycloak `sub`

Écartée : `sub` identifie l'identité technique. Les relations métier doivent
rester stables derrière l'UUID possédé par le service Utilisateur.

### Déployer un PostgreSQL par microservice

Écartée pour cet environnement : l'isolation logique par base suffit et évite
un StatefulSet et un PVC supplémentaires. Chaque service garde ses credentials
et son schéma, sans accès direct aux bases des autres domaines.

## Conséquences

- tous les consommateurs doivent migrer vers `/api/v1/users/**` ;
- le service Avis devra résoudre puis stocker l'UUID retourné par `/me/id` ;
- la base `okit_users` et son compte doivent exister sur PostgreSQL avant le
  déploiement ; leur création relève de l'administration de l'instance partagée ;
- les données existantes doivent être migrées vers `okit_users` avant le retrait
  d'une ancienne instance dédiée ; aucun PVC n'est supprimé par ces manifests ;
- les chemins de build, scripts et pipeline doivent suivre la nouvelle
  arborescence.
