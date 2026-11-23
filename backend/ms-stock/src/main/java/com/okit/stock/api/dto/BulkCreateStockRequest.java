package com.okit.stock.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkCreateStockRequest(
        @NotEmpty @Size(max = 100) List<@NotNull Long> variantIds,
        @Min(0) int quantity,
        @Min(0) Integer threshold) {}