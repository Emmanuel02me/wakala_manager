package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NetworkRateResponse(
        Long id,
        Long providerId,
        String providerName,
        String transactionType,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal networkCommission,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        boolean current,
        String notes) {
}