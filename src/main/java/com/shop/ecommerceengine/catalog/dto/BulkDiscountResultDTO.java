package com.shop.ecommerceengine.catalog.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO for returning the result of a bulk discount operation.
 */
public record BulkDiscountResultDTO(
        int updatedCount,
        BigDecimal discountPercentage,
        String scope
) implements Serializable {
}
