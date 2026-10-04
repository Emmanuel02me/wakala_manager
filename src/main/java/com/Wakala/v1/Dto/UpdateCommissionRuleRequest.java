package com.Wakala.v1.Dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request ya kubadilisha rates.
 * Hii inafunga rule ya zamani (effectiveTo) na kuunda mpya.
 */
public record UpdateCommissionRuleRequest(
        @NotNull Long existingRuleId,
        @NotNull @DecimalMin("0.0") BigDecimal networkCommission,
        @NotNull @DecimalMin("0.0") BigDecimal ownerCommission,
        @NotNull LocalDate effectiveFrom, // tarehe mpya inaanza
        String notes) {
}