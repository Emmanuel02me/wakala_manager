package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.util.List;

public record ShortageAnalysis(
        Long sessionId,
        String overallStatus,
        BigDecimal totalShortage,
        BigDecimal totalOverage,
        List<ProviderAnalysis> providers,
        List<String> generalSuggestions
) {
    public record ProviderAnalysis(
            Long providerId,
            String providerName,
            String status,
            BigDecimal openingFloat,
            BigDecimal expectedClosing,
            BigDecimal actualClosing,
            BigDecimal difference,
            BigDecimal differencePercent,
            int transactionCount,
            String severity,               // LOW, MEDIUM, HIGH, CRITICAL
            List<String> suggestions
    ) {}
}