package com.Wakala.v1.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Wakala.v1.Dto.*;
import com.Wakala.v1.Entity.DailySession;
import com.Wakala.v1.Entity.Expense;
import com.Wakala.v1.Entity.FloatBalance;
import com.Wakala.v1.Entity.Provider;
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

        // Zuia kama kuna OPEN au REOPENED
        sessionRepository.findBySessionDateAndStatus(today, DailySession.SessionStatus.OPEN)
                .ifPresent(s -> {
                    throw new BusinessException("Kuna kikao wazi tayari leo");
                });

        sessionRepository.findBySessionDateAndStatus(today, DailySession.SessionStatus.REOPENED)
                .ifPresent(s -> {
                    throw new BusinessException(
                            "Kuna kikao kilichofunguliwa tena leo. Kifunge kwanza kabla ya kufungua kipya.");
                });

        Optional<DailySession> lastClosedOpt = sessionRepository
                .findTopByStatusOrderBySessionDateDesc(DailySession.SessionStatus.CLOSED);

        boolean hasAdjustment = false;
        BigDecimal adjustmentTotal = BigDecimal.ZERO;
        StringBuilder adjustmentDetails = new StringBuilder();

        if (lastClosedOpt.isPresent()) {
            DailySession lastClosed = lastClosedOpt.get();

            // Cash adjustment
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

            // Float adjustments
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

        // Kama kuna adjustment kubwa, lazima kuwe na note
        if (hasAdjustment && adjustmentTotal.compareTo(new BigDecimal("10000")) > 0) {
            if (req.adjustmentNote() == null || req.adjustmentNote().isBlank()) {
                throw new BusinessException(
                        "Kuna tofauti ya " + adjustmentTotal + " kati ya jana na leo. " +
                                "Tafadhali toa sababu (adjustmentNote).");
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
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            return "+" + diff;
        }
        return diff.toString();
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

        return new SessionResponse(
                s.getId(), s.getSessionDate(), s.getStatus().name(),
                s.getOpeningCash(), s.getClosingCash(),
                s.getOpenedAt(), s.getClosedAt(),
                s.getOpenedBy() != null ? s.getOpenedBy().getFullName() : null,
                s.getClosedBy() != null ? s.getClosedBy().getFullName() : null,
                s.isHasOpeningAdjustment(),
                s.getOpeningAdjustmentNote(),
                s.getAdjustmentTotal(),
                txCount);
    }

    @Transactional(readOnly = true)
    public LastClosedSessionResponse getLastClosedSession() {
        Optional<DailySession> lastClosed = sessionRepository
                .findTopByStatusOrderBySessionDateDesc(DailySession.SessionStatus.CLOSED);

        if (lastClosed.isEmpty()) {
            return new LastClosedSessionResponse(false, null, null, null, List.of());
        }

        DailySession session = lastClosed.get();
        List<FloatBalance> floats = floatBalanceRepository.findBySessionId(session.getId());

        List<LastClosedSessionResponse.FloatClosingInfo> closings = floats.stream()
                .map(fb -> new LastClosedSessionResponse.FloatClosingInfo(
                        fb.getProvider().getId(),
                        fb.getProvider().getName(),
                        fb.getClosingBalance()))
                .toList();

        return new LastClosedSessionResponse(
                true,
                session.getId(),
                session.getSessionDate(),
                session.getClosingCash(),
                closings);
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

        // Angalia gharama zilizosubiri
        List<Expense> pendingExpenses = expenseRepository.findBySessionIdAndStatus(
                sessionId, Expense.ExpenseStatus.PENDING);
        if (!pendingExpenses.isEmpty()) {
            throw new BusinessException(
                    "Kuna gharama " + pendingExpenses.size() + " ambazo hazijaidhinishwa. " +
                            "Zihidinishe kwanza kabla ya kufunga kikao.");
        }

        // Weka closing balance kwa kila provider
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
    // TODAY'S SESSION (OPEN au REOPENED)
    // ═══════════════════════════════════════════
    @Transactional(readOnly = true)
    public SessionResponse getTodaySession() {
        LocalDate today = LocalDate.now();

        Optional<DailySession> open = sessionRepository
                .findBySessionDateAndStatus(today, DailySession.SessionStatus.OPEN);
        if (open.isPresent()) {
            return toResponse(open.get());
        }

        Optional<DailySession> reopened = sessionRepository
                .findBySessionDateAndStatus(today, DailySession.SessionStatus.REOPENED);
        if (reopened.isPresent()) {
            return toResponse(reopened.get());
        }

        return null;
    }
}