package com.okit.catalogue.mappers;

import com.okit.catalogue.dto.ProduitDetailDto;
import com.okit.catalogue.dto.ProduitInputDto;
import com.okit.catalogue.dto.ProduitSummaryDto;
import com.okit.catalogue.entities.Equipe;
import com.okit.catalogue.entities.EquipeType;
import com.okit.catalogue.entities.Marque;
import com.okit.catalogue.entities.Produit;
import com.okit.catalogue.entities.ProduitStatut;
import com.okit.catalogue.entities.Promotion;
import com.okit.catalogue.entities.Saison;
import com.okit.catalogue.entities.TauxTva;
import com.okit.catalogue.services.pricing.PriceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProduitMapperTest {

    private ProduitMapper produitMapper;

    @BeforeEach
    void setUp() {
        PriceCalculator priceCalculator = new PriceCalculator();
        produitMapper = new ProduitMapper(priceCalculator);
    }

    @Test
    @DisplayName("toSummaryDto : mappe correctement une entité Produit complète")
    void shouldMapToSummaryDto() {
        Produit produit = new Produit();
        produit.setIdProduit(1L);
        produit.setTitre("Maillot Extérieur");
        produit.setPrixDeBase(new BigDecimal("80.00"));
        produit.setEnStock(true);
        produit.setStatut(ProduitStatut.ACTIF);
        produit.setMarque(new Marque("Adidas", "https://example.com/adidas.png"));
        produit.setSaison(new Saison("2024/2025"));
        produit.setTauxTva(new TauxTva(new BigDecimal("20.00")));
        produit.setEquipe(new Equipe("Real Madrid", "https://example.com/rm.png", EquipeType.CLUB));

        ProduitSummaryDto summary = produitMapper.toSummaryDto(produit);

        assertThat(summary).isNotNull();
        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.titre()).isEqualTo("Maillot Extérieur");
        assertThat(summary.prixHT()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(summary.prixTTC()).isEqualByComparingTo(new BigDecimal("96.00"));
        assertThat(summary.enStock()).isTrue();
        assertThat(summary.nomMarque()).isEqualTo("Adidas");
        assertThat(summary.nomSaison()).isEqualTo("2024/2025");
        assertThat(summary.nomEquipe()).isEqualTo("Real Madrid");
        assertThat(summary.statut()).isEqualTo(ProduitStatut.ACTIF);
    }

    @Test
    @DisplayName("toDetailDto : mappe correctement les détails complets avec promotion active")
    void shouldMapToDetailDtoWithActivePromotion() {
        Produit produit = new Produit();
        produit.setIdProduit(5L);
        produit.setTitre("Maillot Collector");
        produit.setDescription("Edition 100 ans");
        produit.setPrixDeBase(new BigDecimal("100.00"));
        produit.setPoids(0.3);
        produit.setCouleursPrincipales("Or, Noir");
        produit.setImpactEnvi("A+");
        produit.setEstFlocable(true);
        produit.setEnStock(true);
        produit.setStatut(ProduitStatut.ACTIF);
        produit.setMarque(new Marque(2L, "Puma", "https://example.com/puma.png"));
        produit.setSaison(new Saison(3L, "2025/2026"));
        produit.setTauxTva(new TauxTva(4L, new BigDecimal("20.00")));
        produit.setEquipe(new Equipe(5L, "Marseille", "https://example.com/om.png", EquipeType.CLUB));

        Promotion promo = new Promotion();
        promo.setCode("GOLD20");
        promo.setPourcentageReduction(new BigDecimal("20.00"));
        promo.setDateDebut(LocalDate.now().minusDays(2));
        promo.setDateFin(LocalDate.now().plusDays(2));
        produit.addPromotion(promo);

        ProduitDetailDto detail = produitMapper.toDetailDto(produit);

        assertThat(detail).isNotNull();
        assertThat(detail.id()).isEqualTo(5L);
        assertThat(detail.titre()).isEqualTo("Maillot Collector");
        assertThat(detail.prixDeBaseHT()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(detail.prixTTC()).isEqualByComparingTo(new BigDecimal("120.00"));
        // 120.00€ TTC avec 20% de remise -> 96.00€ TTC
        assertThat(detail.prixApresPromotionTTC()).isEqualByComparingTo(new BigDecimal("96.00"));
        assertThat(detail.promotionActive()).isNotNull();
        assertThat(detail.promotionActive().code()).isEqualTo("GOLD20");
        assertThat(detail.marque().nomMarque()).isEqualTo("Puma");
        assertThat(detail.saison().nom()).isEqualTo("2025/2026");
        assertThat(detail.equipe().nom()).isEqualTo("Marseille");
    }

    @Test
    @DisplayName("toEntity : crée une entité Produit conforme à partir de ProduitInputDto")
    void shouldCreateEntityFromInputDto() {
        ProduitInputDto dto = new ProduitInputDto(
                "Nouveau Maillot",
                "Description maillot",
                new BigDecimal("75.00"),
                0.2,
                "Vert",
                "A",
                true,
                ProduitStatut.ACTIF,
                1L,
                2L,
                3L,
                4L
        );

        Saison saison = new Saison(1L, "2024/2025");
        Marque marque = new Marque(2L, "Nike", "url");
        TauxTva tva = new TauxTva(3L, new BigDecimal("20.00"));
        Equipe equipe = new Equipe(4L, "ASSE", "url", EquipeType.CLUB);

        Produit produit = produitMapper.toEntity(dto, saison, marque, tva, equipe);

        assertThat(produit).isNotNull();
        assertThat(produit.getTitre()).isEqualTo("Nouveau Maillot");
        assertThat(produit.getDescription()).isEqualTo("Description maillot");
        assertThat(produit.getPrixDeBase()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(produit.getPoids()).isEqualTo(0.2);
        assertThat(produit.getCouleursPrincipales()).isEqualTo("Vert");
        assertThat(produit.getImpactEnvi()).isEqualTo("A");
        assertThat(produit.isEstFlocable()).isTrue();
        assertThat(produit.getStatut()).isEqualTo(ProduitStatut.ACTIF);
        assertThat(produit.isEnStock()).isTrue();
        assertThat(produit.getSaison()).isEqualTo(saison);
        assertThat(produit.getMarque()).isEqualTo(marque);
        assertThat(produit.getTauxTva()).isEqualTo(tva);
        assertThat(produit.getEquipe()).isEqualTo(equipe);
    }
}
