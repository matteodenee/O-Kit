package com.okit.catalogue.mappers;

import com.okit.catalogue.dto.EquipeDto;
import com.okit.catalogue.dto.MarqueDto;
import com.okit.catalogue.dto.ProduitDetailDto;
import com.okit.catalogue.dto.ProduitInputDto;
import com.okit.catalogue.dto.ProduitSummaryDto;
import com.okit.catalogue.dto.PromotionDto;
import com.okit.catalogue.dto.SaisonDto;
import com.okit.catalogue.entities.Equipe;
import com.okit.catalogue.entities.Marque;
import com.okit.catalogue.entities.Produit;
import com.okit.catalogue.entities.ProduitStatut;
import com.okit.catalogue.entities.Promotion;
import com.okit.catalogue.entities.Saison;
import com.okit.catalogue.entities.TauxTva;
import com.okit.catalogue.services.pricing.PriceCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class ProduitMapper {

    private final PriceCalculator priceCalculator;

    public ProduitMapper(PriceCalculator priceCalculator) {
        this.priceCalculator = priceCalculator;
    }

    public ProduitSummaryDto toSummaryDto(Produit produit) {
        if (produit == null) {
            return null;
        }

        BigDecimal prixHT = produit.getPrixDeBase() != null
                ? produit.getPrixDeBase().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tauxTva = (produit.getTauxTva() != null && produit.getTauxTva().getTaux() != null)
                ? produit.getTauxTva().getTaux()
                : BigDecimal.ZERO;

        BigDecimal prixTTC = produit.getPrixDeBase() != null
                ? priceCalculator.calculatePriceTTC(produit.getPrixDeBase(), tauxTva)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        return new ProduitSummaryDto(
                produit.getIdProduit(),
                produit.getTitre(),
                prixHT,
                prixTTC,
                produit.isEnStock(),
                produit.getMarque() != null ? produit.getMarque().getNomMarque() : null,
                produit.getSaison() != null ? produit.getSaison().getNom() : null,
                produit.getEquipe() != null ? produit.getEquipe().getNom() : null,
                produit.getStatut()
        );
    }

    public ProduitDetailDto toDetailDto(Produit produit) {
        if (produit == null) {
            return null;
        }

        BigDecimal prixDeBaseHT = produit.getPrixDeBase() != null
                ? produit.getPrixDeBase().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tauxTva = (produit.getTauxTva() != null && produit.getTauxTva().getTaux() != null)
                ? produit.getTauxTva().getTaux()
                : BigDecimal.ZERO;

        BigDecimal prixTTC = produit.getPrixDeBase() != null
                ? priceCalculator.calculatePriceTTC(produit.getPrixDeBase(), tauxTva)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        LocalDate now = LocalDate.now();
        BigDecimal prixApresPromotionTTC = priceCalculator.calculateDiscountedPrice(prixTTC, produit.getPromotions(), now);

        Optional<Promotion> activePromoOpt = priceCalculator.findActivePromotion(produit.getPromotions(), now);
        PromotionDto promotionDto = activePromoOpt.map(p -> new PromotionDto(
                p.getCode(),
                p.getPourcentageReduction(),
                p.getDateDebut(),
                p.getDateFin(),
                true
        )).orElse(null);

        MarqueDto marqueDto = produit.getMarque() != null
                ? new MarqueDto(produit.getMarque().getIdMarque(), produit.getMarque().getNomMarque(), produit.getMarque().getLogoUrl())
                : null;

        SaisonDto saisonDto = produit.getSaison() != null
                ? new SaisonDto(produit.getSaison().getIdSaison(), produit.getSaison().getNom())
                : null;

        EquipeDto equipeDto = produit.getEquipe() != null
                ? new EquipeDto(produit.getEquipe().getIdEquipe(), produit.getEquipe().getNom(), produit.getEquipe().getLogoUrl(), produit.getEquipe().getType())
                : null;

        return new ProduitDetailDto(
                produit.getIdProduit(),
                produit.getTitre(),
                produit.getDescription(),
                prixDeBaseHT,
                prixTTC,
                prixApresPromotionTTC,
                produit.getPoids(),
                produit.getCouleursPrincipales(),
                produit.getImpactEnvi(),
                produit.getEstFlocable(),
                produit.isEnStock(),
                produit.getStatut(),
                marqueDto,
                saisonDto,
                equipeDto,
                tauxTva,
                promotionDto
        );
    }

    public Produit toEntity(ProduitInputDto dto, Saison saison, Marque marque, TauxTva tva, Equipe equipe) {
        if (dto == null) {
            return null;
        }

        Produit produit = new Produit();
        produit.setTitre(dto.titre());
        produit.setDescription(dto.description());
        produit.setPrixDeBase(dto.prixDeBase());
        produit.setPoids(dto.poids());
        produit.setCouleursPrincipales(dto.couleursPrincipales());
        produit.setImpactEnvi(dto.impactEnvi());
        produit.setEstFlocable(dto.estFlocable() != null ? dto.estFlocable() : true);
        produit.setStatut(dto.statut() != null ? dto.statut() : ProduitStatut.ACTIF);
        produit.setEnStock(true);
        produit.setSaison(saison);
        produit.setMarque(marque);
        produit.setTauxTva(tva);
        produit.setEquipe(equipe);

        return produit;
    }
}
