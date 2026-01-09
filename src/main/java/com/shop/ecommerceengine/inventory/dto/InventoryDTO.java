package com.shop.ecommerceengine.inventory.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for inventory information.
 */
public record InventoryDTO(
        UUID id,
        UUID productId,
        Integer stockQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        LocalDateTime updatedAt
) {
}
