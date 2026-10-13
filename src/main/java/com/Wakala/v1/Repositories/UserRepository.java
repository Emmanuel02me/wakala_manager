package com.Wakala.v1.Repositories;

import com.Wakala.v1.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // ═══ Zilizopo (bado zinatumika) ═══
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(User.Role role);

    // ═══ MPYA — kwa soft delete ═══

    /** Login — inaruhusu ACTIVE user pekee */
    Optional<User> findByUsernameAndActiveTrue(String username);

    /** Orodha ya watumiaji hai (kwa UI ya kawaida) */
    List<User> findByActiveTrueOrderByCreatedAtDesc();

    /** Orodha ya wote — pamoja na waliofutwa (kwa OWNER) */
    List<User> findAllByOrderByCreatedAtDesc();

    /** Hesabu owners ACTIVE pekee (kwa ulinzi wa "last owner") */
    long countByRoleAndActiveTrue(User.Role role);
}