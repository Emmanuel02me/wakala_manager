package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String providerName,
        String destinationProviderName,   // null kwa types zingine
        String transactionType,
        String revenueModel,
        BigDecimal amount,
        BigDecimal networkCommission,
        BigDecimal ownerCommission,
        BigDecimal floatEffect,
        BigDecimal cashEffect,
        String customerPhone,
        String customerName,
        LocalDateTime transactionTime,
        boolean locked
) {}