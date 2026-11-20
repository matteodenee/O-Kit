package com.okit.catalogue.dto;

import com.okit.catalogue.entities.ProduitStatut;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProduitUpdateInputDto(
        String titre,
        String description,
        @DecimalMin(value = "0.0", inclusive = false, message = "Le prix de base doit être strictement supérieur à 0")
        BigDecimal prixDeBase,
        @Positive(message = "Le poids doit être strictement supérieur à 0")
        Double poids,
        String couleursPrincipales,
        String impactEnvi,
        Boolean estFlocable,
        ProduitStatut statut
) {
}
