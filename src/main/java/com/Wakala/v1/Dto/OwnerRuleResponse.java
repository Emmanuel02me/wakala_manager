package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OwnerRuleResponse(
        Long id,
        Long providerId,
        String providerName,
        String transactionType,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal ownerCommission,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        boolean current,
        String createdBy,
        LocalDateTime createdAt,
        String notes) {
}