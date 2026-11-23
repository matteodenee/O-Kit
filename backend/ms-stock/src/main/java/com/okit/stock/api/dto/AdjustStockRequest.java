package com.okit.stock.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdjustStockRequest(
        @Min(value = 0, message = "Le stock ne peut pas être négatif")
        @Max(1_000_000) int targetQuantity) {}