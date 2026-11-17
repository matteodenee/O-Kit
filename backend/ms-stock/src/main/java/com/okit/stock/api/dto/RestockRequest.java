package com.okit.stock.api.dto;

import jakarta.validation.constraints.Min;

public record RestockRequest(@Min(value = 1, message = "La quantité doit être > 0") int quantity) {}