package com.okit.catalogue.dto;

import jakarta.validation.constraints.NotNull;

public record StockStatusUpdateDto(
        @NotNull(message = "Le statut du stock est obligatoire")
        Boolean enStock
) {
}
