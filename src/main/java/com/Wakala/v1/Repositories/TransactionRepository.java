package com.Wakala.v1.Repositories;

import com.Wakala.v1.Entity.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = {
            "provider", "destinationProvider", "recordedBy",
            "ownerRule", "networkRate", "voidOf"
    })
    Optional<Transaction> findById(Long id);

    @EntityGraph(attributePaths = {
            "provider", "destinationProvider", "recordedBy",
            "ownerRule", "networkRate", "voidOf"
    })
    List<Transaction> findBySessionId(Long sessionId);

    @EntityGraph(attributePaths = { "provider", "destinationProvider", "recordedBy" })
    List<Transaction> findBySessionIdAndProviderId(Long sessionId, Long providerId);

    @EntityGraph(attributePaths = { "provider", "destinationProvider", "recordedBy" })
    List<Transaction> findBySessionIdAndTransactionType(Long sessionId, Transaction.TransactionType type);

    // ✅ MPYA — hesabu miamala ya provider (kama source AU destination)
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.session.id = :sessionId " +
           "AND (t.provider.id = :providerId OR t.destinationProvider.id = :providerId)")
    long countBySessionIdAndProviderIdOrDestination(
            @Param("sessionId") Long sessionId,
            @Param("providerId") Long providerId);

    @Modifying
    @Query("UPDATE Transaction t SET t.locked = true WHERE t.session.id = :sessionId")
    void lockAllBySessionId(@Param("sessionId") Long sessionId);

    @Modifying
    @Query("UPDATE Transaction t SET t.locked = false WHERE t.session.id = :sessionId")
    void unlockAllBySessionId(@Param("sessionId") Long sessionId);
}