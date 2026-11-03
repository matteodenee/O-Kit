package com.okit.catalogue.dto;

import com.okit.catalogue.entities.ProduitStatut;

import java.math.BigDecimal;

public record ProduitDetailDto(
        Long id,
        String titre,
        String description,
        BigDecimal prixDeBaseHT,
        BigDecimal prixTTC,
        BigDecimal prixApresPromotionTTC,
        Double poids,
        String couleursPrincipales,
        String impactEnvi,
        Boolean estFlocable,
        boolean enStock,
        ProduitStatut statut,
        MarqueDto marque,
        SaisonDto saison,
        EquipeDto equipe,
        BigDecimal tauxTva,
        PromotionDto promotionActive
) {
}
