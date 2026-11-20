package com.okit.stock.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateStockRequest(
        @NotNull Long variantId,
        @Min(0) int quantity,
        @Min(0) Integer threshold) {}