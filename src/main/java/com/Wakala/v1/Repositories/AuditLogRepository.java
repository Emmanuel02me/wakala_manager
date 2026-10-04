package com.Wakala.v1.Repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.Wakala.v1.Entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUserIdOrderByTimestampDesc(Long userId);
    List<AuditLog> findAllByOrderByTimestampDesc();
}
