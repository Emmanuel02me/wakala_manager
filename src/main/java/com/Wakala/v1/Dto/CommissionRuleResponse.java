package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CommissionRuleResponse(
    Long id,
    Long providerId,
    String providerName,
    String transactionType,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    BigDecimal networkCommission,
    BigDecimal ownerCommission,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    boolean active,
    boolean current,           // inatumika leo?
    String createdBy,
    LocalDateTime createdAt,
    String notes
) {}