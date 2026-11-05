# Données de démonstration du Catalogue

## Objectif

Le jeu de données de développement permet de vérifier visuellement le
catalogue acheteur sans saisir manuellement chaque produit. Il contient 18
maillots actifs et un produit désactivé servant à vérifier le filtrage côté
client.

Ces données sont fictives. Elles ne constituent ni un catalogue commercial ni
une représentation officielle des marques, clubs ou sélections cités.

## Choix d'implémentation

Le microservice Catalogue utilise actuellement Hibernate avec
`ddl-auto=update` et ne possède pas de mécanisme de migration ou de seed. Le
jeu de données est donc fourni sous la forme d'un script SQL PostgreSQL lancé
explicitement :

- il fonctionne sur une base Docker existante après la création du schéma par
  le microservice Catalogue ;
- il n'ajoute aucun comportement de développement au binaire Spring Boot ;
- il ne s'exécute jamais automatiquement en production ;
- il ne modifie ni les entités ni la structure de la base ;
- il ne supprime et ne met à jour aucune donnée existante.

Le script prend un verrou transactionnel puis identifie ses produits par leur
titre de démonstration stable. Une nouvelle exécution complète uniquement les
références ou produits absents et ne crée pas de doublon.

## Limites du modèle actuel

Le modèle Catalogue ne contient pas encore :

- de champ identifiant le sport ;
- de tailles ou de stock par taille ;
- d'image associée à un produit.

Le seed renseigne donc uniquement les informations réellement portées par le
modèle : titre, description, équipe, type d'équipe, saison, marque, prix HT,
TVA, poids indicatif, couleurs, caractère floquable, stock global et statut.
Les maillots de rugby et les images ne sont pas ajoutés afin de ne pas encoder
une information fausse ou une URL externe non maîtrisée.

## Initialiser une base locale existante

Depuis la racine du dépôt :

```bash
docker compose up -d postgres catalogue-service
docker compose exec postgres /opt/okit/seed-catalogue-demo.sh
```

Le premier démarrage de `catalogue-service` doit être terminé afin qu'Hibernate
ait créé les tables. Le script affiche ensuite le nombre de produits de
démonstration actifs et désactivés présents.

## Réappliquer sans supprimer les données existantes

La même commande est volontairement réutilisable :

```bash
docker compose exec postgres /opt/okit/seed-catalogue-demo.sh
```

Elle recrée uniquement les lignes manquantes. Elle ne remet pas à leur valeur
initiale les produits de démonstration qui auraient été modifiés, car cela
écraserait des données existantes. Elle ne supprime jamais les autres produits
du catalogue et ne nécessite pas de supprimer le volume PostgreSQL.

## Vérifier le résultat

```bash
docker compose exec postgres sh -c \
  'psql --username "$POSTGRES_USER" --dbname "$CATALOGUE_DB_NAME" \
  --command "SELECT statut, count(*) FROM produit GROUP BY statut ORDER BY statut;"'
```

L'application acheteur appelle
`GET /api/v1/products?statut=ACTIF&size=20` : le produit désactivé ne doit donc
pas apparaître dans Expo Web ou Android.
