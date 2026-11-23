# Fiche d'inventaire des tests

## Synthèse

L'application possède plusieurs niveaux de tests, mais ils ne valident pas tous le système complet :

- **Tests unitaires** : logique métier, calculs, mapping et autorisations, avec dépendances simulées.
- **Tests de composants / intégration** : contrôleurs, persistance, sécurité ou gateway, généralement dans un contexte Spring et parfois avec PostgreSQL Testcontainers.
- **Tests système E2E locaux** : deux scripts exercent des parcours réels avec Keycloak et l'API Gateway.
- **Tests frontend** : vérifications Node des règles, clients API et structure des routes ; pas de test d'interface exécuté dans un navigateur ou sur un appareil.

Inventaire statique : **113 tests Java**, **18 tests JavaScript/TypeScript** et **2 scripts E2E locaux**. Les deux scripts E2E ne sont pas inclus dans le décompte des tests automatisés Java/JS.

## Backend

| Module | Type | Couverture présente |
|---|---|---|
| `ms-catalogue` | **TU** | Calcul de prix et promotions dans [PriceCalculatorTest.java](../backend/ms-catalogue/src/test/java/com/okit/catalogue/services/pricing/PriceCalculatorTest.java), mapping dans [ProduitMapperTest.java](../backend/ms-catalogue/src/test/java/com/okit/catalogue/mappers/ProduitMapperTest.java), logique métier dans [ProduitServiceTest.java](../backend/ms-catalogue/src/test/java/com/okit/catalogue/services/ProduitServiceTest.java). |
| `ms-catalogue` | **Intégration persistance** | Entités, relations, filtres et repositories dans [ProduitRepositoryTest.java](../backend/ms-catalogue/src/test/java/com/okit/catalogue/repositories/ProduitRepositoryTest.java), avec `@DataJpaTest`. |
| `ms-catalogue` | **Intégration Web** | Contrats HTTP, validation et réponses du contrôleur dans [ProduitControllerTest.java](../backend/ms-catalogue/src/test/java/com/okit/catalogue/controllers/ProduitControllerTest.java), avec contrôleur isolé et service simulé. |
| `ms-catalogue` | **Démarrage / smoke** | Chargement du contexte Spring dans [CatalogueApplicationTests.java](../backend/ms-catalogue/src/test/java/com/okit/catalogue/CatalogueApplicationTests.java). |
| `ms-stock` | **Intégration service + BDD** | [StockServiceTest.java](../backend/ms-stock/src/test/java/com/okit/stock/domain/service/StockServiceTest.java) couvre décrémentation, idempotence, stock insuffisant, alertes, release et concurrence. Le contexte Spring utilise PostgreSQL Testcontainers ([AbstractIntegrationTest.java](../backend/ms-stock/src/test/java/com/okit/stock/AbstractIntegrationTest.java)). Le test de concurrence actuel utilise 5 appels sur un stock de 10, pas le scénario P0 de 50 appels sur un stock de 1. |
| `ms-stock` | **Intégration HTTP / sécurité** | [StockSecurityTest.java](../backend/ms-stock/src/test/java/com/okit/stock/api/controller/StockSecurityTest.java) vérifie avec MockMvc l'accès BUYER, SELLER et SVC_COMMANDE. |
| `ms-commande` | **Intégration de flux de commande** | [CommandeFlowTest.java](../backend/ms-commande/src/test/java/com/okit/commande/CommandeFlowTest.java) couvre panier, commande, paiement, annulation et autorisations via MockMvc et PostgreSQL Testcontainers. Catalogue, stock et paiement sont simulés : ce n'est pas un test E2E inter-services. |
| `ms-utilisateur` | **TU** | Services et conversion des rôles dans [UserProfileServiceTest.java](../backend/ms-utilisateur/src/test/java/fr/polytech/okit/user/profile/UserProfileServiceTest.java), [UserAddressServiceTest.java](../backend/ms-utilisateur/src/test/java/fr/polytech/okit/user/address/UserAddressServiceTest.java) et [RealmRoleJwtAuthenticationConverterTest.java](../backend/ms-utilisateur/src/test/java/fr/polytech/okit/user/config/RealmRoleJwtAuthenticationConverterTest.java). |
| `ms-utilisateur` | **Intégration Web** | Contrôleurs profil et adresses, avec services simulés, dans [UserProfileControllerTest.java](../backend/ms-utilisateur/src/test/java/fr/polytech/okit/user/profile/UserProfileControllerTest.java) et [UserAddressControllerTest.java](../backend/ms-utilisateur/src/test/java/fr/polytech/okit/user/address/UserAddressControllerTest.java). |
| `ms-utilisateur` | **Intégration persistance / HTTP** | [UserProfilePersistenceIntegrationTest.java](../backend/ms-utilisateur/src/test/java/fr/polytech/okit/user/profile/UserProfilePersistenceIntegrationTest.java) valide migrations, persistance et parcours API avec PostgreSQL Testcontainers. |
| `api-gateway` | **Intégration gateway** | [ApiGatewayApplicationTest.java](../services/api-gateway/src/test/java/fr/polytech/okit/gateway/ApiGatewayApplicationTest.java) démarre la gateway sur un port local et teste routes, JWT, CORS et transfert HTTP. Les services aval sont des serveurs simulés et le décodeur JWT est mocké : ce n'est pas le système complet. |

