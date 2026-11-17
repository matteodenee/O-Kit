package com.okit.stock.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderStockRequest(
        @NotBlank @Size(max = 80) String orderId,
        @NotEmpty List<@Valid OrderLine> lines) {}