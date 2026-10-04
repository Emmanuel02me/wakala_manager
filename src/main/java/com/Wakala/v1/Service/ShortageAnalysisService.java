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

        // Cash shortage
        if (recon.cashDifference() != null
                && recon.cashDifference().compareTo(BigDecimal.ZERO) < 0) {
            totalShortage = totalShortage.add(recon.cashDifference().abs());
        } else if (recon.cashDifference() != null
                && recon.cashDifference().compareTo(BigDecimal.ZERO) > 0) {
            totalOverage = totalOverage.add(recon.cashDifference());
        }

        // General suggestions
        generalSuggestions.addAll(generateGeneralSuggestions(recon, totalShortage, totalOverage));

        return new ShortageAnalysis(
                sessionId,
                recon.overallStatus(),
                totalShortage,
                totalOverage,
                providerAnalyses,
                generalSuggestions);
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

        // Percent
        BigDecimal percent = p.openingFloat().compareTo(BigDecimal.ZERO) > 0
                ? absDiff.multiply(HUNDRED).divide(p.openingFloat(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Severity
        severity = calculateSeverity(absDiff, percent);

        // ══════════════════════════════════════════════
        // SUGGESTIONS KWA KILA PATTERN
        // ══════════════════════════════════════════════

        if (diff.compareTo(BigDecimal.ZERO) < 0) {
            // SHORTAGE — float imepungua kuliko expected
            suggestions.add("Float ilipungua kwa " + absDiff + " kuliko ilivyotarajiwa.");

            // Pattern 1: Hakuna miamala lakini float imepungua
            if (p.transactionCount() == 0) {
                suggestions.add(
                        "⚠️ Hakuna miamala iliyorekodiwa kwenye " + p.providerName() +
                                " lakini float imepungua. Angalia kama:");
                suggestions.add("   - Opening balance uliyoweka ilikuwa sahihi?");
                suggestions.add("   - Kuna muamala uliofanyika lakini haukurekodiwa?");
                suggestions.add("   - Kuna mtu alitumia float nje ya mfumo?");
            }

            // Pattern 2: Shortage = exact round number
            if (isRoundNumber(absDiff)) {
                suggestions.add(
                        "💡 Shortage ni namba kamili (" + absDiff + "). " +
                                "Angalia kama kuna transfer uliyosahau kurekodi.");
            }

            // Pattern 3: Shortage kubwa sana (CRITICAL)
            if (severity.equals("CRITICAL")) {
                suggestions.add(
                        "🔴 Shortage ni kubwa mno (" + percent + "% ya opening). " +
                                "Hii inaweza kuwa:");
                suggestions.add("   - Opening balance isiyo sahihi");
                suggestions.add("   - Transfer kubwa isiyorekodiwa");
                suggestions.add("   - Kosa la kuingiza data");
                suggestions.add("   - Wizi (kama mifumo mingine ni BALANCED)");
            }

            // Pattern 4: Angalia transfers
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
                suggestions.add(
                        "💡 Hakuna transfers zilizorekodiwa kwa " + p.providerName() +
                                ". Kama ulifanya transfer, angalia kama imerekodiwa.");
            }

        } else {
            // OVERAGE — float imeongezeka kuliko expected
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

        // Cash shortage
        if (recon.cashDifference() != null
                && recon.cashDifference().compareTo(BigDecimal.ZERO) < 0) {
            suggestions.add("💰 CASH SHORTAGE (" + recon.cashDifference().abs() + "):");
            suggestions.add("   - Angalia kama gharama zote zilirekodiwa");
            suggestions.add("   - Angalia kama cash-out zote zilirekodiwa");
            suggestions.add("   - Angalia kama kulikuwa na float top-up kutoka cash");
        }

        // Multiple providers with shortage
        long shortageCount = recon.providers().stream()
                .filter(p -> "SHORTAGE".equals(p.status()))
                .count();

        if (shortageCount > 1) {
            suggestions.add("");
            suggestions.add("⚠️ Provider " + shortageCount + " wana shortage. " +
                    "Hii inaweza kuashiria:");
            suggestions.add("   - Opening balances zote zilikuwa wrong");
            suggestions.add("   - Kuna kosa la kimfumo kwenye kuingiza data");
            suggestions.add("   - Wakala alifanya transfers nyingi bila kurekodi");
        }

        // Large total shortage
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
        if (absDiff.compareTo(new BigDecimal("10000")) > 0
                || percent.compareTo(new BigDecimal("5")) > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private boolean isRoundNumber(BigDecimal amount) {
        // Angalia kama ni multiple ya 10,000
        return amount.remainder(new BigDecimal("10000")).compareTo(BigDecimal.ZERO) == 0;
    }
}