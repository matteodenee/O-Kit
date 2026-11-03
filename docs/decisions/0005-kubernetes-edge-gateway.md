# ADR 0005 Traefik en bordure et Spring Cloud Gateway applicative

## Statut

Acceptée. Cette version remplace la décision initiale qui utilisait uniquement
Traefik pour router directement les API vers les microservices.

## Contexte

Le cluster du groupe fournit Traefik comme contrôleur Ingress et impose une
exposition HTTPS par Ingress, sans `NodePort` ni `LoadBalancer`. Le projet doit
publier les parcours OIDC de Keycloak et les API métier sur l'unique hôte
`dams5-07.students.polytech.lange.xyz`.

Les supports d'architecture du projet retiennent désormais Spring Cloud
Gateway pour centraliser les préoccupations applicatives transverses :
validation des JWT, politiques d'accès, CORS, routage vers les microservices et
observabilité. Traefik et Spring Cloud Gateway ont donc des responsabilités
complémentaires et ne se remplacent pas.

## Décision

- Traefik reste le point d'entrée Kubernetes et termine le trafic TLS.
- Une application `api-gateway` basée sur Spring Cloud Gateway Server WebFlux
  devient le point d'entrée de toutes les API métier sous `/api/**`.
- L'Ingress route `/api/**` vers `api-gateway`.
- `api-gateway` route `/api/v1/users/**` vers le Service interne `user-service`
  sans réécrire le chemin.
- `/realms` et `/resources` restent routés directement par Traefik vers
  Keycloak afin de publier les parcours OIDC sans les soumettre au filtre JWT
  de la gateway applicative.
- La gateway valide les JWT avant de transmettre les requêtes métier. L'en-tête
  `Authorization` est conservé afin que chaque microservice valide également
  le JWT et applique ses autorisations : c'est une défense en profondeur.
- Le CORS des API publiques est configuré uniquement dans `api-gateway` avec
  des origines explicites. Les microservices ne produisent pas d'en-têtes CORS.
- Les endpoints de santé et de métriques restent internes au cluster.
- `api-gateway` est sans état et possède deux réplicas Kubernetes.

## Alternatives envisagées

### Utiliser uniquement Traefik

Cette solution était retenue dans la première version de l'ADR. Elle est
écartée car elle ne matérialise pas la gateway applicative Spring attendue et
répartit les politiques transverses entre l'Ingress et les microservices.

### Faire transiter Keycloak par Spring Cloud Gateway

Écartée. Les endpoints OIDC doivent rester disponibles avant authentification
et Keycloak possède déjà une frontière de sécurité dédiée. Traefik les expose
directement sur des préfixes limités.

### Désactiver la validation JWT dans les microservices

Écartée. Une requête interne ne doit pas devenir fiable uniquement parce
qu'elle provient du réseau du cluster. La gateway réalise un premier contrôle,
mais le service propriétaire des données conserve la validation finale.

### Exposer les Services avec `NodePort` ou `LoadBalancer`

Écartée car interdite par les règles du cluster et inutile avec Traefik.

## Conséquences

- une image et un Deployment supplémentaires sont nécessaires ;
- la gateway devient un composant critique et doit disposer de deux réplicas,
  de probes, de limites de ressources et de métriques ;
- toute nouvelle famille d'API publique nécessite une route explicite dans la gateway ;
- le `user-service` n'est plus une destination directe de l'Ingress ;
- les clients locaux appellent la gateway, et non directement les
  microservices ;
- une panne de la gateway bloque les API métier mais pas les parcours OIDC de
  Keycloak ;
- le nginx initial peut rester sur `/` pendant la construction du frontend,
  car les routes O'kit sont plus spécifiques.
