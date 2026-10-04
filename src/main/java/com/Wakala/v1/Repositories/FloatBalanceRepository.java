package com.Wakala.v1.Repositories;

import com.Wakala.v1.Entity.FloatBalance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;


public interface FloatBalanceRepository extends JpaRepository<FloatBalance, Long> {

    @EntityGraph(attributePaths = {"provider"})
    List<FloatBalance> findBySessionId(Long sessionId);

    @EntityGraph(attributePaths = {"provider"})
    Optional<FloatBalance> findBySessionIdAndProviderId(Long sessionId, Long providerId);
}