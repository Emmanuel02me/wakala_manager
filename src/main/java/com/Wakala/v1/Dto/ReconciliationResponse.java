package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReconciliationResponse(
        Long sessionId,
        LocalDate sessionDate,
        String sessionStatus,

        // ── Cash ──
        BigDecimal openingCash,
        BigDecimal cashEffectTotal,
        BigDecimal expectedCash,
        BigDecimal actualCash,
        BigDecimal cashDifference,

        // ── Faida ──
        BigDecimal totalInstantProfit,       // jumla ya faida kutoka LIPA
        BigDecimal totalExpenses,
        BigDecimal netProfit,                 // instantProfit − expenses

        // ── Volume ──
        int totalTransactionCount,
        BigDecimal totalMonthlyVolume,        // Till + Bank volume

        // ── Per Provider ──
        List<ProviderReconciliation> providers,

        // ── Overall ──
        String overallStatus                  // PENDING, BALANCED, SHORTAGE
) {}