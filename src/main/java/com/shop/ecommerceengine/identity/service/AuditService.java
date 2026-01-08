package com.shop.ecommerceengine.identity.service;

import com.shop.ecommerceengine.identity.entity.AdminAuditLogEntity;
import com.shop.ecommerceengine.identity.event.AdminAuditEvent;
import com.shop.ecommerceengine.identity.repository.AdminAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing admin audit logs.
 * Listens to AdminAuditEvent and persists audit records.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AdminAuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AuditService(AdminAuditLogRepository auditLogRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publish an audit event for asynchronous processing.
     */
    public void publishAuditEvent(UUID adminId, String adminUsername, String action,
                                   String entityType, String entityId,
                                   Map<String, Object> oldValue, Map<String, Object> newValue,
                                   String ipAddress, String userAgent) {
        AdminAuditEvent event = AdminAuditEvent.builder(this)
                .adminId(adminId)
                .adminUsername(adminUsername)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        eventPublisher.publishEvent(event);
        log.debug("Published audit event: {} on {} by {}", action, entityType, adminUsername);
    }

    /**
     * Handle audit event asynchronously.
     */
    @Async
    @EventListener
    @Transactional
    public void handleAuditEvent(AdminAuditEvent event) {
        try {
            AdminAuditLogEntity auditLog = AdminAuditLogEntity.builder()
                    .adminId(event.getAdminId())
                    .adminUsername(event.getAdminUsername())
                    .action(event.getAction())
                    .entityType(event.getEntityType())
                    .entityId(event.getEntityId())
                    .oldValue(event.getOldValue())
                    .newValue(event.getNewValue())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Audit log saved: {} on {} by {}", event.getAction(),
                    event.getEntityType(), event.getAdminUsername());

        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * Get audit logs by admin ID.
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLogEntity> getAuditLogsByAdmin(UUID adminId, Pageable pageable) {
        return auditLogRepository.findByAdminIdOrderByCreatedAtDesc(adminId, pageable);
    }

    /**
     * Get audit logs for a specific entity.
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLogEntity> getAuditLogsForEntity(String entityType, String entityId,
                                                           Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                entityType, entityId, pageable);
    }

    /**
     * Get audit logs by action type.
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLogEntity> getAuditLogsByAction(String action, Pageable pageable) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);
    }

    /**
     * Get audit logs within a time range.
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLogEntity> getAuditLogsByTimeRange(Instant start, Instant end,
                                                             Pageable pageable) {
        return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable);
    }
}
