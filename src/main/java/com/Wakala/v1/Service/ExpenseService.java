package com.Wakala.v1.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Wakala.v1.Dto.ApproveExpenseRequest;
import com.Wakala.v1.Dto.CancelExpenseRequest;
import com.Wakala.v1.Dto.ExpenseRequest;
import com.Wakala.v1.Dto.ExpenseResponse;
import com.Wakala.v1.Entity.DailySession;
import com.Wakala.v1.Entity.Expense;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Exception.BusinessException;
import com.Wakala.v1.Exception.ResourceNotFoundException;
import com.Wakala.v1.Repositories.DailySessionRepository;
import com.Wakala.v1.Repositories.ExpenseRepository;
import com.Wakala.v1.Repositories.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final BigDecimal EMPLOYEE_LIMIT = new BigDecimal("20000");

    private final ExpenseRepository expenseRepository;
    private final DailySessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ✅ Helper: kikao active (OPEN au REOPENED)
    private boolean isSessionActive(DailySession session) {
        return session.getStatus() == DailySession.SessionStatus.OPEN
                || session.getStatus() == DailySession.SessionStatus.REOPENED;
    }

    @Transactional
    public ExpenseResponse record(ExpenseRequest req, User user) {
        DailySession session = sessionRepository.findById(req.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session haipatikani"));

        if (!isSessionActive(session)) {
            throw new BusinessException("Kikao hakiko wazi.");
        }

        Expense.ExpenseStatus status = Expense.ExpenseStatus.APPROVED;
        User approver = user;
        LocalDateTime approvedAt = LocalDateTime.now();

        if (user.getRole() == User.Role.EMPLOYEE
                && req.amount().compareTo(EMPLOYEE_LIMIT) > 0) {
            status = Expense.ExpenseStatus.PENDING;
            approver = null;
            approvedAt = null;
        }

        Expense e = Expense.builder()
                .session(session)
                .description(req.description())
                .amount(req.amount())
                .category(req.category())
                .status(status)
                .recordedBy(user)
                .approvedBy(approver)
                .approvedAt(approvedAt)
                .receiptNumber(req.receiptNumber())
                .build();

        e = expenseRepository.save(e);
        auditService.log(user.getId(), "CREATE_EXPENSE", "Expense", e.getId(), null, null);
        return toResponse(e);
    }

    @Transactional
    public ExpenseResponse approve(ApproveExpenseRequest req, Long ownerId) {
        Expense e = expenseRepository.findById(req.expenseId())
                .orElseThrow(() -> new ResourceNotFoundException("Expense haipatikani"));

        if (e.getStatus() != Expense.ExpenseStatus.PENDING) {
            throw new BusinessException("Expense hii si PENDING");
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User hapatikani"));

        if (owner.getRole() != User.Role.OWNER) {
            throw new BusinessException("Ni OWNER pekee anayeweza kuidhinisha gharama");
        }

        e.setStatus(req.approved() ? Expense.ExpenseStatus.APPROVED : Expense.ExpenseStatus.REJECTED);
        e.setApprovedBy(owner);
        e.setApprovedAt(LocalDateTime.now());
        expenseRepository.save(e);

        auditService.log(ownerId,
                req.approved() ? "APPROVE_EXPENSE" : "REJECT_EXPENSE",
                "Expense", e.getId(), null, null);
        return toResponse(e);
    }

    public List<ExpenseResponse> getPending() {
        return expenseRepository.findByStatusAndCancelledFalse(Expense.ExpenseStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    public List<ExpenseResponse> getBySession(Long sessionId) {
        return expenseRepository.findBySessionIdAndCancelledFalse(sessionId)
                .stream().map(this::toResponse).toList();
    }

    private ExpenseResponse toResponse(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getDescription(),
                e.getAmount(),
                e.getCategory(),
                e.getStatus().name(),
                e.getCreatedAt());
    }

    // ═══════════════════════════════════════════
    // CANCEL (soft delete)
    // ═══════════════════════════════════════════
    @Transactional
    public void cancel(Long expenseId, CancelExpenseRequest req, Long userId) {
        Expense e = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense haipatikani"));

        if (e.isCancelled()) {
            throw new BusinessException("Gharama hii imeshafutwa tayari");
        }

        // Load user MARA MOJA
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User hapatikani"));

        boolean isOwner = user.getRole() == User.Role.OWNER;
        boolean isRecorder = e.getRecordedBy().getId().equals(userId);

        if (!isOwner && !isRecorder) {
            throw new BusinessException("Huna ruhusa kufuta gharama hii");
        }

        DailySession session = e.getSession();
        boolean isReopened = session.getStatus() == DailySession.SessionStatus.REOPENED;

        // Kikao lazima kiwe active (OPEN au REOPENED)
        if (!isSessionActive(session)) {
            throw new BusinessException("Kikao kimefungwa. Hauwezi kufuta gharama.");
        }

        // APPROVED expense inaweza kufutwa PEKEE kama kikao ni REOPENED na ni OWNER
        if (e.getStatus() == Expense.ExpenseStatus.APPROVED) {
            if (!isReopened) {
                throw new BusinessException(
                        "Hauwezi kufuta gharama iliyoidhinishwa. Fungua kikao tena ili kuifuta.");
            }
            if (!isOwner) {
                throw new BusinessException(
                        "Ni OWNER pekee anayeweza kufuta gharama iliyoidhinishwa kwenye kikao kilichofunguliwa tena.");
            }
        }

        // Soft delete
        e.setCancelled(true);
        e.setCancelledAt(LocalDateTime.now());
        e.setCancelledBy(userId);
        e.setCancelReason(req.reason());
        expenseRepository.save(e);

        String oldValue = String.format(
                "amount=%s, description=%s, category=%s, status=%s",
                e.getAmount(), e.getDescription(), e.getCategory(), e.getStatus());
        auditService.log(userId, "CANCEL_EXPENSE", "Expense", expenseId, oldValue, req.reason());
    }
}