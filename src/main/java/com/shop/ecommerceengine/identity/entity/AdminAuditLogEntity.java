package com.shop.ecommerceengine.identity.entity;

import com.shop.ecommerceengine.common.converter.JsonMapConverter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for tracking all admin actions for audit purposes.
 * Stores old and new values as JSONB for complete change tracking.
 */
@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "admin_username", nullable = false, length = 50)
    private String adminUsername;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AdminAuditLogEntity() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Builder-style factory method for convenience
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AdminAuditLogEntity entity = new AdminAuditLogEntity();

        public Builder adminId(UUID adminId) {
            entity.adminId = adminId;
            return this;
        }

        public Builder adminUsername(String adminUsername) {
            entity.adminUsername = adminUsername;
            return this;
        }

        public Builder action(String action) {
            entity.action = action;
            return this;
        }

        public Builder entityType(String entityType) {
            entity.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            entity.entityId = entityId;
            return this;
        }

        public Builder oldValue(Map<String, Object> oldValue) {
            entity.oldValue = oldValue;
            return this;
        }

        public Builder newValue(Map<String, Object> newValue) {
            entity.newValue = newValue;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            entity.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            entity.userAgent = userAgent;
            return this;
        }

        public AdminAuditLogEntity build() {
            return entity;
        }
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public void setAdminId(UUID adminId) {
        this.adminId = adminId;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Map<String, Object> getOldValue() {
        return oldValue;
    }

    public void setOldValue(Map<String, Object> oldValue) {
        this.oldValue = oldValue;
    }

    public Map<String, Object> getNewValue() {
        return newValue;
    }

    public void setNewValue(Map<String, Object> newValue) {
        this.newValue = newValue;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "AdminAuditLogEntity{" +
                "id=" + id +
                ", adminId=" + adminId +
                ", adminUsername='" + adminUsername + '\'' +
                ", action='" + action + '\'' +
                ", entityType='" + entityType + '\'' +
                ", entityId='" + entityId + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
