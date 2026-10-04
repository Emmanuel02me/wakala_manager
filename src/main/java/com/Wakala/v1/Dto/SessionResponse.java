package com.Wakala.v1.Dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SessionResponse(
                Long id,
                LocalDate sessionDate,
                String status,
                BigDecimal openingCash,
                BigDecimal closingCash,
                LocalDateTime openedAt,
                LocalDateTime closedAt,
                String openedBy,
                String closedBy,
                boolean hasOpeningAdjustment,
                String openingAdjustmentNote,
                BigDecimal adjustmentTotal,
                int transactionCount) {
}
