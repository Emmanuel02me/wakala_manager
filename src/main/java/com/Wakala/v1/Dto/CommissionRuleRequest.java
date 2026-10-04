package com.Wakala.v1.Dto;

import com.Wakala.v1.Entity.Transaction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CommissionRuleRequest(
    @NotNull Long providerId,
    @NotNull Transaction.TransactionType transactionType,
    @NotNull @DecimalMin("0.0") BigDecimal minAmount,
    @DecimalMin("0.0") BigDecimal maxAmount,   // optional (null = infinity)
    @NotNull @DecimalMin("0.0") BigDecimal networkCommission,
    @NotNull @DecimalMin("0.0") BigDecimal ownerCommission,
    LocalDate effectiveFrom,   // optional (default = today)
    String notes
) {}