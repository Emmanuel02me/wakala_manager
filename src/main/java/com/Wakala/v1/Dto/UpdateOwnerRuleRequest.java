package com.Wakala.v1.Dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateOwnerRuleRequest(
        @NotNull Long existingRuleId,
        @NotNull @DecimalMin("0.0") BigDecimal ownerCommission,
        @NotNull LocalDate effectiveFrom,
        String notes
) {}