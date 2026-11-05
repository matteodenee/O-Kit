# Checklist d'intégration du microservice Utilisateur

Cette branche reste indépendante. La liste suivante décrit les adaptations à
reprendre lors de l'intégration finale, sans effectuer de merge ni de rebase ici.

## Build et CI/CD

- utiliser `backend/ms-utilisateur` comme contexte Maven et Docker ;
- conserver `mvn verify` et les tests d'intégration Testcontainers ;
- publier l'image `user-service` avec un tag immuable lié au commit ;
- rendre les manifests depuis `k8s/services/ms-utilisateur` ;
- conserver les étapes communes `lint -> test -> build -> scan -> deploy` ;
- ne pas déployer si les tests, scans ou validations de manifests échouent.

Le pipeline commun est présent sur cette branche. Il exécute actuellement les
tests du Catalogue selon les chemins modifiés. La vérification de disponibilité
Kubernetes `k8s-check` est manuelle dans les pipelines lancés depuis l'interface
GitLab ou sur `main` ; elle n'est pas ajoutée aux pipelines de Merge Request.
Le job de test du service Utilisateur et ses chemins devront être ajoutés au
pipeline commun au moment de l'intégration finale.

## Contrats et consommateurs

- migrer les appels de `/api/users/**` vers `/api/v1/users/**` ;
- configurer la gateway pour transmettre le chemin sans réécriture ;
- communiquer au service Avis le contrat
  [`GET /api/v1/users/me/id`](../api/user-service-integration.md) ;
- vérifier qu'aucun consommateur ne persiste le `sub` Keycloak comme identifiant
  métier et qu'aucun client ne peut fournir l'UUID de l'auteur.

## PostgreSQL

- provisionner la base logique `okit_users` et un compte dédié sur l'instance
  PostgreSQL mutualisée ;
- injecter hôte, port, base, utilisateur et mot de passe par l'environnement ;
- sauvegarder puis migrer les profils d'une éventuelle ancienne base dédiée ;
- vérifier la migration et le nombre de lignes avant tout retrait de l'ancien
  StatefulSet ou PVC ;
- ne jamais appliquer cette transition avec `kubectl apply --prune`.

## Déploiement

- réserver le port 8081 au service Utilisateur et le port 8080 à la gateway ;
- confirmer le budget 75m/192Mi en requests et 200m/384Mi en limits par des
  mesures de démarrage et de charge dans l'environnement cible ;
- mettre à jour ensemble les tags d'images de l'overlay staging ;
- effectuer un dry-run serveur et relire `kubectl diff` avant tout déploiement ;
- tester `401`, `403`, profil absent, lecture/écriture du profil et résolution
  de l'UUID métier avec un JWT `BUYER`.
