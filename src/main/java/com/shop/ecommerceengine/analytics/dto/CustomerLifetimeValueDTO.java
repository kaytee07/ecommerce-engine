package com.shop.ecommerceengine.analytics.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for customer lifetime value calculations.
 */
public record CustomerLifetimeValueDTO(
    UUID userId,
    long orderCount,
    BigDecimal totalSpent,
    BigDecimal avgOrderValue,
    Instant firstOrderDate,
    Instant lastOrderDate,
    long customerTenureDays
) implements Serializable {

    public CustomerLifetimeValueDTO {
        if (totalSpent == null) totalSpent = BigDecimal.ZERO;
        if (avgOrderValue == null) avgOrderValue = BigDecimal.ZERO;
    }

    /**
     * Create a zero-value CLV for users with no orders.
     */
    public static CustomerLifetimeValueDTO empty(UUID userId) {
        return new CustomerLifetimeValueDTO(
            userId,
            0,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            null,
            0
        );
    }
}
