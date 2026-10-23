package com.aquaalert.service;

import com.aquaalert.entity.AuditLog;
import com.aquaalert.entity.User;
import com.aquaalert.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an audit trail entry for every meaningful state change
 * (area/resident/schedule create-update-delete, auth events, etc).
 *
 * Runs in its own async thread + its own transaction (REQUIRES_NEW) so a
 * logging failure never rolls back the business operation that triggered
 * it, and the caller doesn't pay the latency of an extra write.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User actor, String action, String entityType, Long entityId, String details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .user(actor)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception ex) {
            // Audit logging must never break the primary operation.
            log.warn("Failed to write audit log for action={} entityType={} entityId={}: {}",
                    action, entityType, entityId, ex.getMessage());
        }
    }
}
