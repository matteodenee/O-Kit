# Gateway applicative et entrée Kubernetes

## Responsabilités

L'entrée O'kit est composée de deux niveaux :

1. Traefik, géré par le cluster, expose l'hôte HTTPS et termine TLS ;
2. `api-gateway`, basé sur Spring Cloud Gateway Server WebFlux, applique les
   politiques transverses des API métier.

Routes publiques :

| Préfixe | Destination Traefik | Destination applicative | Protection |
| --- | --- | --- | --- |
| `/api/v1/users/**` | `api-gateway:http` | `user-service:http` | JWT vérifié par les deux services |
| `/realms/**` | `keycloak:http` | aucune | parcours OIDC public |
| `/resources/**` | `keycloak:http` | aucune | ressources OIDC publiques |

Le chemin `/api/v1/users/**` est conservé tel quel. Spring Cloud Gateway transmet
l'en-tête `Authorization`, puis `user-service` réalise sa propre validation du
JWT avant d'accéder au profil correspondant au claim `sub`.

La console Keycloak, PostgreSQL, `/health` et `/metrics` ne sont pas exposés
par l'Ingress.

## Configuration locale

La gateway écoute sur `http://localhost:8080` avec Docker Compose :

- Keycloak : `http://localhost:8090` ;
- aperçu Expo Web : `http://localhost:19006` ;
- gateway : `http://localhost:8080` ;
- `user-service` reste joignable directement sur `http://localhost:8081`
  uniquement pour le diagnostic local.

Le client doit utiliser `http://localhost:8080` comme URL de base des API. Le
CORS autorise seulement l'origine explicite configurée par
`CORS_ALLOWED_ORIGIN`, avec `http://localhost:19006` par défaut.

## Kubernetes

Les ressources dans `infra/kubernetes/gateway` fournissent :

- une ConfigMap non sensible ;
- un Deployment sans état à deux réplicas ;
- un Service `ClusterIP` ;
- un ServiceMonitor pour `/metrics` ;
- l'Ingress Traefik partagé avec Keycloak.

L'image de la gateway est publiée sous
`registry.gitlab.com/iwa-group2/o-kit/api-gateway:<sha-git>`. L'overlay remplace
le tag factice par le SHA immuable retenu avant le rendu. Le Deployment utilise
le Secret `gitlab-registry`. Aucun secret ni kubeconfig ne doit être ajouté au
dépôt.

## Coexistence avec le nginx initial

Le nginx fourni au groupe peut continuer à traiter `/`. Les préfixes O'kit sont
plus spécifiques. Lorsque le frontend React disposera de son propre Service,
sa route `/` pourra être ajoutée puis le nginx initial retiré de manière
coordonnée.

## Contrôles attendus

Après déploiement :

```bash
curl --fail \
  https://dams5-07.students.polytech.lange.xyz/realms/okit/.well-known/openid-configuration

# Sans JWT, la gateway doit répondre 401 sans contacter user-service.
curl --output /dev/null --write-out '%{http_code}\n' \
  https://dams5-07.students.polytech.lange.xyz/api/v1/users/me

# Avec un JWT valide, la requête doit traverser la gateway puis être validée
# une seconde fois par user-service.
curl --header "Authorization: Bearer ${ACCESS_TOKEN}" \
  https://dams5-07.students.polytech.lange.xyz/api/v1/users/me
```

`ACCESS_TOKEN` est une variable locale et ne doit jamais être enregistré dans
le dépôt ou affiché dans des logs partagés.
