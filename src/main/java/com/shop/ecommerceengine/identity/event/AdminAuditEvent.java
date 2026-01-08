package com.shop.ecommerceengine.identity.event;

import org.springframework.context.ApplicationEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Event fired when an admin action occurs that needs to be audited.
 * Listeners can process this event asynchronously.
 */
public class AdminAuditEvent extends ApplicationEvent {

    private final UUID adminId;
    private final String adminUsername;
    private final String action;
    private final String entityType;
    private final String entityId;
    private final Map<String, Object> oldValue;
    private final Map<String, Object> newValue;
    private final String ipAddress;
    private final String userAgent;

    public AdminAuditEvent(Object source, UUID adminId, String adminUsername,
                           String action, String entityType, String entityId,
                           Map<String, Object> oldValue, Map<String, Object> newValue,
                           String ipAddress, String userAgent) {
        super(source);
        this.adminId = adminId;
        this.adminUsername = adminUsername;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public static Builder builder(Object source) {
        return new Builder(source);
    }

    public static class Builder {
        private final Object source;
        private UUID adminId;
        private String adminUsername;
        private String action;
        private String entityType;
        private String entityId;
        private Map<String, Object> oldValue;
        private Map<String, Object> newValue;
        private String ipAddress;
        private String userAgent;

        public Builder(Object source) {
            this.source = source;
        }

        public Builder adminId(UUID adminId) {
            this.adminId = adminId;
            return this;
        }

        public Builder adminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder oldValue(Map<String, Object> oldValue) {
            this.oldValue = oldValue;
            return this;
        }

        public Builder newValue(Map<String, Object> newValue) {
            this.newValue = newValue;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public AdminAuditEvent build() {
            return new AdminAuditEvent(source, adminId, adminUsername, action,
                    entityType, entityId, oldValue, newValue, ipAddress, userAgent);
        }
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public Map<String, Object> getOldValue() {
        return oldValue;
    }

    public Map<String, Object> getNewValue() {
        return newValue;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
