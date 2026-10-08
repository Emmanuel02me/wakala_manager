package com.Wakala.v1.Service;

import com.Wakala.v1.Dto.OwnerRuleRequest;
import com.Wakala.v1.Dto.OwnerRuleResponse;
import com.Wakala.v1.Dto.UpdateOwnerRuleRequest;
import com.Wakala.v1.Entity.NetworkRate;
import com.Wakala.v1.Entity.OwnerRule;
import com.Wakala.v1.Entity.Provider;
import com.Wakala.v1.Entity.Transaction;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Exception.BusinessException;
import com.Wakala.v1.Exception.ResourceNotFoundException;
import com.Wakala.v1.Repositories.NetworkRateRepository;
import com.Wakala.v1.Repositories.OwnerRuleRepository;
import com.Wakala.v1.Repositories.ProviderRepository;
import com.Wakala.v1.Repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OwnerRuleService {

    private final OwnerRuleRepository ruleRepository;
    private final NetworkRateRepository networkRateRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ═══════════════════════════════════════════════
    // CREATE — Owner anaweka ownerCommission pekee
    // ═══════════════════════════════════════════════
    @Transactional
    public OwnerRuleResponse create(OwnerRuleRequest req, Long userId) {
        Provider provider = providerRepository.findById(req.providerId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider haipatikani"));

        User createdBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User haipatikani"));

        validateRange(req);
        LocalDate from = req.effectiveFrom() != null ? req.effectiveFrom() : LocalDate.now();
        checkOverlap(req.providerId(), req.transactionType(), req.minAmount(),
                req.maxAmount(), from);

        // ✅ Auto-fill effectiveTo + networkRate reference
        LocalDate effectiveTo = null;
        NetworkRate matchedRate = null;

        if (req.transactionType() == Transaction.TransactionType.LIPA_CASH_OUT
                || req.transactionType() == Transaction.TransactionType.TILL_CASH_OUT
                || req.transactionType() == Transaction.TransactionType.BANK_CASH_OUT) {

            List<NetworkRate> rates = networkRateRepository
                    .findByProviderIdAndTransactionTypeAndActiveTrueOrderByMinAmountAsc(
                            req.providerId(), req.transactionType());

            for (NetworkRate nr : rates) {
                boolean matches = nr.getMinAmount().compareTo(req.minAmount()) == 0
                        && (nr.getMaxAmount() == null && req.maxAmount() == null
                                || (nr.getMaxAmount() != null && req.maxAmount() != null
                                        && nr.getMaxAmount().compareTo(req.maxAmount()) == 0));
                if (matches) {
                    matchedRate = nr;
                    effectiveTo = nr.getEffectiveTo();
                    break;
                }
            }
        }

        OwnerRule rule = OwnerRule.builder()
                .provider(provider)
                .transactionType(req.transactionType())
                .minAmount(req.minAmount())
                .maxAmount(req.maxAmount())
                .ownerCommission(req.ownerCommission())
                .effectiveFrom(from)
                .effectiveTo(effectiveTo)
                .networkRate(matchedRate) // ✅ Sasa inatumika
                .active(true)
                .createdBy(createdBy)
                .notes(req.notes())
                .build();

        rule = ruleRepository.save(rule);
        auditService.log(userId, "CREATE_OWNER_RULE",
                "OwnerRule", rule.getId(), null, rule.toString());
        return toResponse(rule);
    }

    // ═══════════════════════════════════════════════
    // SUPERSEDE — Badilisha ownerCommission
    // ═══════════════════════════════════════════════
    @Transactional
    public OwnerRuleResponse supersede(UpdateOwnerRuleRequest req, Long userId) {
        OwnerRule old = ruleRepository.findById(req.existingRuleId())
                .orElseThrow(() -> new ResourceNotFoundException("Rule haipatikani"));

        if (!old.isActive()) {
            throw new BusinessException("Rule hii imefungwa tayari");
        }

        LocalDate newFrom = req.effectiveFrom();
        if (newFrom.isBefore(old.getEffectiveFrom())) {
            throw new BusinessException("effectiveFrom mpya haiwezi kuwa kabla ya rule ya zamani");
        }

        User changedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User haipatikani"));

        old.setEffectiveTo(newFrom.minusDays(1));
        ruleRepository.save(old);

        OwnerRule newRule = OwnerRule.builder()
                .provider(old.getProvider())
                .transactionType(old.getTransactionType())
                .minAmount(old.getMinAmount())
                .maxAmount(old.getMaxAmount())
                .ownerCommission(req.ownerCommission())
                .effectiveFrom(newFrom)
                .effectiveTo(old.getEffectiveTo())
                .active(true)
                .createdBy(changedBy)
                .notes(req.notes())
                .build();

        newRule = ruleRepository.save(newRule);

        auditService.log(userId, "SUPERSEDE_OWNER_RULE",
                "OwnerRule", newRule.getId(),
                old.toString(), newRule.toString());

        return toResponse(newRule);
    }

    // ═══════════════════════════════════════════════
    // DEACTIVATE
    // ═══════════════════════════════════════════════
    @Transactional
    public void deactivate(Long ruleId, Long userId) {
        OwnerRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule haipatikani"));

        rule.setActive(false);
        rule.setEffectiveTo(LocalDate.now());
        ruleRepository.save(rule);

        auditService.log(userId, "DEACTIVATE_OWNER_RULE",
                "OwnerRule", ruleId, "active=true", "active=false");
    }

    // ═══════════════════════════════════════════════
    // LOOKUP — Inaitwa na TransactionService
    // ═══════════════════════════════════════════════
    @Transactional(readOnly = true)
    public OwnerRule findApplicableRule(Long providerId,
            Transaction.TransactionType type,
            BigDecimal amount) {
        return ruleRepository.findApplicable(providerId, type, amount, LocalDate.now())
                .orElseThrow(() -> new BusinessException(
                        "Hakuna owner rule inayolingana na kiasi " + amount +
                                " kwa provider " + providerId + " na aina " + type));
    }

    // ═══════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════
    @Transactional(readOnly = true)
    public List<OwnerRuleResponse> getAllActive() {
        return ruleRepository.findByActiveTrueOrderByProviderIdAscMinAmountAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OwnerRuleResponse> getByProviderAndType(
            Long providerId, Transaction.TransactionType type) {
        return ruleRepository.findByProviderIdAndTransactionTypeOrderByEffectiveFromDesc(providerId, type)
                .stream().map(this::toResponse).toList();
    }

    // ═══════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════
    private void validateRange(OwnerRuleRequest req) {
        if (req.maxAmount() != null && req.maxAmount().compareTo(req.minAmount()) < 0) {
            throw new BusinessException("maxAmount lazima iwe kubwa au sawa na minAmount");
        }
    }

    private void checkOverlap(Long providerId, Transaction.TransactionType type,
            BigDecimal min, BigDecimal max, LocalDate from) {
        List<OwnerRule> existing = ruleRepository.findOverlapping(providerId, type, from);

        for (OwnerRule r : existing) {
            boolean amountOverlaps = (max == null || max.compareTo(r.getMinAmount()) >= 0) &&
                    (r.getMaxAmount() == null || r.getMaxAmount().compareTo(min) >= 0);

            if (amountOverlaps) {
                throw new BusinessException(
                        "Range " + min + "–" + (max == null ? "∞" : max) +
                                " inapishana na rule iliyopo (" + r.getMinAmount() + "–" +
                                (r.getMaxAmount() == null ? "∞" : r.getMaxAmount()) + ")");
            }
        }
    }

    // ═══════════════════════════════════════════════
    // MAPPER
    // ═══════════════════════════════════════════════
    private OwnerRuleResponse toResponse(OwnerRule r) {
        LocalDate today = LocalDate.now();
        return new OwnerRuleResponse(
                r.getId(),
                r.getProvider().getId(),
                r.getProvider().getName(),
                r.getTransactionType().name(),
                r.getMinAmount(),
                r.getMaxAmount(),
                r.getOwnerCommission(),
                r.getEffectiveFrom(),
                r.getEffectiveTo(),
                r.isActive(),
                r.isEffectiveOn(today),
                r.getCreatedBy() != null ? r.getCreatedBy().getFullName() : null,
                r.getCreatedAt(),
                r.getNotes());
    }
}