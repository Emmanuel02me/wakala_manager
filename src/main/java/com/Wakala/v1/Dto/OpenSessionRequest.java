package com.Wakala.v1.Dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record OpenSessionRequest(
                @NotNull @DecimalMin("0.0") BigDecimal openingCash,
                @NotEmpty List<FloatOpeningRequest> floatOpenings,
                @Size(max = 500) String adjustmentNote, // Employee anaweza kutoa sababu
                String notes) {
}