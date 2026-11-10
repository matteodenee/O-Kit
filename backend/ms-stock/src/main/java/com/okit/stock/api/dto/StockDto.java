package com.okit.stock.api.dto;

public record StockDto(Long variantId, int stock, int threshold, StockStatus status) {}