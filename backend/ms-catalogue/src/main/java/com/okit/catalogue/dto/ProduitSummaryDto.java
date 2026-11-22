package com.okit.catalogue.dto;

import com.okit.catalogue.entities.ProduitStatut;

import java.math.BigDecimal;
import java.util.List;

public record ProduitSummaryDto(
        Long id,
        String titre,
        BigDecimal prixHT,
        BigDecimal prixTTC,
        boolean enStock,
        String nomMarque,
        String nomSaison,
        String nomEquipe,
        ProduitStatut statut,
        List<Long> varianteIds
) {
}
