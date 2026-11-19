package com.okit.commande;

import com.okit.commande.client.CatalogueClient;
import com.okit.commande.client.CatalogueClient.ProduitCatalogue;
import com.okit.commande.client.PaiementClient;
import com.okit.commande.client.PaiementClient.ResultatPaiement;
import com.okit.commande.client.StockClient;
import com.okit.commande.domain.exception.StockInsuffisantException;
import com.okit.commande.persistence.repository.CommandeRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommandeFlowTest extends AbstractIntegrationTest {

    private static final String ADRESSE = """
            {"adresseLivraison": {"rue": "1 rue de la Paix", "codePostal": "75002", "ville": "Paris"}}
            """;

    @Autowired
    MockMvc mvc;
    @Autowired
    CommandeRepository commandeRepository;

    // Les autres microservices sont simulés
    @MockitoBean
    CatalogueClient catalogueClient;
    @MockitoBean
    StockClient stockClient;
    @MockitoBean
    PaiementClient paiementClient;

    @BeforeEach
    void setUp() {
        commandeRepository.deleteAll();
        // Produit 1 : flocable, 50 EUR HT, TVA 20 %, tailles M (id 11) et L (id 12)
        when(catalogueClient.getProduit(1L)).thenReturn(produit(1L, true));
        // Produit 2 : non flocable
        when(catalogueClient.getProduit(2L)).thenReturn(produit(2L, false));
        when(stockClient.estDisponible(anyLong(), anyInt())).thenReturn(true);
        when(paiementClient.payer(anyLong(), anyString(), any())).thenReturn(new ResultatPaiement(true, "TX-1"));
    }

    @Test
    @DisplayName("Sans token : 401 ; acheteur sur une route vendeur : 403")
    void securite() throws Exception {
        mvc.perform(get("/api/v1/panier")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/seller/commandes").with(acheteur("u1"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Ajout d'un maillot floqué : prix du flocage inclus dans le total")
    void ajoutAvecFlocage() throws Exception {
        mvc.perform(post("/api/v1/panier/lignes").with(acheteur("u1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idProduit": 1, "idVariante": 11, "quantite": 2,
                                 "flocage": {"texte": "Zidane", "numero": 10}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("PANIER"))
                .andExpect(jsonPath("$.lignes[0].flocage.texte").value("ZIDANE"))
                .andExpect(jsonPath("$.lignes[0].totalHt").value(125.00))   // (50 + 12.50) x 2
                .andExpect(jsonPath("$.prixHt").value(125.00))
                .andExpect(jsonPath("$.totalTtc").value(154.90));           // 150.00 + 4.90 de livraison
    }

    @Test
    @DisplayName("Flocage sur un produit non flocable : 400")
    void flocageRefuse() throws Exception {
        mvc.perform(post("/api/v1/panier/lignes").with(acheteur("u1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idProduit": 2, "idVariante": 21, "quantite": 1,
                                 "flocage": {"texte": "MBAPPE", "numero": 7}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Validation : paiement, décrément du stock, commande CONFIRMEE, panier vidé")
    void validationOk() throws Exception {
        ajouterAuPanier("u1", 1L, 11L, 2);

        mvc.perform(post("/api/v1/commandes").with(acheteur("u1"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADRESSE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("CONFIRMEE"))
                .andExpect(jsonPath("$.idTransaction").value("TX-1"))
                .andExpect(jsonPath("$.adresseLivraison.ville").value("Paris"));

        verify(stockClient).decrementer(startsWith("CMD-"), eq(List.of(new StockClient.LigneStock(11L, 2))));
        mvc.perform(get("/api/v1/panier").with(acheteur("u1")))
                .andExpect(jsonPath("$.lignes").isEmpty());
        mvc.perform(get("/api/v1/commandes").with(acheteur("u1")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Stock insuffisant à la validation : 409, remboursement, panier conservé")
    void validationStockInsuffisant() throws Exception {
        ajouterAuPanier("u1", 1L, 11L, 2);
        doThrow(new StockInsuffisantException(List.of(new StockInsuffisantException.Manque(11L, 2, 1))))
                .when(stockClient).decrementer(anyString(), anyList());

        mvc.perform(post("/api/v1/commandes").with(acheteur("u1"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADRESSE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.manques[0].variantId").value(11));

        verify(paiementClient).rembourser(eq("TX-1"), any(BigDecimal.class));
        mvc.perform(get("/api/v1/panier").with(acheteur("u1")))
                .andExpect(jsonPath("$.lignes.length()").value(1));
    }

    @Test
    @DisplayName("Annulation d'une ligne : stock libéré, total recalculé")
    void annulationLigne() throws Exception {
        ajouterAuPanier("u1", 1L, 11L, 1);
        ajouterAuPanier("u1", 1L, 12L, 1);
        mvc.perform(post("/api/v1/commandes").with(acheteur("u1"))
                .contentType(MediaType.APPLICATION_JSON).content(ADRESSE));
        Long idCommande = commandeRepository.findAll().getFirst().getId();
        String json = mvc.perform(get("/api/v1/commandes/" + idCommande).with(acheteur("u1")))
                .andReturn().getResponse().getContentAsString();
        long ligne = JsonPath.<Number>read(json, "$.lignes[0].idLigneCommande").longValue();

        mvc.perform(post("/api/v1/commandes/" + idCommande + "/lignes/" + ligne + "/annulation")
                        .with(acheteur("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CONFIRMEE"))
                .andExpect(jsonPath("$.lignes[0].statut").value("ANNULEE"))
                .andExpect(jsonPath("$.prixHt").value(50.00));

        verify(stockClient).liberer(endsWith("-L" + ligne), eq(List.of(new StockClient.LigneStock(11L, 1))));
    }

    @Test
    @DisplayName("Prix modifié au catalogue avant la validation : 409 sans paiement, panier mis à jour, puis validation au nouveau prix")
    void validationPrixModifie() throws Exception {
        ajouterAuPanier("u1", 1L, 11L, 1);
        when(catalogueClient.getProduit(1L)).thenReturn(produit(1L, true, "60.00", "ACTIF"));

        mvc.perform(post("/api/v1/commandes").with(acheteur("u1"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADRESSE))
                .andExpect(status().isConflict());
        verify(paiementClient, never()).payer(anyLong(), anyString(), any());

        mvc.perform(get("/api/v1/panier").with(acheteur("u1")))
                .andExpect(jsonPath("$.lignes[0].prixUnitaireHt").value(60.00))
                .andExpect(jsonPath("$.totalTtc").value(76.90));             // 72.00 + 4.90 de livraison

        mvc.perform(post("/api/v1/commandes").with(acheteur("u1"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADRESSE))
                .andExpect(status().isCreated());
        verify(paiementClient).payer(anyLong(), eq("u1"), eq(new BigDecimal("76.90")));
    }

    @Test
    @DisplayName("Produit désactivé avant la validation : 409 sans paiement ni décrément")
    void validationProduitDesactive() throws Exception {
        ajouterAuPanier("u1", 1L, 11L, 1);
        when(catalogueClient.getProduit(1L)).thenReturn(produit(1L, true, "50.00", "DESACTIVE"));

        mvc.perform(post("/api/v1/commandes").with(acheteur("u1"))
                        .contentType(MediaType.APPLICATION_JSON).content(ADRESSE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Maillot 1 (M)")));
        verify(paiementClient, never()).payer(anyLong(), anyString(), any());
        verify(stockClient, never()).decrementer(anyString(), anyList());
    }

    @Test
    @DisplayName("Deux annulations simultanées : un seul remboursement")
    void annulationsConcurrentes() throws Exception {
        ajouterAuPanier("u1", 1L, 11L, 1);
        mvc.perform(post("/api/v1/commandes").with(acheteur("u1"))
                .contentType(MediaType.APPLICATION_JSON).content(ADRESSE));
        Long id = commandeRepository.findAll().getFirst().getId();
        // Remboursement lent : sans verrou, les deux requêtes passeraient la vérification du statut
        doAnswer(inv -> {
            Thread.sleep(300);
            return null;
        }).when(paiementClient).rembourser(anyString(), any());

        CompletableFuture<Integer> acheteur = CompletableFuture.supplyAsync(() -> annuler(
                post("/api/v1/commandes/" + id + "/annulation").with(acheteur("u1"))));
        CompletableFuture<Integer> vendeur = CompletableFuture.supplyAsync(() -> annuler(
                patch("/api/v1/seller/commandes/" + id + "/statut").with(vendeur())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statut\": \"ANNULEE\"}")));

        List<Integer> statuts = List.of(acheteur.join(), vendeur.join());
        org.assertj.core.api.Assertions.assertThat(statuts).containsExactlyInAnyOrder(200, 409);
        verify(paiementClient, times(1)).rembourser(eq("TX-1"), any());
    }

    @Test
    @DisplayName("Un acheteur ne voit pas les commandes d'un autre : 404")
    void isolationUtilisateurs() throws Exception {
        ajouterAuPanier("u1", 1L, 11L, 1);
        mvc.perform(post("/api/v1/commandes").with(acheteur("u1"))
                .contentType(MediaType.APPLICATION_JSON).content(ADRESSE));
        Long id = commandeRepository.findAll().getFirst().getId();

        mvc.perform(get("/api/v1/commandes/" + id).with(acheteur("u2"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Vendeur : expédition sans code de suivi refusée, puis acceptée avec")
    void vendeurExpedition() throws Exception {
        ajouterAuPanier("u1", 1L, 11L, 1);
        mvc.perform(post("/api/v1/commandes").with(acheteur("u1"))
                .contentType(MediaType.APPLICATION_JSON).content(ADRESSE));
        Long id = commandeRepository.findAll().getFirst().getId();
        String url = "/api/v1/seller/commandes/" + id + "/statut";

        mvc.perform(patch(url).with(vendeur()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"statut\": \"EN_PREPARATION\"}")).andExpect(status().isOk());
        mvc.perform(patch(url).with(vendeur()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"statut\": \"EXPEDIEE\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch(url).with(vendeur()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\": \"EXPEDIEE\", \"codeSuiviTransporteur\": \"COLI123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeSuiviTransporteur").value("COLI123"));
    }

    // ------------------------------------------------------------------ utilitaires

    private void ajouterAuPanier(String user, Long idProduit, Long idVariante, int qte) throws Exception {
        mvc.perform(post("/api/v1/panier/lignes").with(acheteur(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idProduit\": %d, \"idVariante\": %d, \"quantite\": %d}"
                                .formatted(idProduit, idVariante, qte)))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor acheteur(String sub) {
        return jwt().jwt(j -> j.subject(sub)).authorities(() -> "ROLE_BUYER");
    }

    private static RequestPostProcessor vendeur() {
        return jwt().jwt(j -> j.subject("vendeur")).authorities(() -> "ROLE_SELLER");
    }

    private int annuler(org.springframework.test.web.servlet.RequestBuilder requete) {
        try {
            return mvc.perform(requete).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ProduitCatalogue produit(Long id, boolean flocable) {
        return produit(id, flocable, "50.00", "ACTIF");
    }

    private static ProduitCatalogue produit(Long id, boolean flocable, String prixHt, String statut) {
        return new ProduitCatalogue(id, "Maillot " + id, new BigDecimal(prixHt), null, new BigDecimal("20.00"),
                flocable, statut,
                List.of(new ProduitCatalogue.Variante(id * 10 + 1, "M"), new ProduitCatalogue.Variante(id * 10 + 2, "L")));
    }
}
