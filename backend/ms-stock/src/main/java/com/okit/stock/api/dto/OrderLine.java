package com.okit.stock.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderLine(@NotNull Long variantId, @Min(1) int quantity) {}