package com.Wakala.v1.Repositories;

import com.Wakala.v1.Entity.DailySession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySessionRepository extends JpaRepository<DailySession, Long> {

    /**
     * Override findById ili kuchukua openedBy na closedBy.
     * Bila hii, toResponse() inashindwa na LazyInitializationException.
     */
    @EntityGraph(attributePaths = { "openedBy", "closedBy" })
    Optional<DailySession> findById(Long id);

    @EntityGraph(attributePaths = { "openedBy", "closedBy" })
    List<DailySession> findAll();

    @EntityGraph(attributePaths = { "openedBy", "closedBy" })
    Optional<DailySession> findBySessionDateAndStatus(LocalDate date, DailySession.SessionStatus status);

    @EntityGraph(attributePaths = { "openedBy", "closedBy" })
    List<DailySession> findByStatus(DailySession.SessionStatus status);

    @EntityGraph(attributePaths = { "openedBy", "closedBy" })
    List<DailySession> findByOpenedByIdOrClosedById(Long openedById, Long closedById);

    @EntityGraph(attributePaths = { "openedBy", "closedBy" })
    Optional<DailySession> findTopByStatusOrderBySessionDateDesc(DailySession.SessionStatus status);
}