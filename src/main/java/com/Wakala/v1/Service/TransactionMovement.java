package com.Wakala.v1.Service;

import com.Wakala.v1.Entity.RevenueModel;
import com.Wakala.v1.Entity.Transaction;

import java.math.BigDecimal;

public class TransactionMovement {

        public record Effect(
                        BigDecimal floatEffect,
                        BigDecimal cashEffect,
                        RevenueModel revenueModel) {
        }

        /**
         * Hesabu athari za muamala kwenye float na cash.
         *
         * @param type              Aina ya muamala
         * @param amount            Kiasi cha msingi
         * @param networkCommission Makato ya mtandao (kutoka rule)
         * @param ownerCommission   Faida ya wakala (kutoka rule)
         * @param hasNetworkFee
         *                          - true = Hali A (amount = kile mteja ana kwenye
         *                          simu; mfumo unakata network)
         *                          - false = Hali B (amount = kile kilichofika kwenye
         *                          float; network ilishakatwa)
         */
        public static Effect calculate(
                        Transaction.TransactionType type,
                        BigDecimal amount,
                        BigDecimal networkCommission,
                        BigDecimal ownerCommission,
                        boolean hasNetworkFee,
                        boolean chargeOwnerCommission) {
                return switch (type) {

                        // ══════════════════════════════════════════════
                        // TILL_CASH_OUT (Monthly)
                        // ownerCommission = 0 kila wakati (faida ya monthly)
                        // ══════════════════════════════════════════════
                        case TILL_CASH_OUT -> {
                                BigDecimal floatEffect = hasNetworkFee
                                                ? amount.subtract(networkCommission)
                                                : amount;
                                yield new Effect(floatEffect, floatEffect.negate(), RevenueModel.MONTHLY);
                        }

                        // ══════════════════════════════════════════════
                        // TILL_SEND, TILL_PAY_BILL, TILL_CASH_IN
                        // Exact amount, network = 0
                        // ══════════════════════════════════════════════
                        case TILL_SEND, TILL_PAY_BILL, TILL_CASH_IN ->
                                new Effect(amount.negate(), amount, RevenueModel.MONTHLY);

                        // ══════════════════════════════════════════════
                        // BANK_CASH_OUT (Monthly, exact amount)
                        // Mteja ana 200,000 kwenye bank, anataka 190,000 cash.
                        // Bank inakata 10,000 direct. Wakala anapokea 190,000.
                        // ══════════════════════════════════════════════
                        case BANK_CASH_OUT ->
                                new Effect(amount, amount.negate(), RevenueModel.MONTHLY);

                        // ══════════════════════════════════════════════
                        // BANK_CONTROL_NUMBER (Instant, Kwa hiari)
                        // Mteja analipa bill 10,000.
                        // chargeOwnerCommission = false → mteja analipa 10,000
                        // chargeOwnerCommission = true → mteja analipa 10,500 (owner=500)
                        // floatEffect = −amount (bill inapokea exact amount)
                        // cashEffect = +(amount + ownerCommission) (wakala anapokea kutoka kwa mteja)
                        // ══════════════════════════════════════════════
                        case BANK_CONTROL_NUMBER -> {
                                BigDecimal owner = chargeOwnerCommission ? ownerCommission : BigDecimal.ZERO;
                                BigDecimal cashIn = amount.add(owner);
                                yield new Effect(amount.negate(), cashIn, RevenueModel.INSTANT);
                        }

                        // ══════════════════════════════════════════════
                        // LIPA_CASH_OUT (Instant)
                        // ══════════════════════════════════════════════
                        case LIPA_CASH_OUT -> {
                                BigDecimal floatEffect = hasNetworkFee
                                                ? amount.subtract(networkCommission)
                                                : amount;
                                BigDecimal cashEffect = floatEffect.subtract(ownerCommission).negate();
                                yield new Effect(floatEffect, cashEffect, RevenueModel.INSTANT);
                        }

                        // ══════════════════════════════════════════════
                        // TRANSFERS (No revenue)
                        // ══════════════════════════════════════════════
                        case TRANSFER_PROVIDER, TRANSFER_FLOAT_TO_BANK ->
                                new Effect(amount.negate(), BigDecimal.ZERO, RevenueModel.NONE);

                        case TRANSFER_BANK_TO_FLOAT ->
                                new Effect(amount.negate(), BigDecimal.ZERO, RevenueModel.NONE);

                        // ══════════════════════════════════════════════
                        // MANUAL ADJUSTMENTS (No revenue)
                        // ══════════════════════════════════════════════
                        case FLOAT_TOPUP ->
                                new Effect(amount, amount.negate(), RevenueModel.NONE);

                        case FLOAT_WITHDRAW ->
                                new Effect(amount.negate(), amount, RevenueModel.NONE);

                        case VOID -> 
                                new Effect(BigDecimal.ZERO, BigDecimal.ZERO, RevenueModel.NONE);
                                // Void inarudisha kila kitu kinyume
                                // Effects zinachukuliwa kutoka original (tutahesabu kwenye service)
                                // Tutazijaza kwa mkono kwenye service
                };
        }
}