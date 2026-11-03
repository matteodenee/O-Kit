# Intégration interservices avec le service Utilisateur

## Objectif

Les autres microservices référencent un acheteur par l'UUID métier possédé par
`user-service`, jamais par le claim Keycloak `sub` et jamais par un accès direct
à la table `user_profiles`.

## Résolution de l'identifiant

```http
GET /api/v1/users/me/id
Authorization: Bearer <access-token de l'acheteur>
```

Réponse `200 OK` :

```json
{
  "id": "7c4b5ef3-84c4-41f2-995a-f2a2ca7474d2"
}
```

Le service Utilisateur déduit le profil exclusivement du claim signé `sub`.
La requête n'accepte ni UUID, ni `sub`, ni identifiant utilisateur dans l'URL,
les paramètres ou le corps. Elle ne retourne aucune donnée privée du profil.

## Sécurité et erreurs

- `401 Unauthorized` : token absent, expiré ou invalide ;
- `403 Forbidden` : token sans rôle `BUYER`, avec `SELLER`, ou avec les deux ;
- `404 Not Found` : aucun profil métier n'est encore associé au `sub` ;
- le JWT original doit être transmis à la gateway puis à `user-service` ;
- aucun token complet ne doit être journalisé ou persisté par le consommateur.

## Contrat à appliquer dans le service Avis

Lors de la création d'un avis authentifié, le service Avis doit :

1. conserver le bearer token uniquement pendant le traitement de la requête ;
2. appeler `GET /api/v1/users/me/id` via le réseau interne ou le chemin
   d'intégration validé par l'équipe ;
3. utiliser la propriété `id` comme identifiant de l'auteur ;
4. ne jamais accepter un UUID d'auteur fourni par le client ;
5. propager une erreur fonctionnelle cohérente si le profil n'existe pas ou si
   la résolution est refusée.

Le service Avis n'est pas modifié dans cette branche. Le timeout, la stratégie
de reprise et le comportement en cas d'indisponibilité du service Utilisateur
restent à décider avec son développeur avant l'intégration finale.

Le schéma normatif de la réponse et les codes HTTP figurent dans
[`user-service.openapi.yaml`](user-service.openapi.yaml).
