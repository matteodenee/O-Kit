package com.okit.stock.api.dto;

import jakarta.validation.constraints.Min;

public record ThresholdRequest(@Min(0) int threshold) {}