package com.Wakala.v1.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Wakala.v1.Dto.*;
import com.Wakala.v1.Entity.*;
import com.Wakala.v1.Exception.BusinessException;
import com.Wakala.v1.Exception.ResourceNotFoundException;
import com.Wakala.v1.Repositories.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final long EDIT_WINDOW_MINUTES = 30;

    private final TransactionRepository transactionRepository;
    private final DailySessionRepository sessionRepository;
    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;
    private final NetworkRateService networkRateService;
    private final OwnerRuleService ownerRuleService;
    private final AuditService auditService;

    private boolean isSessionActive(DailySession session) {
        return session.getStatus() == DailySession.SessionStatus.OPEN
                || session.getStatus() == DailySession.SessionStatus.REOPENED;
    }

    @Transactional
    public TransactionResponse record(TransactionRequest request, User user) {
        DailySession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session haipatikani"));

        if (!isSessionActive(session)) {
            throw new BusinessException("Kikao hakiko wazi. Hauwezi kurekodi muamala.");
        }

        Provider provider = providerRepository.findById(request.providerId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider hapatikani"));

        Provider destinationProvider = resolveDestinationProvider(request);

        if (destinationProvider != null) {
            validateTransferTypes(request.transactionType(), provider, destinationProvider);
        }

        // ── Network Commission: Auto-fetch kutoka network_rates ──
        BigDecimal networkCommission = BigDecimal.ZERO;
        BigDecimal ownerCommission = BigDecimal.ZERO;
        NetworkRate networkRate = null;
        OwnerRule ownerRule = null;

        boolean requiresNetworkRate = requiresNetworkRate(request.transactionType());

        if (requiresNetworkRate) {
            try {
                networkRate = networkRateService.findApplicableRate(
                        request.providerId(),
                        request.transactionType(),
                        request.amount());
                networkCommission = networkRate.getNetworkCommission();
            } catch (BusinessException e) {
                // Kama rate haipo, tumia 0
                networkCommission = BigDecimal.ZERO;
            }
        }

        // ── Owner Commission: LIPA pekee (au BANK_CONTROL_NUMBER kama
        // chargeOwnerCommission) ──
        if (requiresOwnerRule(request.transactionType()) ||
                isBankControlNumberWithCommission(request)) {
            try {
                ownerRule = ownerRuleService.findApplicableRule(
                        request.providerId(),
                        request.transactionType(),
                        request.amount());
                ownerCommission = ownerRule.getOwnerCommission();
            } catch (BusinessException e) {
                ownerCommission = BigDecimal.ZERO;
            }
        }

        boolean hasNetworkFee = Boolean.TRUE.equals(request.hasNetworkFee());
        boolean chargeOwnerCommission = Boolean.TRUE.equals(request.chargeOwnerCommission());

        TransactionMovement.Effect effect = TransactionMovement.calculate(
                request.transactionType(),
                request.amount(),
                networkCommission,
                ownerCommission,
                hasNetworkFee,
                chargeOwnerCommission);

        Transaction tx = Transaction.builder()
                .session(session)
                .provider(provider)
                .destinationProvider(destinationProvider)
                .transactionType(request.transactionType())
                .revenueModel(effect.revenueModel())
                .amount(request.amount())
                .networkCommission(networkCommission)
                .ownerCommission(ownerCommission)
                .floatEffect(effect.floatEffect())
                .cashEffect(effect.cashEffect())
                .networkRate(networkRate)
                .ownerRule(ownerRule)
                .customerPhone(request.customerPhone())
                .customerName(request.customerName())
                .reference(request.reference())
                .transactionTime(LocalDateTime.now())
                .recordedBy(user)
                .locked(false)
                .build();

        tx = transactionRepository.save(tx);
        auditService.log(user.getId(), "CREATE_TRANSACTION", "Transaction", tx.getId(), null, null);

        return toResponse(tx);
    }

    // ═══════════════════════════════════════════
    // RULES ZA COMMISSION
    // ═══════════════════════════════════════════

    /**
     * Transaction types zinazohitaji network rate
     */
    private boolean requiresNetworkRate(Transaction.TransactionType type) {
        return switch (type) {
            case TILL_CASH_OUT, LIPA_CASH_OUT, BANK_CASH_OUT -> true;
            default -> false;
        };
    }

    /**
     * Transaction types zinazohitaji owner rule
     * (LIPA pekee kwa sasa - wakala anaweka faida yake)
     */
    private boolean requiresOwnerRule(Transaction.TransactionType type) {
        return type == Transaction.TransactionType.LIPA_CASH_OUT;
    }

    private Provider resolveDestinationProvider(TransactionRequest request) {
        if (!requiresDestination(request.transactionType()))
            return null;

        if (request.destinationProviderId() == null) {
            throw new BusinessException(
                    "destinationProviderId inahitajika kwa muamala wa " + request.transactionType());
        }

        if (request.destinationProviderId().equals(request.providerId())) {
            throw new BusinessException("Source na destination haziwezi kuwa sawa");
        }

        return providerRepository.findById(request.destinationProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination provider hapatikani"));
    }

    private boolean requiresDestination(Transaction.TransactionType type) {
        return switch (type) {
            case TRANSFER_PROVIDER, TRANSFER_BANK_TO_FLOAT, TRANSFER_FLOAT_TO_BANK -> true;
            default -> false;
        };
    }

    private boolean isBankControlNumberWithCommission(TransactionRequest req) {
        return req.transactionType() == Transaction.TransactionType.BANK_CONTROL_NUMBER
                && Boolean.TRUE.equals(req.chargeOwnerCommission());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getBySession(Long sessionId) {
        return transactionRepository.findBySessionId(sessionId).stream()
                .map(this::toResponse).toList();
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getProvider().getName(),
                t.getDestinationProvider() != null ? t.getDestinationProvider().getName() : null,
                t.getTransactionType().name(),
                t.getRevenueModel().name(),
                t.getAmount(),
                t.getNetworkCommission(),
                t.getOwnerCommission(),
                t.getFloatEffect(),
                t.getCashEffect(),
                t.getCustomerPhone(),
                t.getCustomerName(),
                t.getTransactionTime(),
                t.isLocked());
    }

    private void validateTransferTypes(Transaction.TransactionType type,
            Provider source, Provider destination) {
        if (destination == null)
            return;

        switch (type) {
            case TRANSFER_BANK_TO_FLOAT -> {
                if (source.getType() != Provider.ProviderType.BANK) {
                    throw new BusinessException("TRANSFER_BANK_TO_FLOAT: provider (source) lazima iwe BANK");
                }
                if (destination.getType() != Provider.ProviderType.MOBILE_MONEY) {
                    throw new BusinessException("TRANSFER_BANK_TO_FLOAT: destination lazima iwe MOBILE_MONEY");
                }
            }
            case TRANSFER_FLOAT_TO_BANK -> {
                if (source.getType() != Provider.ProviderType.MOBILE_MONEY) {
                    throw new BusinessException("TRANSFER_FLOAT_TO_BANK: provider (source) lazima iwe MOBILE_MONEY");
                }
                if (destination.getType() != Provider.ProviderType.BANK) {
                    throw new BusinessException("TRANSFER_FLOAT_TO_BANK: destination lazima iwe BANK");
                }
            }
            case TRANSFER_PROVIDER -> {
                /* provider yoyote → provider yoyote */ }
            default -> {
            }
        }
    }

    @Transactional
    public TransactionResponse updateSafeFields(Long txId, UpdateTransactionRequest req, Long userId) {
        Transaction tx = transactionRepository.findById(txId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction haipatikani"));

        validateEditable(tx);

        String oldValue = String.format("customerName=%s, customerPhone=%s, reference=%s",
                tx.getCustomerName(), tx.getCustomerPhone(), tx.getReference());

        if (req.customerName() != null)
            tx.setCustomerName(req.customerName());
        if (req.customerPhone() != null)
            tx.setCustomerPhone(req.customerPhone());
        if (req.reference() != null)
            tx.setReference(req.reference());

        transactionRepository.save(tx);

        String newValue = String.format("customerName=%s, customerPhone=%s, reference=%s",
                tx.getCustomerName(), tx.getCustomerPhone(), tx.getReference());
        auditService.log(userId, "UPDATE_TRANSACTION_SAFE", "Transaction", txId, oldValue, newValue);

        return toResponse(tx);
    }

    @Transactional
    public TransactionResponse voidTransaction(Long txId, VoidTransactionRequest req, Long userId) {
        Transaction original = transactionRepository.findById(txId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction haipatikani"));

        if (original.isVoided()) {
            throw new BusinessException("Transaction hii imeshavoidiwa tayari");
        }

        if (original.getTransactionType() == Transaction.TransactionType.VOID) {
            throw new BusinessException("Hauwezi kuviodi void");
        }

        if (!isSessionActive(original.getSession())) {
            throw new BusinessException("Kikao kimefungwa. Hauwezi kuviodi transaction.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User hapatikani"));

        if (user.getRole() != User.Role.OWNER) {
            throw new BusinessException("Ni OWNER pekee anayeweza kuviodi transaction");
        }

        Transaction voidTx = Transaction.builder()
                .session(original.getSession())
                .provider(original.getProvider())
                .destinationProvider(original.getDestinationProvider())
                .transactionType(Transaction.TransactionType.VOID)
                .revenueModel(original.getRevenueModel())
                .amount(original.getAmount())
                .networkCommission(original.getNetworkCommission().negate())
                .ownerCommission(original.getOwnerCommission().negate())
                .floatEffect(original.getFloatEffect().negate())
                .cashEffect(original.getCashEffect().negate())
                .ownerRule(original.getOwnerRule())
                .networkRate(original.getNetworkRate())
                .voidOf(original)
                .voidReason(req.reason())
                .customerName(original.getCustomerName())
                .customerPhone(original.getCustomerPhone())
                .reference("VOID-" + original.getId())
                .transactionTime(LocalDateTime.now())
                .recordedBy(user)
                .build();

        original.setVoided(true);
        original.setVoidReason(req.reason());
        original.setVoidedBy(userId);
        original.setVoidedAt(LocalDateTime.now());
        original.setLocked(true);

        transactionRepository.save(original);
        voidTx = transactionRepository.save(voidTx);

        auditService.log(userId, "VOID_TRANSACTION", "Transaction", voidTx.getId(),
                original.toString(), voidTx.toString());

        return toResponse(voidTx);
    }

    private void validateEditable(Transaction tx) {
        if (!isSessionActive(tx.getSession())) {
            throw new BusinessException("Kikao kimefungwa. Hauwezi kuedit.");
        }
        if (tx.isLocked())
            throw new BusinessException("Transaction imefungwa.");
        if (tx.isVoided())
            throw new BusinessException("Transaction imeshavoidiwa.");

        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(
                tx.getCreatedAt(), LocalDateTime.now());
        if (minutes > EDIT_WINDOW_MINUTES) {
            throw new BusinessException("Muda wa kuedit (dakika " + EDIT_WINDOW_MINUTES + ") umepita.");
        }
    }
}