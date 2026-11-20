package com.okit.catalogue.services.pricing;

import com.okit.catalogue.entities.Promotion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class PriceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * Calcule le prix TTC à partir du prix HT et du taux de TVA (en pourcentage, ex: 20.00 pour 20%).
     * Le résultat est arrondi à 2 décimales avec RoundingMode.HALF_UP.
     *
     * @param prixHT  le prix hors taxes
     * @param tauxTva le taux de TVA en pourcentage
     * @return le prix TTC arrondi
     */
    public BigDecimal calculatePriceTTC(BigDecimal prixHT, BigDecimal tauxTva) {
        if (prixHT == null) {
            throw new IllegalArgumentException("Le prix HT ne peut pas être nul");
        }
        if (tauxTva == null) {
            throw new IllegalArgumentException("Le taux de TVA ne peut pas être nul");
        }

        BigDecimal multiplicateur = BigDecimal.ONE.add(tauxTva.divide(HUNDRED, 4, RoundingMode.HALF_UP));
        return prixHT.multiply(multiplicateur).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Recherche la promotion active pour une date de référence donnée.
     * Une promotion est active si dateDebut <= dateReference <= dateFin.
     * Si plusieurs promotions sont actives, retient la meilleure réduction (pourcentage le plus élevé).
     *
     * @param promotions    la liste des promotions associées au produit
     * @param dateReference la date de référence (ex: aujourd'hui)
     * @return la promotion applicable, ou Optional.empty() si aucune
     */
    public Optional<Promotion> findActivePromotion(List<Promotion> promotions, LocalDate dateReference) {
        if (promotions == null || promotions.isEmpty() || dateReference == null) {
            return Optional.empty();
        }

        return promotions.stream()
                .filter(p -> p.getDateDebut() != null && p.getDateFin() != null)
                .filter(p -> !dateReference.isBefore(p.getDateDebut()) && !dateReference.isAfter(p.getDateFin()))
                .filter(p -> p.getPourcentageReduction() != null && p.getPourcentageReduction().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(Promotion::getPourcentageReduction));
    }

    /**
     * Calcule le prix remisé (TTC ou HT) à partir d'un prix de base et d'une liste de promotions.
     * Si une promotion est active, applique le pourcentage de réduction et arrondit à 2 décimales (HALF_UP).
     * Si aucune promotion n'est active, renvoie le prix de base arrondi à 2 décimales.
     *
     * @param prixDeBase    le prix de base (HT ou TTC)
     * @param promotions    la liste des promotions
     * @param dateReference la date de référence
     * @return le prix après réduction, arrondi à 2 décimales
     */
    public BigDecimal calculateDiscountedPrice(BigDecimal prixDeBase, List<Promotion> promotions, LocalDate dateReference) {
        if (prixDeBase == null) {
            throw new IllegalArgumentException("Le prix de base ne peut pas être nul");
        }

        Optional<Promotion> promoOpt = findActivePromotion(promotions, dateReference);
        if (promoOpt.isEmpty()) {
            return prixDeBase.setScale(2, RoundingMode.HALF_UP);
        }

        Promotion promo = promoOpt.get();
        BigDecimal pourcentage = promo.getPourcentageReduction();
        BigDecimal reduction = prixDeBase.multiply(pourcentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal prixRemise = prixDeBase.subtract(reduction).setScale(2, RoundingMode.HALF_UP);
        return prixRemise.max(BigDecimal.ZERO);
    }
}
