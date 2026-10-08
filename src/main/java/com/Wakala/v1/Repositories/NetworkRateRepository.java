package com.Wakala.v1.Repositories;

import com.Wakala.v1.Entity.NetworkRate;
import com.Wakala.v1.Entity.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NetworkRateRepository extends JpaRepository<NetworkRate, Long> {

    @EntityGraph(attributePaths = { "provider" })
    List<NetworkRate> findByActiveTrueOrderByProviderIdAscTransactionTypeAscMinAmountAsc();

    @EntityGraph(attributePaths = { "provider" })
    List<NetworkRate> findByProviderIdAndTransactionTypeAndActiveTrueOrderByMinAmountAsc(
            Long providerId, Transaction.TransactionType type);

    @Query("""
                SELECT r FROM NetworkRate r
                WHERE r.provider.id = :providerId
                  AND r.transactionType = :type
                  AND r.active = true
                  AND r.minAmount <= :amount
                  AND (r.maxAmount IS NULL OR r.maxAmount >= :amount)
                  AND r.effectiveFrom <= :date
                  AND (r.effectiveTo IS NULL OR r.effectiveTo >= :date)
            """)
    Optional<NetworkRate> findApplicable(
            @Param("providerId") Long providerId,
            @Param("type") Transaction.TransactionType type,
            @Param("amount") BigDecimal amount,
            @Param("date") LocalDate date);
}