package com.okit.stock.api.dto;

import java.time.Instant;

public record MovementDto(Long id, Long variantId, int change, int stockAfter,
                          String reason, String reference, Instant createdAt) {}