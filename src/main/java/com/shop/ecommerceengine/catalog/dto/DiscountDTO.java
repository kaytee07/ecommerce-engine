package com.shop.ecommerceengine.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for setting a discount on a product.
 * Uses Jakarta Validation for input validation.
 */
public record DiscountDTO(
        @NotNull(message = "Discount percentage is required")
        @DecimalMin(value = "0", message = "Discount percentage must be at least 0")
        @DecimalMax(value = "100", message = "Discount percentage must be at most 100")
        BigDecimal discountPercentage,

        LocalDateTime startDate,

        LocalDateTime endDate
) {
    /**
     * Validates that end date is after start date if both are provided.
     */
    public boolean hasValidDateRange() {
        if (startDate == null || endDate == null) {
            return true;
        }
        return endDate.isAfter(startDate);
    }
}
