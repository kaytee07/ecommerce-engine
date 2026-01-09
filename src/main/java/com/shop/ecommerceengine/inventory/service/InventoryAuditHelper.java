package com.shop.ecommerceengine.inventory.service;

import com.shop.ecommerceengine.identity.service.AuditService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper for publishing inventory-related audit events.
 */
@Component
public class InventoryAuditHelper {

    private static final String ENTITY_TYPE = "INVENTORY";

    private final AuditService auditService;

    public InventoryAuditHelper(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Audit stock adjustment.
     */
    public void auditStockAdjusted(UUID adminId, String adminUsername,
                                    UUID productId, int quantityBefore, int quantityAfter,
                                    String adjustmentType, String reason,
                                    String ipAddress, String userAgent) {
        if (adminId == null) {
            return;
        }

        Map<String, Object> oldValue = new HashMap<>();
        oldValue.put("stockQuantity", quantityBefore);

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("stockQuantity", quantityAfter);
        newValue.put("change", quantityAfter - quantityBefore);
        newValue.put("adjustmentType", adjustmentType);
        newValue.put("reason", reason);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "INVENTORY_ADJUSTED",
                ENTITY_TYPE,
                productId.toString(),
                oldValue,
                newValue,
                ipAddress, userAgent
        );
    }

    /**
     * Audit stock reservation.
     */
    public void auditStockReserved(UUID adminId, String adminUsername,
                                    UUID productId, int quantity,
                                    String ipAddress, String userAgent) {
        if (adminId == null) {
            return;
        }

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("reservedQuantity", quantity);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "INVENTORY_RESERVED",
                ENTITY_TYPE,
                productId.toString(),
                null,
                newValue,
                ipAddress, userAgent
        );
    }

    /**
     * Audit stock release.
     */
    public void auditStockReleased(UUID adminId, String adminUsername,
                                    UUID productId, int quantity,
                                    String ipAddress, String userAgent) {
        if (adminId == null) {
            return;
        }

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("releasedQuantity", quantity);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "INVENTORY_RELEASED",
                ENTITY_TYPE,
                productId.toString(),
                null,
                newValue,
                ipAddress, userAgent
        );
    }

    /**
     * Audit inventory creation.
     */
    public void auditInventoryCreated(UUID adminId, String adminUsername,
                                       UUID productId, int initialStock,
                                       String ipAddress, String userAgent) {
        if (adminId == null) {
            return;
        }

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("productId", productId.toString());
        newValue.put("initialStock", initialStock);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "INVENTORY_CREATED",
                ENTITY_TYPE,
                productId.toString(),
                null,
                newValue,
                ipAddress, userAgent
        );
    }
}
