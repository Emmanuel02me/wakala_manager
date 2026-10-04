package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LastClosedSessionResponse(
        boolean exists,
        Long sessionId,
        LocalDate sessionDate,
        BigDecimal closingCash,
        List<FloatClosingInfo> floatClosings
) {
    public record FloatClosingInfo(
            Long providerId,
            String providerName,
            BigDecimal closingBalance
    ) {}
}