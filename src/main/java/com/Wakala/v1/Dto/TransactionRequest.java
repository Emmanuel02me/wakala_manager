package com.Wakala.v1.Dto;

import com.Wakala.v1.Entity.Transaction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransactionRequest(
                @NotNull Long sessionId,
                @NotNull Long providerId,

                // Kwa transfers pekee (null kwa types zingine)
                Long destinationProviderId,

                @NotNull Transaction.TransactionType transactionType,

                @NotNull @DecimalMin("0.01") BigDecimal amount,

                /**
                 * Inaonyesha kama network commission inapaswa kukatwa na mfumo.
                 *
                 * - true = Hali A: amount ni kile mteja ana kwenye simu.
                 * Mfumo unakata network commission.
                 * - false = Hali B: network ilishakatwa tayari.
                 * amount ni kile kilichofika kwenye float.
                 * - null = Haihusiki (transfers, TILL_SEND, n.k.)
                 */
                Boolean hasNetworkFee,

                /**
                 * Kwa BANK_CONTROL_NUMBER pekee:
                 * - true = Wakala anaongeza commission (mfano, 500 kwa Mijini)
                 * - false = Wakala haongezi (mteja analipa exact)
                 */
                Boolean chargeOwnerCommission,

                String customerPhone,
                String customerName,
                String reference) {
}