package com.okit.stock.api.dto;

public record StockCheckDto(Long variantId, boolean available, StockStatus status) {}