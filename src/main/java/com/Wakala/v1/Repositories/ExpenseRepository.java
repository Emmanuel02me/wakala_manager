package com.Wakala.v1.Repositories;

import com.Wakala.v1.Entity.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = { "recordedBy", "approvedBy", "session" })
    Optional<Expense> findById(Long id);

    @EntityGraph(attributePaths = { "recordedBy", "approvedBy" })
    List<Expense> findBySessionIdAndCancelledFalse(Long sessionId);

    @EntityGraph(attributePaths = { "recordedBy", "approvedBy" })
    List<Expense> findByStatusAndCancelledFalse(Expense.ExpenseStatus status);

    @EntityGraph(attributePaths = { "recordedBy", "approvedBy" })
    List<Expense> findBySessionIdAndStatusAndCancelledFalse(
            Long sessionId, Expense.ExpenseStatus status);

    @EntityGraph(attributePaths = { "recordedBy", "approvedBy" })
    List<Expense> findBySessionIdAndStatus(Long sessionId, Expense.ExpenseStatus status);
}