## Frontends

| Application | Type | Couverture présente |
|---|---|---|
| `mobile-client` | **Tests de routes et contrats côté source** | Les tests Node vérifient navigation, intégration des écrans et chemins API : [catalogue-route.test.mjs](../apps/mobile-client/tests/catalogue-route.test.mjs), [commande-route.test.mjs](../apps/mobile-client/tests/commande-route.test.mjs), [user-addresses-route.test.mjs](../apps/mobile-client/tests/user-addresses-route.test.mjs) et [auth-callback-route.test.mjs](../apps/mobile-client/tests/auth-callback-route.test.mjs). Ils ne pilotent pas l'application sur émulateur ou appareil. |
| `seller-backoffice` | **TU / contrats frontend** | Tests Node des règles de rôles et des appels API catalogue : [roles.test.ts](../apps/seller-backoffice/tests/roles.test.ts) et [catalogue-api.test.ts](../apps/seller-backoffice/tests/catalogue-api.test.ts). Ils ne démarrent pas le backend ni un navigateur. |

Commandes frontend : `npm test` dans chaque application, selon les scripts de [mobile-client/package.json](../apps/mobile-client/package.json) et [seller-backoffice/package.json](../apps/seller-backoffice/package.json). Les tests Java se lancent avec Maven dans le module concerné ; les tests Testcontainers nécessitent Docker.

## Tests système E2E locaux

Ces scripts parlent aux services locaux réellement démarrés (Keycloak, API Gateway et base) et exercent donc des parcours plus proches du système complet :

- [test-local-user-flow.sh](../backend/ms-utilisateur/scripts/test-local-user-flow.sh) : inscription acheteur, authentification Authorization Code + PKCE, création et relecture du profil, renouvellement de token et refus sans authentification.
- [test-local-seller-flow.sh](../backend/ms-utilisateur/scripts/test-local-seller-flow.sh) : création d'un vendeur, authentification PKCE, vérification des rôles et refus d'accès au profil acheteur.

Ils sont limités aux parcours identité/profil et ne valident pas le flux complet commande-catalogue-stock-paiement. Ce sont des scripts manuels dépendant d'un environnement local, pas une suite E2E exécutée automatiquement avec les tests unitaires.

## Lacunes notables

- Aucun test E2E ne relie actuellement commande, catalogue, stock et paiement réels.
- Le test de concurrence stock ne vérifie pas **50 requêtes simultanées sur un stock initial de 1**.
- Le contrat `PATCH /api/v1/stocks/variants/{id}/decrement` n'est pas couvert ; le endpoint existant est `POST /internal/stocks/decrement`.
- Pas de test mobile ou backoffice piloté par navigateur/appareil, ni de test de charge dédié identifié.
