package com.Wakala.v1.Dto;

import jakarta.validation.constraints.Size;

/**
 * Update ya fields SALAMA pekee.
 * Hatuwezi kubadilisha amount, commission, effects.
 */
public record UpdateTransactionRequest(
                @Size(max = 100) String customerName,
                @Size(max = 15) String customerPhone,
                @Size(max = 50) String reference,
                @Size(max = 255) String reason // kwa audit
) {
}