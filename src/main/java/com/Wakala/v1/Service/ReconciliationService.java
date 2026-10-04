package com.Wakala.v1.Service;

import com.Wakala.v1.Dto.ProviderReconciliation;
import com.Wakala.v1.Dto.ReconciliationResponse;
import com.Wakala.v1.Entity.*;
import com.Wakala.v1.Exception.ResourceNotFoundException;
import com.Wakala.v1.Repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    // Tolerance: TZS 1 (kwa kuwa TZS haina senti kivitendo)
    private static final BigDecimal TOLERANCE = new BigDecimal("1");

    private final DailySessionRepository sessionRepository;
    private final FloatBalanceRepository floatBalanceRepository;
    private final TransactionRepository transactionRepository;
    private final ExpenseRepository expenseRepository;

    /**
     * Hesabu reconciliation kwa kikao kimoja.
     */
    @Transactional(readOnly = true)
    public ReconciliationResponse calculate(Long sessionId) {
        DailySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session haipatikani"));

        List<FloatBalance> floatBalances = floatBalanceRepository.findBySessionId(sessionId);
        List<Transaction> transactions = transactionRepository.findBySessionId(sessionId);
        List<Expense> expenses = expenseRepository.findBySessionIdAndStatusAndCancelledFalse(
                sessionId, Expense.ExpenseStatus.APPROVED);

        // ── Group transactions by provider (source + destination) ──
        Map<Long, List<Transaction>> txByProvider = new HashMap<>();
        for (Transaction tx : transactions) {
            txByProvider.computeIfAbsent(tx.getProvider().getId(), k -> new ArrayList<>()).add(tx);
            if (tx.getDestinationProvider() != null) {
                txByProvider.computeIfAbsent(tx.getDestinationProvider().getId(), k -> new ArrayList<>())
                        .add(tx);
            }
        }

        // ── Calculate per provider ──
        List<ProviderReconciliation> providerResults = new ArrayList<>();
        for (FloatBalance fb : floatBalances) {
            providerResults.add(calculateForProvider(fb, txByProvider));
        }

        // ── Cash Effect (kutoka transactions) ──
        BigDecimal cashEffectTotal = transactions.stream()
                .map(Transaction::getCashEffect)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Hesabu matumizi KABLA ya expectedCash
        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ✅ Expected Cash = Opening + Cash Effect - Expenses
        // Expenses zinapunguza cash inayotarajiwa (mfanyakazi alitumia kutoka cash)
        BigDecimal expectedCash = session.getOpeningCash()
                .add(cashEffectTotal)
                .subtract(totalExpenses);

        BigDecimal actualCash = session.getClosingCash();
        BigDecimal cashDifference = actualCash != null
                ? actualCash.subtract(expectedCash)
                : null;
        cashDifference = normalizeZero(cashDifference);

        // ── Faida ──
        BigDecimal totalInstantProfit = providerResults.stream()
                .map(ProviderReconciliation::instantProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netProfit = totalInstantProfit.subtract(totalExpenses);

        // ── Volume ──
        BigDecimal totalMonthlyVolume = providerResults.stream()
                .map(ProviderReconciliation::monthlyVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Overall status ──
        String overallStatus = determineOverallStatus(actualCash, cashDifference, providerResults);

        return new ReconciliationResponse(
                sessionId,
                session.getSessionDate(),
                session.getStatus().name(),
                session.getOpeningCash(),
                cashEffectTotal,
                expectedCash,
                actualCash,
                cashDifference,
                totalInstantProfit,
                totalExpenses,
                netProfit,
                transactions.size(),
                totalMonthlyVolume,
                providerResults,
                overallStatus);
    }

    /**
     * Hesabu kwa provider mmoja.
     */
    private ProviderReconciliation calculateForProvider(
            FloatBalance fb,
            Map<Long, List<Transaction>> txByProvider) {

        Long providerId = fb.getProvider().getId();
        List<Transaction> providerTxs = txByProvider.getOrDefault(providerId, List.of());

        BigDecimal floatEffect = BigDecimal.ZERO;
        BigDecimal instantProfit = BigDecimal.ZERO;
        BigDecimal networkCommissionTotal = BigDecimal.ZERO;
        BigDecimal ownerCommissionTotal = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        int txCount = 0;
        int monthlyTxCount = 0;
        BigDecimal monthlyVolume = BigDecimal.ZERO;

        for (Transaction tx : providerTxs) {
            boolean isSource = tx.getProvider().getId().equals(providerId);
            boolean isDestination = tx.getDestinationProvider() != null
                    && tx.getDestinationProvider().getId().equals(providerId);

            // Float effect
            if (isSource) {
                floatEffect = floatEffect.add(tx.getFloatEffect());
            } else if (isDestination) {
                floatEffect = floatEffect.subtract(tx.getFloatEffect());
            }

            txCount++;
            totalAmount = totalAmount.add(tx.getAmount());

            // Faida
            if (tx.getRevenueModel() == RevenueModel.INSTANT) {
                instantProfit = instantProfit.add(tx.getOwnerCommission());
            }

            if (isMonthlyTracked(tx.getTransactionType())) {
                monthlyTxCount++;
                monthlyVolume = monthlyVolume.add(tx.getAmount());
            }

            // Commissions (kwa provider hii pekee)
            if (isSource) {
                networkCommissionTotal = networkCommissionTotal.add(tx.getNetworkCommission());
                ownerCommissionTotal = ownerCommissionTotal.add(tx.getOwnerCommission());
            }
        }

        BigDecimal openingFloat = fb.getOpeningBalance();
        BigDecimal expectedClosing = openingFloat.add(floatEffect);
        BigDecimal actualClosing = fb.getClosingBalance();

        // ✅ Handle null kabla ya kuita .abs()
        BigDecimal difference = null;
        if (actualClosing != null) {
            difference = actualClosing.subtract(expectedClosing);
            // Normalize kwa tolerance ya TZS 1
            if (difference.abs().compareTo(TOLERANCE) < 0) {
                difference = BigDecimal.ZERO;
            }
        }

        String status = determineFloatStatus(actualClosing, difference);

        return new ProviderReconciliation(
                providerId,
                fb.getProvider().getName(),
                fb.getProvider().getType().name(),
                openingFloat,
                floatEffect,
                expectedClosing,
                actualClosing,
                difference,
                txCount,
                totalAmount,
                instantProfit,
                networkCommissionTotal,
                ownerCommissionTotal,
                monthlyTxCount,
                monthlyVolume,
                status);
    }

    private boolean isMonthlyTracked(Transaction.TransactionType type) {
        return switch (type) {
            case TILL_CASH_IN, TILL_CASH_OUT, TILL_SEND, TILL_PAY_BILL,
                    BANK_CONTROL_NUMBER, BANK_CASH_OUT ->
                true;
            default -> false;
        };
    }

    /**
     * ✅ Normalize: kama value ni ndani ya TZS 1, rudisha ZERO kamili.
     * Inashughulikia -0.00, 0.00, -0.01, -0.5, n.k. NA null.
     */
    private BigDecimal normalizeZero(BigDecimal value) {
        if (value == null)
            return null;
        if (value.abs().compareTo(TOLERANCE) < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private String determineFloatStatus(BigDecimal actual, BigDecimal difference) {
        if (actual == null)
            return "PENDING";
        if (difference == null)
            return "PENDING";

        // ✅ Tumia TOLERANCE (kwa uthabiti)
        if (difference.abs().compareTo(TOLERANCE) < 0) {
            return "BALANCED";
        }

        return difference.compareTo(BigDecimal.ZERO) > 0 ? "OVERAGE" : "SHORTAGE";
    }

    private String determineOverallStatus(BigDecimal actualCash, BigDecimal cashDifference,
            List<ProviderReconciliation> providers) {
        if (actualCash == null)
            return "PENDING";

        BigDecimal normalizedCashDiff = normalizeZero(cashDifference);

        boolean hasShortage = providers.stream()
                .anyMatch(p -> "SHORTAGE".equals(p.status()));
        boolean hasOverage = providers.stream()
                .anyMatch(p -> "OVERAGE".equals(p.status()));

        boolean cashShortage = normalizedCashDiff != null
                && normalizedCashDiff.compareTo(BigDecimal.ZERO) < 0;
        boolean cashOverage = normalizedCashDiff != null
                && normalizedCashDiff.compareTo(BigDecimal.ZERO) > 0;

        if (hasShortage || cashShortage)
            return "SHORTAGE";
        if (hasOverage || cashOverage)
            return "OVERAGE";
        return "BALANCED";
    }
}