package com.Wakala.v1.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.Wakala.v1.Entity.AuditLog;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Repositories.AuditLogRepository;
import com.Wakala.v1.Repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entityType, Long entityId,
                    String oldValue, String newValue) {
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        AuditLog log = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .build();
        auditLogRepository.save(log);
    }
}
