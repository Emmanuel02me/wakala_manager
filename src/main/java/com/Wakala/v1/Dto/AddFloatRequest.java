package com.Wakala.v1.Dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddFloatRequest(
        @NotNull(message = "Provider inahitajika")
        Long providerId,

        @NotNull(message = "Kiasi kinahitajika")
        @DecimalMin(value = "0.01", message = "Kiasi kiwe zaidi ya 0")
        BigDecimal openingBalance,

        @NotNull(message = "Chanzo cha pesa kinahitajika")
        FloatSourceType sourceType,

        Long sourceProviderId,  // required only kama sourceType = FROM_PROVIDER

        @NotBlank(message = "Sababu inahitajika")
        @Size(max = 500, message = "Sababu isizidi herufi 500")
        String note
) {
    public enum FloatSourceType {
        /** Nilikuwa nayo tangu mwanzo — opening correction. Total inaongezeka. */
        ALREADY_HAD,
        /** Nimetumia cash kuweka — cash inapungua, float inaongezeka. Total haibadiliki. */
        FROM_CASH,
        /** Nimetumia provider mwingine — source inapungua, destination inaongezeka. Total haibadiliki. */
        FROM_PROVIDER
    }
}