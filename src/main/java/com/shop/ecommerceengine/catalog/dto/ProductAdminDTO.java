package com.shop.ecommerceengine.catalog.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Admin product DTO with full fields including discounts and soft delete info.
 * Immutable record for returning product data to admin users.
 */
public record ProductAdminDTO(
        UUID id,
        String name,
        String slug,
        String description,
        BigDecimal price,
        BigDecimal compareAtPrice,
        UUID categoryId,
        String categoryName,
        Map<String, Object> attributes,
        String sku,
        boolean active,
        boolean featured,
        BigDecimal discountPercentage,
        LocalDateTime discountStart,
        LocalDateTime discountEnd,
        boolean discountActive,
        BigDecimal effectivePrice,
        Instant deletedAt,
        Long version,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
