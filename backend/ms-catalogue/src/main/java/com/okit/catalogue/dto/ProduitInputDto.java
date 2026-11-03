package com.okit.catalogue.dto;

import com.okit.catalogue.entities.ProduitStatut;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProduitInputDto(
        @NotBlank(message = "Le titre du produit est obligatoire")
        String titre,

        String description,

        @NotNull(message = "Le prix de base est obligatoire")
        @DecimalMin(value = "0.0", inclusive = false, message = "Le prix de base doit être strictement supérieur à 0")
        BigDecimal prixDeBase,

        @Positive(message = "Le poids doit être strictement supérieur à 0")
        Double poids,

        String couleursPrincipales,

        String impactEnvi,

        Boolean estFlocable,

        ProduitStatut statut,

        @NotNull(message = "L'identifiant de la saison est obligatoire")
        Long idSaison,

        @NotNull(message = "L'identifiant de la marque est obligatoire")
        Long idMarque,

        @NotNull(message = "L'identifiant du taux de TVA est obligatoire")
        Long idTauxTVA,

        Long idEquipe
) {
}
