package com.Wakala.v1.Dto;

import java.math.BigDecimal;

public record ProviderReconciliation(
        Long providerId,
        String providerName,
        String providerType,          // MOBILE_MONEY au BANK

        // ── Float ──
        BigDecimal openingFloat,
        BigDecimal floatEffect,
        BigDecimal expectedClosingFloat,
        BigDecimal actualClosingFloat,
        BigDecimal floatDifference,

        // ── Miamala ──
        int transactionCount,
        BigDecimal totalAmount,

        // ── Faida ──
        BigDecimal instantProfit,           // kutoka LIPA_CASH_OUT
        BigDecimal networkCommissionTotal,
        BigDecimal ownerCommissionTotal,

        // ── Monthly (Till + Bank) ──
        int monthlyTransactionCount,
        BigDecimal monthlyVolume,

        // ── Status ──
        String status                 // PENDING, BALANCED, SHORTAGE, OVERAGE
) {}