package com.okit.chatbot.tools;

import com.okit.chatbot.client.OkitApiClient;
import com.okit.chatbot.client.OkitApiClient.ProduitDetail;
import com.okit.chatbot.client.OkitApiClient.ProduitResume;
import com.okit.chatbot.client.OkitApiClient.StatutStock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OkitToolsTest {

    private final OkitApiClient api = mock(OkitApiClient.class);
    private final OkitTools outils = new OkitTools(api, "jwt-acheteur");

    @BeforeEach
    void catalogue() {
        when(api.produitsActifs()).thenReturn(List.of(
                resume(1L, "Maillot domicile 2026", "90", true, "Nike", "PSG"),
                resume(2L, "Maillot extérieur 2026", "85", false, "Nike", "PSG"),
                resume(3L, "Maillot domicile 2026", "80", true, "Puma", "OM"),
                resume(4L, "Short d'entraînement", "35", true, "Nike", "Lyon")));
    }

    @Test
    void rechercheParMotsClesSansAccentNiCasse() {
        assertThat(outils.rechercherProduits("MAILLOT exterieur psg", null))
                .extracting(OkitTools.ResultatProduit::id)
                .first().isEqualTo(2L);
    }

    @Test
    void rechercheFiltreParPrixMax() {
        assertThat(outils.rechercherProduits("maillot", new BigDecimal("85")))
                .extracting(OkitTools.ResultatProduit::id)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void rechercheVideListeLesProduitsEnStockDabord() {
        assertThat(outils.rechercherProduits(null, null))
                .extracting(OkitTools.ResultatProduit::id)
                .last().isEqualTo(2L);
    }

    @Test
    void similairesMemeEquipePuisMemeMarque() {
        when(api.produit(1L)).thenReturn(detail(1L, "PSG", "Nike"));
        assertThat(outils.produitsSimilaires(1L))
                .extracting(OkitTools.ResultatProduit::id)
                .containsExactly(2L, 4L);
    }

    @Test
    void ficheProduitAvecDisponibiliteParTaille() {
        when(api.produit(1L)).thenReturn(detail(1L, "PSG", "Nike"));
        when(api.stocks(List.of(11L, 12L), "jwt-acheteur")).thenReturn(List.of(
                new StatutStock(11L, "IN_STOCK"), new StatutStock(12L, "OUT_OF_STOCK")));

        OkitTools.FicheProduit fiche = outils.detailProduit(1L);

        assertThat(fiche.tailles()).containsExactly(
                new OkitTools.Taille("M", "en stock"), new OkitTools.Taille("L", "épuisé"));
        assertThat(fiche.equipe()).isEqualTo("PSG");
    }

    @Test
    void produitDesactiveIntrouvable() {
        ProduitDetail desactive = new ProduitDetail(9L, "Ancien", null, null, null, null, null, false, false,
                "DESACTIVE", null, null, null, List.of());
        when(api.produit(9L)).thenReturn(desactive);
        assertThatThrownBy(() -> outils.detailProduit(9L)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ProduitResume resume(long id, String titre, String prix, boolean enStock, String marque, String equipe) {
        return new ProduitResume(id, titre, new BigDecimal(prix), enStock, marque, "2026", equipe);
    }

    private static ProduitDetail detail(long id, String equipe, String marque) {
        return new ProduitDetail(id, "Maillot", "desc", new BigDecimal("90"), null, "bleu", null, true, true,
                "ACTIF", new ProduitDetail.Marque(marque), new ProduitDetail.Nom("2026"), new ProduitDetail.Nom(equipe),
                List.of(new ProduitDetail.Variante(11L, "M"), new ProduitDetail.Variante(12L, "L")));
    }
}
