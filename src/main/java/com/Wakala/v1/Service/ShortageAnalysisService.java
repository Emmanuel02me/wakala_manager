package com.Wakala.v1.Service;

import com.Wakala.v1.Dto.ProviderReconciliation;
import com.Wakala.v1.Dto.ReconciliationResponse;
import com.Wakala.v1.Dto.ShortageAnalysis;
import com.Wakala.v1.Entity.Transaction;
import com.Wakala.v1.Repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShortageAnalysisService {

    private final ReconciliationService reconciliationService;
    private final TransactionRepository transactionRepository;

    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    @Transactional(readOnly = true)
    public ShortageAnalysis analyze(Long sessionId) {
        ReconciliationResponse recon = reconciliationService.calculate(sessionId);

        List<ShortageAnalysis.ProviderAnalysis> providerAnalyses = new ArrayList<>();
        List<String> generalSuggestions = new ArrayList<>();

        BigDecimal totalShortage = BigDecimal.ZERO;
        BigDecimal totalOverage = BigDecimal.ZERO;

        for (ProviderReconciliation p : recon.providers()) {
            ShortageAnalysis.ProviderAnalysis analysis = analyzeProvider(p, sessionId);
            providerAnalyses.add(analysis);

            if (p.floatDifference() != null) {
                if (p.floatDifference().compareTo(BigDecimal.ZERO) < 0) {
                    totalShortage = totalShortage.add(p.floatDifference().abs());
                } else {
                    totalOverage = totalOverage.add(p.floatDifference());
                }
            }
        }

        if (recon.cashDifference() != null
                && recon.cashDifference().compareTo(BigDecimal.ZERO) < 0) {
            totalShortage = totalShortage.add(recon.cashDifference().abs());
        } else if (recon.cashDifference() != null
                && recon.cashDifference().compareTo(BigDecimal.ZERO) > 0) {
            totalOverage = totalOverage.add(recon.cashDifference());
        }

        generalSuggestions.addAll(generateGeneralSuggestions(recon, totalShortage, totalOverage));

        // ═══════════════════════════════════════════════
        // TOTAL BUSINESS VALUE ANALYSIS
        // ═══════════════════════════════════════════════
        List<String> totalValueExplanations = generateTotalValueExplanations(recon);

        return new ShortageAnalysis(
                sessionId,
                recon.overallStatus(),
                totalShortage,
                totalOverage,
                recon.openingTotalValue(),
                recon.expectedTotalValue(),
                recon.actualTotalValue(),
                recon.totalValueDifference(),
                recon.totalValueStatus(),
                totalValueExplanations,
                providerAnalyses,
                generalSuggestions);
    }

    // ═══════════════════════════════════════════════════════
    // TOTAL VALUE EXPLANATIONS — "kwa nini total imebadilika"
    // ═══════════════════════════════════════════════════════
    private List<String> generateTotalValueExplanations(ReconciliationResponse recon) {
        List<String> explanations = new ArrayList<>();

        BigDecimal opening = recon.openingTotalValue();
        BigDecimal expected = recon.expectedTotalValue();
        BigDecimal actual = recon.actualTotalValue();
        BigDecimal diff = recon.totalValueDifference();
        BigDecimal profit = recon.totalInstantProfit();
        BigDecimal expenses = recon.totalExpenses();

        // Kama bado haijafungwa — onyesha muhtasari wa opening pekee
        if (actual == null) {
            explanations.add("💰 Ulianza na: TZS " + format(opening));
            explanations.add("   (Cash: TZS " + format(recon.openingCash())
                    + " + Floats: TZS " + format(opening.subtract(recon.openingCash())) + ")");
            explanations.add("");
            explanations.add("📊 Hadi sasa:");
            explanations.add("   Faida: TZS " + format(profit));
            explanations.add("   Matumizi: TZS " + format(expenses));
            explanations.add("   Total inayotarajiwa ukifunga sasa: TZS " + format(expected));
            return explanations;
        }

        // ═══════════════════════════════════════════════
        // KIKAO KIMEFUNGWA — TOA UFAFANUZI KAMILI
        // ═══════════════════════════════════════════════

        explanations.add("📊 MUHTASARI WA TOTAL:");
        explanations.add("   Ulianza na: TZS " + format(opening));
        explanations.add("   Faida: +TZS " + format(profit));
        explanations.add("   Matumizi: -TZS " + format(expenses));
        explanations.add("   Inatarajiwa: TZS " + format(expected) + " (= Opening + Faida - Matumizi)");
        explanations.add("   Umefunga na: TZS " + format(actual));
        explanations.add("");

        int diffSign = diff.compareTo(BigDecimal.ZERO);

        // ─── BALANCED ───
        if (diffSign == 0) {
            explanations.add("✅ TOTAL IMELINGANA KIKAMILIFU!");
            explanations.add("   Hesabu zote zinaingiana: TZS " + format(actual));
            explanations.add("   Cash + Floats wote = Opening + Faida - Matumizi");
            return explanations;
        }

        // ─── SHORTAGE ───
        if (diffSign < 0) {
            explanations.add("⚠️ TOTAL IMEPUNGUA KWA: TZS " + format(diff.abs()));
            explanations.add("");
            explanations.add("🔍 UCHAMBUZI — Wapi pesa imepungua:");
            explanations.add("");

            // Cash shortage
            BigDecimal cashDiff = recon.cashDifference();
            if (cashDiff != null && cashDiff.compareTo(BigDecimal.ZERO) < 0) {
                explanations.add("   💵 Cash: -TZS " + format(cashDiff.abs())
                        + " (inatarajiwa TZS " + format(recon.expectedCash())
                        + ", umeleta TZS " + format(recon.actualCash()) + ")");
            } else if (cashDiff != null && cashDiff.compareTo(BigDecimal.ZERO) > 0) {
                explanations.add("   💵 Cash: +TZS " + format(cashDiff)
                        + " (imezidi — angalia chini)");
            }

            // Float shortages per provider
            for (ProviderReconciliation p : recon.providers()) {
                if (p.floatDifference() != null && p.floatDifference().compareTo(BigDecimal.ZERO) < 0) {
                    explanations.add("   📱 " + p.providerName() + ": -TZS " + format(p.floatDifference().abs())
                            + " (inatarajiwa TZS " + format(p.expectedClosingFloat())
                            + ", ipo TZS " + format(p.actualClosingFloat()) + ")");
                }
            }

            explanations.add("");
            explanations.add("💡 SABABU ZINAZOWEZEKANA:");
            explanations.add("   1. Miamala ambayo haikurekodiwa (mfanyakazi alifanya bila kuingiza)");
            explanations.add("   2. Transfer kati ya provider haikurekodiwa");
            explanations.add("   3. Opening balance ya leo haikuwa sahihi (imeingizwa chini ya ilivyokuwa)");
            explanations.add("   4. Kosa la kuhesabu (typo wakati wa kuingiza namba)");
            explanations.add("   5. Wizi au upotevu halisi wa pesa");
            explanations.add("");
            explanations.add("🔧 HATUA ZA KUFUATA:");
            explanations.add("   1. Kagua kila provider — angalia shortage kubwa zaidi");
            explanations.add("   2. Linganisha na SMS za mtandao (kama zipo)");
            explanations.add("   3. Muulize mfanyakazi kama alifanya transfer yoyote");
            explanations.add("   4. Kagua opening balance ya jana (kama ilikuwa sahihi)");
            explanations.add("   5. Kama haipatikani — fungua kikao tena na urekebishe");
            return explanations;
        }

        // ─── OVERAGE ───
        explanations.add("⚠️ TOTAL IMEONGEZEKA KWA: TZS " + format(diff));
        explanations.add("");
        explanations.add("🔍 UCHAMBUZI — Wapi pesa imeongezeka:");
        explanations.add("");

        BigDecimal cashDiffOverage = recon.cashDifference();
        if (cashDiffOverage != null && cashDiffOverage.compareTo(BigDecimal.ZERO) > 0) {
            explanations.add("   💵 Cash: +TZS " + format(cashDiffOverage)
                    + " (inatarajiwa TZS " + format(recon.expectedCash())
                    + ", umeleta TZS " + format(recon.actualCash()) + ")");
        }

        for (ProviderReconciliation p : recon.providers()) {
            if (p.floatDifference() != null && p.floatDifference().compareTo(BigDecimal.ZERO) > 0) {
                explanations.add("   📱 " + p.providerName() + ": +TZS " + format(p.floatDifference())
                        + " (inatarajiwa TZS " + format(p.expectedClosingFloat())
                        + ", ipo TZS " + format(p.actualClosingFloat()) + ")");
            }
        }

        explanations.add("");
        explanations.add("💡 SABABU ZINAZOWEZEKANA:");
        explanations.add("   1. Miamala ilirekodiwa mara mbili (double-entry)");
        explanations.add("   2. Faida halisi ni kubwa kuliko ilivyohesabiwa");
        explanations.add("   3. Transfer ilikuja kutoka nje (mfano: mtu alikudhamini)");
        explanations.add("   4. Opening balance ilikuwa chini ya ilivyokuwa halisi");
        explanations.add("   5. Cash iliyokuwa imehekwa nje imerudi");
        explanations.add("");
        explanations.add("🔧 HATUA ZA KUFUATA:");
        explanations.add("   1. Kagua miamala ya leo — kuna yoyote iliyorekodiwa mara mbili?");
        explanations.add("   2. Angalia kama kuna transfer isiyo ya kawaida");
        explanations.add("   3. Thibitisha opening balance ya leo ilikuwa sahihi");
        explanations.add("   4. Kama ni faida halisi — furaha! Ila thibitisha kwa usahihi");
        return explanations;
    }

    private String format(BigDecimal value) {
        if (value == null) return "0";
        return String.format("%,.0f", value);
    }

    private ShortageAnalysis.ProviderAnalysis analyzeProvider(
            ProviderReconciliation p, Long sessionId) {

        List<String> suggestions = new ArrayList<>();
        String severity = "OK";

        if (p.floatDifference() == null || p.floatDifference().compareTo(BigDecimal.ZERO) == 0) {
            return new ShortageAnalysis.ProviderAnalysis(
                    p.providerId(), p.providerName(), "BALANCED",
                    p.openingFloat(), p.expectedClosingFloat(), p.actualClosingFloat(),
                    BigDecimal.ZERO, BigDecimal.ZERO, p.transactionCount(),
                    "OK", List.of("Hakuna tatizo. Hesabu zinalingana."));
        }

        BigDecimal diff = p.floatDifference();
        BigDecimal absDiff = diff.abs();

        BigDecimal percent = p.openingFloat().compareTo(BigDecimal.ZERO) > 0
                ? absDiff.multiply(HUNDRED).divide(p.openingFloat(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        severity = calculateSeverity(absDiff, percent);

        if (diff.compareTo(BigDecimal.ZERO) < 0) {
            suggestions.add("Float ilipungua kwa " + absDiff + " kuliko ilivyotarajiwa.");

            if (p.transactionCount() == 0) {
                suggestions.add("⚠️ Hakuna miamala iliyorekodiwa kwenye " + p.providerName()
                        + " lakini float imepungua. Angalia kama:");
                suggestions.add("   - Opening balance uliyoweka ilikuwa sahihi?");
                suggestions.add("   - Kuna muamala uliofanyika lakini haukurekodiwa?");
                suggestions.add("   - Kuna mtu alitumia float nje ya mfumo?");
            }

            if (isRoundNumber(absDiff)) {
                suggestions.add("💡 Shortage ni namba kamili (" + absDiff + "). "
                        + "Angalia kama kuna transfer uliyosahau kurekodi.");
            }

            if (severity.equals("CRITICAL")) {
                suggestions.add("🔴 Shortage ni kubwa mno (" + percent + "% ya opening). Hii inaweza kuwa:");
                suggestions.add("   - Opening balance isiyo sahihi");
                suggestions.add("   - Transfer kubwa isiyorekodiwa");
                suggestions.add("   - Kosa la kuingiza data");
                suggestions.add("   - Wizi (kama mifumo mingine ni BALANCED)");
            }

            List<Transaction> transfers = transactionRepository
                    .findBySessionId(sessionId).stream()
                    .filter(t -> t.getTransactionType() == Transaction.TransactionType.TRANSFER_PROVIDER
                            || t.getTransactionType() == Transaction.TransactionType.TRANSFER_BANK_TO_FLOAT
                            || t.getTransactionType() == Transaction.TransactionType.TRANSFER_FLOAT_TO_BANK)
                    .filter(t -> t.getProvider().getId().equals(p.providerId())
                            || (t.getDestinationProvider() != null
                                    && t.getDestinationProvider().getId().equals(p.providerId())))
                    .toList();

            if (transfers.isEmpty() && absDiff.compareTo(TEN_THOUSAND) > 0) {
                suggestions.add("💡 Hakuna transfers zilizorekodiwa kwa " + p.providerName()
                        + ". Kama ulifanya transfer, angalia kama imerekodiwa.");
            }
        } else {
            suggestions.add("Float imeongezeka kwa " + diff + " kuliko ilivyotarajiwa.");
            suggestions.add("Angalia kama:");
            suggestions.add("   - Kuna float top-up isiyorekodiwa?");
            suggestions.add("   - Kuna muamala uliorekodiwa mara mbili?");
            suggestions.add("   - Opening balance ilikuwa chini ya actual?");
        }

        return new ShortageAnalysis.ProviderAnalysis(
                p.providerId(), p.providerName(), p.status(),
                p.openingFloat(), p.expectedClosingFloat(), p.actualClosingFloat(),
                diff, percent, p.transactionCount(),
                severity, suggestions);
    }

    private List<String> generateGeneralSuggestions(
            ReconciliationResponse recon, BigDecimal totalShortage, BigDecimal totalOverage) {

        List<String> suggestions = new ArrayList<>();

        if (recon.overallStatus().equals("BALANCED")) {
            suggestions.add("✅ Kikao kimefungwa kwa usahihi. Hakuna tatizo.");
            return suggestions;
        }

        suggestions.add("🔍 Uchambuzi wa Kikao:");
        suggestions.add("   Jumla ya shortage: " + totalShortage);
        suggestions.add("   Jumla ya overage: " + totalOverage);
        suggestions.add("");

        if (recon.cashDifference() != null
                && recon.cashDifference().compareTo(BigDecimal.ZERO) < 0) {
            suggestions.add("💰 CASH SHORTAGE (" + recon.cashDifference().abs() + "):");
            suggestions.add("   - Angalia kama gharama zote zilirekodiwa");
            suggestions.add("   - Angalia kama cash-out zote zilirekodiwa");
            suggestions.add("   - Angalia kama kulikuwa na float top-up kutoka cash");
        }

        long shortageCount = recon.providers().stream()
                .filter(p -> "SHORTAGE".equals(p.status()))
                .count();

        if (shortageCount > 1) {
            suggestions.add("");
            suggestions.add("⚠️ Provider " + shortageCount + " wana shortage. Hii inaweza kuashiria:");
            suggestions.add("   - Opening balances zote zilikuwa wrong");
            suggestions.add("   - Kuna kosa la kimfumo kwenye kuingiza data");
            suggestions.add("   - Wakala alifanya transfers nyingi bila kurekodi");
        }

        if (totalShortage.compareTo(new BigDecimal("100000")) > 0) {
            suggestions.add("");
            suggestions.add("🚨 SHORTAGE KUBWA SANA (" + totalShortage + "):");
            suggestions.add("   Hatua za haraka:");
            suggestions.add("   1. Kagua opening balances za leo");
            suggestions.add("   2. Linganisha na last closed session");
            suggestions.add("   3. Angalia kila transfer iliyofanyika");
            suggestions.add("   4. Wasiliana na wakala kwa maelezo");
            suggestions.add("   5. Fikiria kufungua tena kikao na values sahihi");
        }

        return suggestions;
    }

    private String calculateSeverity(BigDecimal absDiff, BigDecimal percent) {
        if (absDiff.compareTo(new BigDecimal("500000")) > 0
                || percent.compareTo(new BigDecimal("50")) > 0) {
            return "CRITICAL";
        }
        if (absDiff.compareTo(new BigDecimal("100000")) > 0
                || percent.compareTo(new BigDecimal("20")) > 0) {
            return "HIGH";
        }
        if (absDiff.compareTo(TEN_THOUSAND) > 0
                || percent.compareTo(new BigDecimal("5")) > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean isRoundNumber(BigDecimal amount) {
        return amount.remainder(TEN_THOUSAND).compareTo(BigDecimal.ZERO) == 0;
    }
}