package com.shop.ecommerceengine.identity.repository;

import com.shop.ecommerceengine.identity.entity.AdminAuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AdminAuditLogEntity for audit trail queries.
 */
@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, UUID> {

    /**
     * Find all audit logs by admin ID.
     */
    Page<AdminAuditLogEntity> findByAdminIdOrderByCreatedAtDesc(UUID adminId, Pageable pageable);

    /**
     * Find all audit logs for a specific entity.
     */
    Page<AdminAuditLogEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId, Pageable pageable);

    /**
     * Find audit logs by action type.
     */
    Page<AdminAuditLogEntity> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    /**
     * Find audit logs within a time range.
     */
    Page<AdminAuditLogEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(
            Instant start, Instant end, Pageable pageable);

    /**
     * Find recent audit logs by entity type.
     */
    List<AdminAuditLogEntity> findTop10ByEntityTypeOrderByCreatedAtDesc(String entityType);

    /**
     * Find audit logs by admin username.
     */
    Page<AdminAuditLogEntity> findByAdminUsernameOrderByCreatedAtDesc(
            String adminUsername, Pageable pageable);
}
