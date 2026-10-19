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
                BigDecimal totalInstantProfit,
                BigDecimal totalExpenses,
                BigDecimal netProfit,

                // ── Volume ──
                int totalTransactionCount,
                BigDecimal totalMonthlyVolume,

                // ── Per Provider ──
                List<ProviderReconciliation> providers,

                // ── Overall (Cash + Floats) ──
                String overallStatus,

                // ── TOTAL BUSINESS VALUE (Cash + Floats zote) ──
                BigDecimal openingTotalValue, // Cash + Floats wakati wa kufungua
                BigDecimal expectedTotalValue, // Opening + Profit - Expenses
                BigDecimal actualTotalValue, // Cash + Floats wakati wa kufunga (null kama haijafungwa)
                BigDecimal totalValueDifference, // actual - expected
                String totalValueStatus // PENDING | BALANCED | SHORTAGE | OVERAGE
) {
}