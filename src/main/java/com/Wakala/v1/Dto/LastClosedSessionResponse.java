package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LastClosedSessionResponse(
        boolean exists,
        Long sessionId,
        LocalDate sessionDate,
        BigDecimal closingCash,
        List<FloatClosingInfo> floatClosings,

        // ── TOTAL BUSINESS VALUE ──
        BigDecimal closingTotalValue,        // Cash + closing floats
        BigDecimal cashDifference,           // cash actual - cash expected
        BigDecimal totalFloatDifference,     // jumla ya float tofauti zote
        String overallStatus,                // BALANCED | SHORTAGE | OVERAGE | PENDING
        boolean hadIssues                    // true kama overallStatus != BALANCED
) {
    public record FloatClosingInfo(
            Long providerId,
            String providerName,
            BigDecimal closingBalance,
            BigDecimal floatDifference      // actual - expected kwa provider
    ) {}
}