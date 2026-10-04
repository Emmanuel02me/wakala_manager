package com.Wakala.v1.Service;

import com.Wakala.v1.Dto.CommissionRuleRequest;
import com.Wakala.v1.Dto.CommissionRuleResponse;
import com.Wakala.v1.Dto.UpdateCommissionRuleRequest;
import com.Wakala.v1.Entity.CommissionRule;
import com.Wakala.v1.Entity.Provider;
import com.Wakala.v1.Entity.Transaction;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Exception.BusinessException;
import com.Wakala.v1.Exception.ResourceNotFoundException;
import com.Wakala.v1.Repositories.CommissionRuleRepository;
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
public class CommissionRuleService {

    private final CommissionRuleRepository ruleRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ═══════════════════════════════════════════════════
    // 1. CREATE — Owner anaweka rule mpya
    // ═══════════════════════════════════════════════════
    @Transactional
    public CommissionRuleResponse create(CommissionRuleRequest req, Long userId) {
        Provider provider = providerRepository.findById(req.providerId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider haipatikani"));

        User createdBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User haipatikani"));

        validateRange(req);
        LocalDate from = req.effectiveFrom() != null ? req.effectiveFrom() : LocalDate.now();
        checkOverlap(req.providerId(), req.transactionType(), req.minAmount(),
                req.maxAmount(), from, null);

        CommissionRule rule = CommissionRule.builder()
                .provider(provider).transactionType(req.transactionType())
                .minAmount(req.minAmount()).maxAmount(req.maxAmount())
                .networkCommission(req.networkCommission())
                .ownerCommission(req.ownerCommission())
                .effectiveFrom(from).active(true).createdBy(createdBy)
                .notes(req.notes()).build();

        rule = ruleRepository.save(rule);
        auditService.log(userId, "CREATE_COMMISSION_RULE",
                "CommissionRule", rule.getId(), null, rule.toString());
        return toResponse(rule);
    }

    // ═══════════════════════════════════════════════════
    // 2. SUPERSEDE — Badilisha rates (fungua zamani, unda mpya)
    // ═══════════════════════════════════════════════════
    @Transactional
    public CommissionRuleResponse supersede(UpdateCommissionRuleRequest req, Long userId) {
        CommissionRule old = ruleRepository.findById(req.existingRuleId())
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

        CommissionRule newRule = CommissionRule.builder()
                .provider(old.getProvider()).transactionType(old.getTransactionType())
                .minAmount(old.getMinAmount()).maxAmount(old.getMaxAmount())
                .networkCommission(req.networkCommission())
                .ownerCommission(req.ownerCommission())
                .effectiveFrom(newFrom).active(true).createdBy(changedBy)
                .notes(req.notes()).build();

        newRule = ruleRepository.save(newRule);

        auditService.log(userId, "SUPERSEDE_COMMISSION_RULE",
                "CommissionRule", newRule.getId(),
                old.toString(), newRule.toString());

        return toResponse(newRule);
    }

    // ═══════════════════════════════════════════════════
    // 3. DEACTIVATE — Soft delete (hakuna kufuta kabisa)
    // ═══════════════════════════════════════════════════
    @Transactional
    public void deactivate(Long ruleId, Long userId) {
        CommissionRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule haipatikani"));

        rule.setActive(false);
        rule.setEffectiveTo(LocalDate.now());
        ruleRepository.save(rule);

        auditService.log(userId, "DEACTIVATE_COMMISSION_RULE",
                "CommissionRule", ruleId, "active=true", "active=false");
    }

    // ═══════════════════════════════════════════════════
    // 4. LOOKUP — Inaitwa na TransactionService
    // ═══════════════════════════════════════════════════
    public CommissionRule findApplicableRule(Long providerId,
            Transaction.TransactionType type,
            BigDecimal amount) {
        return findApplicableRule(providerId, type, amount, LocalDate.now());
    }

    public CommissionRule findApplicableRule(Long providerId,
            Transaction.TransactionType type,
            BigDecimal amount,
            LocalDate date) {
        return ruleRepository.findApplicable(providerId, type, amount, date)
                .orElseThrow(() -> new BusinessException(
                        "Hakuna commission rule inayolingana na kiasi " + amount
                                + " kwa provider " + providerId + " na aina " + type
                                + " kwa tarehe " + date));
    }

    // ═══════════════════════════════════════════════════
    // 5. QUERIES
    // ═══════════════════════════════════════════════════
    public List<CommissionRuleResponse> getAllActive() {
        return ruleRepository.findByActiveTrueOrderByProviderIdAscTransactionTypeAscEffectiveFromAsc()
                .stream().map(this::toResponse).toList();
    }

    public List<CommissionRuleResponse> getByProviderAndType(
            Long providerId, Transaction.TransactionType type) {
        return ruleRepository.findByProviderIdAndTransactionTypeOrderByEffectiveFromDesc(providerId, type)
                .stream().map(this::toResponse).toList();
    }

    // ═══════════════════════════════════════════════════
    // VALIDATION HELPERS
    // ═══════════════════════════════════════════════════
    private void validateRange(CommissionRuleRequest req) {
        if (req.maxAmount() != null && req.maxAmount().compareTo(req.minAmount()) < 0) {
            throw new BusinessException("maxAmount lazima iwe kubwa au sawa na minAmount");
        }
    }

    /**
     * Angalia kama range mpya inapishana na rules zilizopo
     * kwa provider na type hiyo hiyo, kwa tarehe inayolingana.
     */
    private void checkOverlap(Long providerId, Transaction.TransactionType type,
            BigDecimal min, BigDecimal max,
            LocalDate from, LocalDate to) {
        List<CommissionRule> existing = ruleRepository.findOverlapping(providerId, type, from, to);

        for (CommissionRule r : existing) {
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

    // ═══════════════════════════════════════════════════
    // MAPPER
    // ═══════════════════════════════════════════════════
    private CommissionRuleResponse toResponse(CommissionRule r) {
        LocalDate today = LocalDate.now();
        return new CommissionRuleResponse(
                r.getId(),
                r.getProvider().getId(),
                r.getProvider().getName(),
                r.getTransactionType().name(),
                r.getMinAmount(),
                r.getMaxAmount(),
                r.getNetworkCommission(),
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