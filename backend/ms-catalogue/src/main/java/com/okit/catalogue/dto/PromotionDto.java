package com.okit.catalogue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromotionDto(
        String code,
        BigDecimal pourcentageReduction,
        LocalDate dateDebut,
        LocalDate dateFin,
        boolean actif
) {
}
