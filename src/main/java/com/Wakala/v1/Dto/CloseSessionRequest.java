package com.Wakala.v1.Dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record CloseSessionRequest(
                @NotNull @DecimalMin("0.0") BigDecimal closingCash,
                @NotEmpty List<FloatClosingRequest> floatClosings,
                String notes) {
}