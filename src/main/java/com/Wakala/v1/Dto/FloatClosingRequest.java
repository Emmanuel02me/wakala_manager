package com.Wakala.v1.Dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record FloatClosingRequest(
    @NotNull Long providerId,
    @NotNull @DecimalMin("0.0") BigDecimal closingBalance
) {}
