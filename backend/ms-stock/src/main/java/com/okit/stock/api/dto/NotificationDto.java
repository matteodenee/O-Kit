package com.okit.stock.api.dto;

import java.time.Instant;

public record NotificationDto(Long id, Long variantId, String type, String message,
                              int stock, int threshold, boolean read, Instant createdAt) {}