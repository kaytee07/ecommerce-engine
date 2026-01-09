package com.shop.ecommerceengine.catalog.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Product response DTO.
 * Immutable record for returning product data to clients.
 * Implements Serializable for Redis caching support.
 */
public record ProductDTO(
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
        BigDecimal currentDiscountPercentage,
        BigDecimal effectivePrice,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
    /**
     * Calculates the discount percentage if compareAtPrice is set and higher than price.
     * This is the legacy method for compare-at-price discounts.
     */
    public Integer compareAtDiscountPercentage() {
        if (compareAtPrice != null && compareAtPrice.compareTo(price) > 0) {
            BigDecimal discount = compareAtPrice.subtract(price);
            return discount.multiply(BigDecimal.valueOf(100))
                    .divide(compareAtPrice, 0, java.math.RoundingMode.HALF_UP)
                    .intValue();
        }
        return null;
    }
}
