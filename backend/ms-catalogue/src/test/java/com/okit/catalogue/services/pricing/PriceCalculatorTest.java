package com.okit.catalogue.services.pricing;

import com.okit.catalogue.entities.Promotion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceCalculatorTest {

    private PriceCalculator priceCalculator;

    @BeforeEach
    void setUp() {
        priceCalculator = new PriceCalculator();
    }

    @Test
    @DisplayName("Calcul exact du prix TTC (ex: 80€ HT avec 20% TVA -> 96.00€ TTC)")
    void shouldCalculatePriceTTCCorrectly() {
        BigDecimal prixHT = new BigDecimal("80.00");
        BigDecimal tauxTva = new BigDecimal("20.00");

        BigDecimal prixTTC = priceCalculator.calculatePriceTTC(prixHT, tauxTva);

        assertThat(prixTTC).isEqualByComparingTo(new BigDecimal("96.00"));
    }

    @Test
    @DisplayName("Calcul TTC avec taux réduit et arrondi HALF_UP")
    void shouldCalculatePriceTTCWithReducedRateAndRounding() {
        BigDecimal prixHT = new BigDecimal("49.90");
        BigDecimal tauxTva = new BigDecimal("5.50");

        // 49.90 * 1.055 = 52.6445 -> arrondi HALF_UP = 52.64
        BigDecimal prixTTC = priceCalculator.calculatePriceTTC(prixHT, tauxTva);

        assertThat(prixTTC).isEqualByComparingTo(new BigDecimal("52.64"));
    }

    @Test
    @DisplayName("Lève IllegalArgumentException si prix HT ou taux TVA est null")
    void shouldThrowExceptionWhenInputsAreNull() {
        assertThatThrownBy(() -> priceCalculator.calculatePriceTTC(null, new BigDecimal("20.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le prix HT ne peut pas être nul");

        assertThatThrownBy(() -> priceCalculator.calculatePriceTTC(new BigDecimal("80.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le taux de TVA ne peut pas être nul");
    }

    @Test
    @DisplayName("Application d'une remise promotionnelle valide")
    void shouldApplyValidPromotionDiscount() {
        LocalDate today = LocalDate.of(2026, 10, 11);
        Promotion promo = new Promotion();
        promo.setCode("PROMO20");
        promo.setPourcentageReduction(new BigDecimal("20.00"));
        promo.setDateDebut(today.minusDays(5));
        promo.setDateFin(today.plusDays(5));

        BigDecimal prixDeBase = new BigDecimal("100.00");

        BigDecimal prixRemise = priceCalculator.calculateDiscountedPrice(prixDeBase, List.of(promo), today);

        assertThat(prixRemise).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("Retient la meilleure promotion lorsque plusieurs sont actives simultanément")
    void shouldSelectBestPromotionWhenMultipleAreActive() {
        LocalDate today = LocalDate.of(2026, 10, 11);

        Promotion promo10 = new Promotion();
        promo10.setCode("PROMO10");
        promo10.setPourcentageReduction(new BigDecimal("10.00"));
        promo10.setDateDebut(today.minusDays(2));
        promo10.setDateFin(today.plusDays(2));

        Promotion promo30 = new Promotion();
        promo30.setCode("PROMO30");
        promo30.setPourcentageReduction(new BigDecimal("30.00"));
        promo30.setDateDebut(today.minusDays(1));
        promo30.setDateFin(today.plusDays(3));

        BigDecimal prixDeBase = new BigDecimal("100.00");

        BigDecimal prixRemise = priceCalculator.calculateDiscountedPrice(prixDeBase, List.of(promo10, promo30), today);

        assertThat(prixRemise).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    @DisplayName("Rejet d'une promotion expirée")
    void shouldRejectExpiredPromotion() {
        LocalDate today = LocalDate.of(2026, 10, 11);

        Promotion promoExpiree = new Promotion();
        promoExpiree.setCode("EXPIRED");
        promoExpiree.setPourcentageReduction(new BigDecimal("50.00"));
        promoExpiree.setDateDebut(today.minusDays(10));
        promoExpiree.setDateFin(today.minusDays(1));

        BigDecimal prixDeBase = new BigDecimal("80.00");

        BigDecimal prixResultat = priceCalculator.calculateDiscountedPrice(prixDeBase, List.of(promoExpiree), today);

        assertThat(prixResultat).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(priceCalculator.findActivePromotion(List.of(promoExpiree), today)).isEmpty();
    }

    @Test
    @DisplayName("Rejet d'une promotion future")
    void shouldRejectFuturePromotion() {
        LocalDate today = LocalDate.of(2026, 10, 11);

        Promotion promoFuture = new Promotion();
        promoFuture.setCode("FUTURE");
        promoFuture.setPourcentageReduction(new BigDecimal("40.00"));
        promoFuture.setDateDebut(today.plusDays(2));
        promoFuture.setDateFin(today.plusDays(10));

        BigDecimal prixDeBase = new BigDecimal("80.00");

        BigDecimal prixResultat = priceCalculator.calculateDiscountedPrice(prixDeBase, List.of(promoFuture), today);

        assertThat(prixResultat).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(priceCalculator.findActivePromotion(List.of(promoFuture), today)).isEmpty();
    }

    @Test
    @DisplayName("Vérifie les bornes exactes de validité (dateDebut et dateFin incluses)")
    void shouldAcceptPromotionsOnBoundaryDates() {
        LocalDate today = LocalDate.of(2026, 10, 11);

        Promotion promoDebut = new Promotion();
        promoDebut.setCode("START_DAY");
        promoDebut.setPourcentageReduction(new BigDecimal("15.00"));
        promoDebut.setDateDebut(today);
        promoDebut.setDateFin(today.plusDays(5));

        Promotion promoFin = new Promotion();
        promoFin.setCode("END_DAY");
        promoFin.setPourcentageReduction(new BigDecimal("25.00"));
        promoFin.setDateDebut(today.minusDays(5));
        promoFin.setDateFin(today);

        assertThat(priceCalculator.findActivePromotion(List.of(promoDebut), today)).isPresent();
        assertThat(priceCalculator.findActivePromotion(List.of(promoFin), today)).isPresent();
    }

    @Test
    @DisplayName("findActivePromotion renvoie vide si liste vide ou null ou date null")
    void shouldReturnEmptyWhenNoPromotionsAvailable() {
        LocalDate today = LocalDate.of(2026, 10, 11);

        assertThat(priceCalculator.findActivePromotion(null, today)).isEmpty();
        assertThat(priceCalculator.findActivePromotion(Collections.emptyList(), today)).isEmpty();
        assertThat(priceCalculator.findActivePromotion(List.of(new Promotion()), null)).isEmpty();
    }

    @Test
    @DisplayName("Lève une exception si prixDeBase est nul dans calculateDiscountedPrice")
    void shouldThrowExceptionWhenBasePriceIsNull() {
        LocalDate today = LocalDate.of(2026, 10, 11);

        assertThatThrownBy(() -> priceCalculator.calculateDiscountedPrice(null, Collections.emptyList(), today))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le prix de base ne peut pas être nul");
    }
}
