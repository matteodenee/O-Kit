# Déploiement Kubernetes de Keycloak

## Périmètre

Le socle `infra/keycloak` déploie :

- Keycloak 26.0.5 en mode serveur ;
- PostgreSQL comme stockage dédié et persistant ;
- le realm `okit` depuis la configuration versionnée ;
- le profil utilisateur Keycloak qui réserve le prénom et le nom aux usages
  administratifs et les retire de l'inscription acheteur ;
- les Services internes, probes et métriques ;
- un Service interne consommé par la gateway d'entrée ;
- une NetworkPolicy protégeant PostgreSQL.

L'image est construite depuis `infra/keycloak/Dockerfile`. Elle prépare
Keycloak pendant le build afin que le conteneur puisse démarrer avec
`--optimized` et un système de fichiers racine en lecture seule. Elle embarque
également le thème `okit-seller`, qui masque l'inscription publique sur le
client vendeur sans la retirer du client acheteur. Sa référence
dans l'overlay `infra/kubernetes/overlays/user-staging` utilise le registre
GitLab du projet avec un tag volontairement invalide jusqu'à ce qu'un SHA Git
immuable soit publié. Le Deployment référence le Secret `gitlab-registry`.

Le namespace cible est `polytech-dams5-07`.

## Exposition et issuer

Keycloak produit des tokens dont l'issuer est :

```text
https://dams5-07.students.polytech.lange.xyz/realms/okit
```

La gateway située dans `infra/kubernetes/gateway` publie `/realms` et
`/resources`. La console `/admin` reste interne et peut être ouverte
temporairement par `port-forward`. Le `user-service` utilise le même issuer
public, mais récupère les clés de signature sur le Service interne
`http://keycloak:8080`.

## Secrets

Le Secret `keycloak-secrets` doit fournir :

- `database-password` ;
- `bootstrap-admin-username` ;
- `bootstrap-admin-password` ;
- `ms-commande-client-secret`.

Aucune valeur réelle n'est conservée dans Git. Le compte bootstrap sert
uniquement à la première administration ; son mot de passe doit être fort et
conservé dans le gestionnaire de secrets retenu par l'équipe.

Le reset de mot de passe est désactivé sur Kubernetes tant qu'aucun SMTP de
production n'est disponible. Le realm versionné ne contient plus la
configuration Mailpit locale. Sa réactivation nécessitera un fournisseur SMTP
et des identifiants injectés via Secret ; aucun identifiant SMTP ne doit être
ajouté au realm JSON versionné.

## Persistance et import du realm

PostgreSQL demande un volume de 2 Gio. Le realm est importé au démarrage
uniquement s'il n'existe pas encore dans la base. Modifier
`infra/keycloak/import/okit-realm.json` ne migre donc pas automatiquement un
realm existant. Après chaque rollout, le script embarqué
`/opt/keycloak/bin/reconcile-kubernetes-realm.sh` synchronise de manière
idempotente les paramètres, rôles, clients, scopes et mappers attendus.

Le PVC utilise la classe `nfs-tower` observée sur le namespace. Il est distinct
du PVC historique `pgdata-pvc`, qui ne doit pas être modifié ou supprimé.

La suppression du PVC Keycloak détruit les comptes et les sessions. Elle ne
doit jamais être utilisée comme mécanisme normal de mise à jour.

## Ressources

| Composant | CPU demandé | Mémoire demandée | CPU maximum | Mémoire maximum |
| --- | ---: | ---: | ---: | ---: |
| Keycloak | 300m | 768Mi | 1000m | 1536Mi |
| PostgreSQL Keycloak | 100m | 256Mi | 500m | 512Mi |

Avec le service utilisateur et sa base, les demandes restent sous le quota
actuel de 4 CPU et 8 Gio de mémoire demandée du namespace.
