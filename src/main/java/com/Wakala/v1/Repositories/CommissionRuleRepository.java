package com.Wakala.v1.Repositories;

import com.Wakala.v1.Entity.CommissionRule;
import com.Wakala.v1.Entity.Transaction;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {
    @EntityGraph(attributePaths = {"provider", "createdBy"})
    List<CommissionRule> findByProviderIdAndTransactionTypeOrderByEffectiveFromDesc(
            Long providerId, Transaction.TransactionType type);

    @EntityGraph(attributePaths = {"provider", "createdBy"})
    List<CommissionRule> findByActiveTrueOrderByProviderIdAscTransactionTypeAscEffectiveFromAsc();

    /**
     * Lookup ya msingi: tafuta rule inayolingana na kiasi, kwa tarehe fulani.
     *
     * Masharti:
     * - provider inalingana
     * - type inalingana
     * - active = true
     * - minAmount <= amount <= maxAmount (au maxAmount null)
     * - effectiveFrom <= date
     * - effectiveTo IS NULL OR effectiveTo >= date
     */
    @Query("""
        SELECT r FROM CommissionRule r
        WHERE r.provider.id = :providerId
          AND r.transactionType = :type
          AND r.active = true
          AND r.minAmount <= :amount
          AND (r.maxAmount IS NULL OR r.maxAmount >= :amount)
          AND r.effectiveFrom <= :date
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
    """)
    
    @EntityGraph(attributePaths = {"provider"})
    Optional<CommissionRule> findApplicable(
            @Param("providerId") Long providerId,
            @Param("type") Transaction.TransactionType type,
            @Param("amount") BigDecimal amount,
            @Param("date") LocalDate date);

    /**
     * Tafuta rules zote zinazopishana na range fulani
     * (kwa validation wakati wa kuunda rule mpya).
     */
    @Query("""
        SELECT r FROM CommissionRule r
        WHERE r.provider.id = :providerId
          AND r.transactionType = :type
          AND r.active = true
          AND (r.effectiveTo IS NULL OR r.effectiveTo >= :fromDate)
          AND (:toDate IS NULL OR r.effectiveFrom <= :toDate)
    """)
    List<CommissionRule> findOverlapping(
            @Param("providerId") Long providerId,
            @Param("type") Transaction.TransactionType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}