package com.shop.ecommerceengine.inventory.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin DTO for inventory with additional fields.
 */
public record InventoryAdminDTO(
        UUID id,
        UUID productId,
        Integer stockQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
