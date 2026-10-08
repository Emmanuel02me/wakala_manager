package com.Wakala.v1.Repositories;

import com.Wakala.v1.Entity.OwnerRule;
import com.Wakala.v1.Entity.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OwnerRuleRepository extends JpaRepository<OwnerRule, Long> {

    @EntityGraph(attributePaths = { "provider", "createdBy" })
    List<OwnerRule> findByActiveTrueOrderByProviderIdAscMinAmountAsc();

    @EntityGraph(attributePaths = { "provider", "createdBy" })
    List<OwnerRule> findByProviderIdAndTransactionTypeOrderByEffectiveFromDesc(
            Long providerId, Transaction.TransactionType type);

    @Query("""
                SELECT r FROM OwnerRule r
                WHERE r.provider.id = :providerId
                  AND r.transactionType = :type
                  AND r.active = true
                  AND r.minAmount <= :amount
                  AND (r.maxAmount IS NULL OR r.maxAmount >= :amount)
                  AND r.effectiveFrom <= :date
                  AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
            """)
    Optional<OwnerRule> findApplicable(
            @Param("providerId") Long providerId,
            @Param("type") Transaction.TransactionType type,
            @Param("amount") BigDecimal amount,
            @Param("date") LocalDate date);

    @Query("""
                SELECT r FROM OwnerRule r
                WHERE r.provider.id = :providerId
                  AND r.transactionType = :type
                  AND r.active = true
                  AND (r.effectiveTo IS NULL OR r.effectiveTo >= :fromDate)
            """)
    List<OwnerRule> findOverlapping(
            @Param("providerId") Long providerId,
            @Param("type") Transaction.TransactionType type,
            @Param("fromDate") LocalDate fromDate);
}