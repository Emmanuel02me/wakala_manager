package com.Wakala.v1.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Wakala.v1.Dto.*;
import com.Wakala.v1.Entity.DailySession;
import com.Wakala.v1.Entity.Expense;
import com.Wakala.v1.Entity.FloatBalance;
import com.Wakala.v1.Entity.Provider;
import com.Wakala.v1.Entity.RevenueModel;
import com.Wakala.v1.Entity.Transaction;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Exception.BusinessException;
import com.Wakala.v1.Exception.ResourceNotFoundException;
import com.Wakala.v1.Repositories.DailySessionRepository;
import com.Wakala.v1.Repositories.ExpenseRepository;
import com.Wakala.v1.Repositories.FloatBalanceRepository;
import com.Wakala.v1.Repositories.ProviderRepository;
import com.Wakala.v1.Repositories.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final DailySessionRepository sessionRepository;
    private final FloatBalanceRepository floatBalanceRepository;
    private final ProviderRepository providerRepository;
    private final ExpenseRepository expenseRepository;
    private final ReconciliationService reconciliationService;
    private final AuditService auditService;
    private final TransactionRepository transactionRepository;

    // ═══════════════════════════════════════════
    // OPEN SESSION
    // ═══════════════════════════════════════════
    @Transactional
    public SessionResponse openSession(OpenSessionRequest req, User user) {
        LocalDate today = LocalDate.now();

        // ✅ FIX: Kagua kikao CHOCHOTE kilicho wazi (sio leo tu)
        List<DailySession> openSessions = sessionRepository.findAllOpen();
        if (!openSessions.isEmpty()) {
            DailySession existing = openSessions.get(0);
            long daysOld = java.time.temporal.ChronoUnit.DAYS.between(
                    existing.getSessionDate(), today);

            String msg;
            if (daysOld == 0) {
                msg = "Kuna kikao wazi tayari leo. Kifunge kwanza kabla ya kufungua kipya.";
            } else if (daysOld == 1) {
                msg = "Kuna kikao wazi tangu jana (" + existing.getSessionDate() + "). "
                        + "Kifunge kwanza kabla ya kufungua kipya.";
            } else {
                msg = "Kuna kikao wazi tangu " + existing.getSessionDate()
                        + " (siku " + daysOld + " zilizopita). "
                        + "Kifunge kwanza kabla ya kufungua kipya. "
                        + "Kama kikao hiki kimesahaulika, wasiliana na OWNER kukifunga kwa mkono.";
            }
            throw new BusinessException(msg);
        }

        Optional<DailySession> lastClosedOpt = sessionRepository
                .findTopByStatusOrderBySessionDateDesc(DailySession.SessionStatus.CLOSED);

        boolean hasAdjustment = false;
        BigDecimal adjustmentTotal = BigDecimal.ZERO;
        StringBuilder adjustmentDetails = new StringBuilder();

        if (lastClosedOpt.isPresent()) {
            DailySession lastClosed = lastClosedOpt.get();

            BigDecimal cashDiff = req.openingCash().subtract(lastClosed.getClosingCash());
            if (cashDiff.compareTo(BigDecimal.ZERO) != 0) {
                hasAdjustment = true;
                adjustmentTotal = adjustmentTotal.add(cashDiff.abs());
                adjustmentDetails.append("Cash: ")
                        .append(formatDiff(cashDiff))
                        .append(" (jana ")
                        .append(lastClosed.getClosingCash())
                        .append(" → leo ")
                        .append(req.openingCash())
                        .append(")\n");
            }

            List<FloatBalance> lastFloats = floatBalanceRepository
                    .findBySessionId(lastClosed.getId());

            for (FloatOpeningRequest fo : req.floatOpenings()) {
                for (FloatBalance lf : lastFloats) {
                    if (lf.getProvider().getId().equals(fo.providerId())) {
                        BigDecimal diff = fo.openingBalance().subtract(lf.getClosingBalance());
                        if (diff.compareTo(BigDecimal.ZERO) != 0) {
                            hasAdjustment = true;
                            adjustmentTotal = adjustmentTotal.add(diff.abs());
                            adjustmentDetails.append(lf.getProvider().getName())
                                    .append(": ")
                                    .append(formatDiff(diff))
                                    .append(" (jana ")
                                    .append(lf.getClosingBalance())
                                    .append(" → leo ")
                                    .append(fo.openingBalance())
                                    .append(")\n");
                        }
                        break;
                    }
                }
            }
        }

        if (hasAdjustment && adjustmentTotal.compareTo(new BigDecimal("10000")) > 0) {
            if (req.adjustmentNote() == null || req.adjustmentNote().isBlank()) {
                throw new BusinessException(
                        "Kuna tofauti ya " + adjustmentTotal + " kati ya jana na leo. "
                                + "Tafadhali toa sababu (adjustmentNote).");
            }
        }

        DailySession session = DailySession.builder()
                .sessionDate(today)
                .openedBy(user)
                .openedAt(LocalDateTime.now())
                .status(DailySession.SessionStatus.OPEN)
                .openingCash(req.openingCash())
                .hasOpeningAdjustment(hasAdjustment)
                .openingAdjustmentNote(req.adjustmentNote())
                .adjustmentTotal(hasAdjustment ? adjustmentTotal : null)
                .notes(req.notes())
                .build();
        session = sessionRepository.save(session);

        for (FloatOpeningRequest f : req.floatOpenings()) {
            Provider p = providerRepository.findById(f.providerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Provider hapatikani: " + f.providerId()));
            FloatBalance fb = FloatBalance.builder()
                    .session(session)
                    .provider(p)
                    .openingBalance(f.openingBalance())
                    .build();
            floatBalanceRepository.save(fb);
        }

        auditService.log(user.getId(), "OPEN_SESSION", "DailySession", session.getId(),
                null, hasAdjustment ? adjustmentDetails.toString() : null);

        return toResponse(session);
    }

    private String formatDiff(BigDecimal diff) {
        if (diff.compareTo(BigDecimal.ZERO) > 0)
            return "+" + diff;
        return diff.toString();
    }

    // ═══════════════════════════════════════════════════════════
    // ONGEZA FLOAT KATIKATI YA KIKAO
    // ═══════════════════════════════════════════════════════════
    @Transactional
    public SessionResponse addFloatToOpenSession(Long sessionId, AddFloatRequest req, User user) {
        DailySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session haipatikani"));

        if (session.getStatus() != DailySession.SessionStatus.OPEN
                && session.getStatus() != DailySession.SessionStatus.REOPENED) {
            throw new BusinessException("Kikao hakiko wazi — huwezi kuongeza float");
        }

        Provider provider = providerRepository.findById(req.providerId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider haipatikani"));

        long txCount = transactionRepository.countBySessionIdAndProviderIdOrDestination(
                sessionId, req.providerId());

        Optional<FloatBalance> existingFb = floatBalanceRepository
                .findBySessionIdAndProviderId(sessionId, req.providerId());

        // ═══════════════════════════════════════════════════════════
        // MODE A: ALREADY HAD — opening correction (Total inaongezeka)
        // ═══════════════════════════════════════════════════════════
        if (req.sourceType() == AddFloatRequest.FloatSourceType.ALREADY_HAD) {
            if (txCount > 0) {
                throw new BusinessException(
                        provider.getName() + " tayari ina miamala " + txCount + " leo. "
                                + "Huwezi kuiweka kama 'Nilikuwa nayo' — hii ingeharibu hesabu. "
                                + "Tumia 'Nimetumia cash' au 'Nimetumia provider mwingine' badala yake.");
            }

            if (existingFb.isPresent()) {
                BigDecimal oldBalance = existingFb.get().getOpeningBalance();
                existingFb.get().setOpeningBalance(req.openingBalance());
                existingFb.get().setAdjustmentNote(req.note());
                floatBalanceRepository.save(existingFb.get());

                auditService.log(user.getId(), "ADJUST_OPENING_FLOAT", "FloatBalance",
                        existingFb.get().getId(),
                        oldBalance.toPlainString(),
                        req.openingBalance().toPlainString() + " — " + req.note());
            } else {
                FloatBalance fb = FloatBalance.builder()
                        .session(session)
                        .provider(provider)
                        .openingBalance(req.openingBalance())
                        .adjustmentNote(req.note())
                        .build();
                fb = floatBalanceRepository.save(fb);

                auditService.log(user.getId(), "ADD_OPENING_FLOAT", "FloatBalance",
                        fb.getId(), null,
                        req.openingBalance().toPlainString() + " — " + req.note());
            }

            return toResponse(session);
        }

        // ═══════════════════════════════════════════════════════════
        // MODE B/C: TRANSFER — create transaction (Total haibadiliki)
        // ═══════════════════════════════════════════════════════════
        if (existingFb.isPresent()
                && existingFb.get().getOpeningBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                    provider.getName() + " tayari ina opening ya TZS "
                            + existingFb.get().getOpeningBalance().toPlainString() + " kwenye kikao. "
                            + "Badilisha opening yake badala ya kuongeza kama transfer.");
        }

        FloatBalance fb = existingFb.orElseGet(() -> {
            FloatBalance newFb = FloatBalance.builder()
                    .session(session)
                    .provider(provider)
                    .openingBalance(BigDecimal.ZERO)
                    .adjustmentNote(req.note())
                    .build();
            return floatBalanceRepository.save(newFb);
        });

        // ─── FROM_CASH: FLOAT_TOPUP kutoka cash ───
        if (req.sourceType() == AddFloatRequest.FloatSourceType.FROM_CASH) {
            Transaction tx = Transaction.builder()
                    .session(session)
                    .provider(provider)
                    .transactionType(Transaction.TransactionType.FLOAT_TOPUP)
                    .revenueModel(RevenueModel.NONE)
                    .amount(req.openingBalance())
                    .networkCommission(BigDecimal.ZERO)
                    .ownerCommission(BigDecimal.ZERO)
                    .floatEffect(req.openingBalance())
                    .cashEffect(req.openingBalance().negate())
                    .transactionTime(LocalDateTime.now())
                    .recordedBy(user)
                    .reference("Float iliyoongezwa katikati ya kikao: " + req.note())
                    .build();
            tx = transactionRepository.save(tx);

            auditService.log(user.getId(), "ADD_FLOAT_FROM_CASH", "Transaction",
                    tx.getId(), null,
                    provider.getName() + ": +" + req.openingBalance().toPlainString()
                            + " (cash: -" + req.openingBalance().toPlainString() + ") — " + req.note());
        }

        // ─── FROM_PROVIDER: TRANSFER_PROVIDER kutoka provider mwingine ───
        else if (req.sourceType() == AddFloatRequest.FloatSourceType.FROM_PROVIDER) {
            if (req.sourceProviderId() == null) {
                throw new BusinessException("Provider chanzo kinahitajika kwa 'Nimetumia provider mwingine'");
            }
            if (req.sourceProviderId().equals(req.providerId())) {
                throw new BusinessException("Hauwezi kuhamisha provider kwenda kwake mwenyewe");
            }

            Provider sourceProvider = providerRepository.findById(req.sourceProviderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Provider chanzo haipatikani"));

            if (!floatBalanceRepository.existsBySessionIdAndProviderId(sessionId, sourceProvider.getId())) {
                throw new BusinessException(
                        "Provider chanzo " + sourceProvider.getName()
                                + " haipo kwenye kikao hiki. Ongeza kwanza.");
            }

            Transaction tx = Transaction.builder()
                    .session(session)
                    .provider(sourceProvider)
                    .destinationProvider(provider)
                    .transactionType(Transaction.TransactionType.TRANSFER_PROVIDER)
                    .revenueModel(RevenueModel.NONE)
                    .amount(req.openingBalance())
                    .networkCommission(BigDecimal.ZERO)
                    .ownerCommission(BigDecimal.ZERO)
                    .floatEffect(req.openingBalance().negate())
                    .cashEffect(BigDecimal.ZERO)
                    .transactionTime(LocalDateTime.now())
                    .recordedBy(user)
                    .reference("Float transfer: " + sourceProvider.getName()
                            + " → " + provider.getName() + " — " + req.note())
                    .build();
            tx = transactionRepository.save(tx);

            auditService.log(user.getId(), "ADD_FLOAT_FROM_PROVIDER", "Transaction",
                    tx.getId(), null,
                    sourceProvider.getName() + " → " + provider.getName()
                            + ": " + req.openingBalance().toPlainString() + " — " + req.note());
        }

        return toResponse(session);
    }

    // ═══════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════
    public DailySession findById(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session haipatikani: " + id));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getAll() {
        return sessionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse getById(Long id) {
        return toResponse(findById(id));
    }

    private SessionResponse toResponse(DailySession s) {
        int txCount = transactionRepository.findBySessionId(s.getId()).size();

        List<FloatBalance> floats = floatBalanceRepository.findBySessionId(s.getId());
        BigDecimal openingTotal = s.getOpeningCash();
        for (FloatBalance fb : floats) {
            openingTotal = openingTotal.add(fb.getOpeningBalance());
        }

        BigDecimal closingTotal = null;
        if (s.getClosingCash() != null) {
            boolean allClosed = floats.stream().allMatch(fb -> fb.getClosingBalance() != null);
            if (allClosed) {
                closingTotal = s.getClosingCash();
                for (FloatBalance fb : floats) {
                    closingTotal = closingTotal.add(fb.getClosingBalance());
                }
            }
        }

        return new SessionResponse(
                s.getId(), s.getSessionDate(), s.getStatus().name(),
                s.getOpeningCash(), s.getClosingCash(),
                s.getOpenedAt(), s.getClosedAt(),
                s.getOpenedBy() != null ? s.getOpenedBy().getFullName() : null,
                s.getClosedBy() != null ? s.getClosedBy().getFullName() : null,
                s.isHasOpeningAdjustment(),
                s.getOpeningAdjustmentNote(),
                s.getAdjustmentTotal(),
                txCount,
                openingTotal,
                closingTotal);
    }

    @Transactional(readOnly = true)
    public LastClosedSessionResponse getLastClosedSession() {
        Optional<DailySession> lastClosed = sessionRepository
                .findTopByStatusOrderBySessionDateDesc(DailySession.SessionStatus.CLOSED);

        if (lastClosed.isEmpty()) {
            return new LastClosedSessionResponse(
                    false, null, null, null, List.of(),
                    null, null, null, "PENDING", false);
        }

        DailySession session = lastClosed.get();
        List<FloatBalance> floats = floatBalanceRepository.findBySessionId(session.getId());

        BigDecimal closingTotal = session.getClosingCash();
        BigDecimal totalFloatDiff = BigDecimal.ZERO;
        boolean hasFloatDiff = false;

        ReconciliationResponse recon = reconciliationService.calculate(session.getId());

        List<LastClosedSessionResponse.FloatClosingInfo> closings = floats.stream()
                .map(fb -> {
                    var providerRecon = recon.providers().stream()
                            .filter(p -> p.providerId().equals(fb.getProvider().getId()))
                            .findFirst().orElse(null);
                    BigDecimal floatDiff = providerRecon != null ? providerRecon.floatDifference() : null;
                    return new LastClosedSessionResponse.FloatClosingInfo(
                            fb.getProvider().getId(),
                            fb.getProvider().getName(),
                            fb.getClosingBalance(),
                            floatDiff);
                })
                .toList();

        for (var closing : closings) {
            if (closing.closingBalance() != null) {
                closingTotal = closingTotal.add(closing.closingBalance());
            }
            if (closing.floatDifference() != null
                    && closing.floatDifference().compareTo(BigDecimal.ZERO) != 0) {
                totalFloatDiff = totalFloatDiff.add(closing.floatDifference());
                hasFloatDiff = true;
            }
        }

        BigDecimal totalFloatDiffFinal = hasFloatDiff ? totalFloatDiff : BigDecimal.ZERO;
        boolean hadIssues = !"BALANCED".equals(recon.overallStatus());

        return new LastClosedSessionResponse(
                true,
                session.getId(),
                session.getSessionDate(),
                session.getClosingCash(),
                closings,
                closingTotal,
                recon.cashDifference(),
                totalFloatDiffFinal,
                recon.overallStatus(),
                hadIssues);
    }

    // ═══════════════════════════════════════════
    // CLOSE SESSION
    // ═══════════════════════════════════════════
    @Transactional
    public ReconciliationResponse closeSession(Long sessionId, CloseSessionRequest req, User user) {
        DailySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session haipatikani"));

        if (session.getStatus() != DailySession.SessionStatus.OPEN
                && session.getStatus() != DailySession.SessionStatus.REOPENED) {
            throw new BusinessException("Kikao hakiko wazi au hakijafunguliwa tena.");
        }

        List<Expense> pendingExpenses = expenseRepository.findBySessionIdAndStatus(
                sessionId, Expense.ExpenseStatus.PENDING);
        if (!pendingExpenses.isEmpty()) {
            throw new BusinessException(
                    "Kuna gharama " + pendingExpenses.size() + " ambazo hazijaidhinishwa. "
                            + "Zihidinishe kwanza kabla ya kufunga kikao.");
        }

        for (FloatClosingRequest fc : req.floatClosings()) {
            FloatBalance fb = floatBalanceRepository
                    .findBySessionIdAndProviderId(sessionId, fc.providerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "FloatBalance haipatikani kwa provider: " + fc.providerId()));
            fb.setClosingBalance(fc.closingBalance());
            floatBalanceRepository.save(fb);
        }

        session.setClosingCash(req.closingCash());
        session.setStatus(DailySession.SessionStatus.CLOSED);
        session.setClosedBy(user);
        session.setClosedAt(LocalDateTime.now());
        if (req.notes() != null) {
            session.setNotes(req.notes());
        }
        sessionRepository.save(session);

        transactionRepository.lockAllBySessionId(sessionId);

        auditService.log(user.getId(), "CLOSE_SESSION", "DailySession", sessionId, null, null);

        return reconciliationService.calculate(sessionId);
    }

    // ═══════════════════════════════════════════════════════════
    // FORCE CLOSE — kwa OWNER pekee (kikao kilichokwama)
    // ═══════════════════════════════════════════════════════════
    @Transactional
    public ReconciliationResponse forceCloseSession(Long sessionId, CloseSessionRequest req, User user) {
        DailySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session haipatikani"));

        if (user.getRole() != User.Role.OWNER) {
            throw new BusinessException("Ni OWNER pekee anayeweza kufunga kikao kwa mkono");
        }

        if (session.getStatus() == DailySession.SessionStatus.CLOSED) {
            throw new BusinessException("Kikao hiki kimefungwa tayari");
        }

        // Hifadhi status ya awali kwa ajili ya audit
        DailySession.SessionStatus originalStatus = session.getStatus();

        List<Expense> pendingExpenses = expenseRepository.findBySessionIdAndStatus(
                sessionId, Expense.ExpenseStatus.PENDING);
        if (!pendingExpenses.isEmpty()) {
            throw new BusinessException(
                    "Kuna gharama " + pendingExpenses.size() + " ambazo hazijaidhinishwa. "
                            + "Zihidinishe kwanza.");
        }

        for (FloatClosingRequest fc : req.floatClosings()) {
            FloatBalance fb = floatBalanceRepository
                    .findBySessionIdAndProviderId(sessionId, fc.providerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "FloatBalance haipatikani kwa provider: " + fc.providerId()));
            fb.setClosingBalance(fc.closingBalance());
            floatBalanceRepository.save(fb);
        }

        session.setClosingCash(req.closingCash());
        session.setStatus(DailySession.SessionStatus.CLOSED);
        session.setClosedBy(user);
        session.setClosedAt(LocalDateTime.now());
        if (req.notes() != null) {
            session.setNotes(req.notes());
        }
        sessionRepository.save(session);

        transactionRepository.lockAllBySessionId(sessionId);

        auditService.log(user.getId(), "FORCE_CLOSE_SESSION", "DailySession", sessionId,
                "Was " + originalStatus + " since " + session.getSessionDate(),
                "Force-closed by OWNER. Closing cash: " + req.closingCash());

        return reconciliationService.calculate(sessionId);
    }

    // ═══════════════════════════════════════════
    // REOPEN SESSION
    // ═══════════════════════════════════════════
    @Transactional
    public SessionResponse reopenSession(Long sessionId, String reason, User user) {
        DailySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session haipatikani"));

        if (user.getRole() != User.Role.OWNER) {
            throw new BusinessException("Ni OWNER pekee anayeweza kufungua kikao tena");
        }

        if (session.getStatus() != DailySession.SessionStatus.CLOSED) {
            throw new BusinessException("Kikao hiki hakijafungwa (au kimeshafunguliwa)");
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Sababu inahitajika ili kufungua kikao tena");
        }

        session.setStatus(DailySession.SessionStatus.REOPENED);
        session.setReopenCount(session.getReopenCount() + 1);
        session.setLastReopenReason(reason);
        session.setLastReopenedBy(user.getId());
        session.setLastReopenedAt(LocalDateTime.now());
        sessionRepository.save(session);

        transactionRepository.unlockAllBySessionId(sessionId);

        auditService.log(user.getId(), "REOPEN_SESSION", "DailySession", sessionId,
                null, reason);

        return toResponse(session);
    }

    // ═══════════════════════════════════════════
    // TODAY'S SESSION
    // ═══════════════════════════════════════════
    @Transactional(readOnly = true)
    public SessionResponse getTodaySession() {
        LocalDate today = LocalDate.now();

        Optional<DailySession> open = sessionRepository
                .findBySessionDateAndStatus(today, DailySession.SessionStatus.OPEN);
        if (open.isPresent())
            return toResponse(open.get());

        Optional<DailySession> reopened = sessionRepository
                .findBySessionDateAndStatus(today, DailySession.SessionStatus.REOPENED);
        if (reopened.isPresent())
            return toResponse(reopened.get());

        return null;
    }

    // ═══════════════════════════════════════════════════════════
    // kikao CHOCHOTE kilicho wazi (hata cha siku zilizopita)
    // ═══════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public SessionResponse getAnyOpenSession() {
        List<DailySession> open = sessionRepository.findAllOpen();
        if (open.isEmpty()) {
            return null;
        }
        return toResponse(open.get(0));
    }
}