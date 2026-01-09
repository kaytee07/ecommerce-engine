package com.shop.ecommerceengine.catalog.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for updating an existing product.
 * All fields are optional - only provided fields will be updated.
 */
public record ProductUpdateDTO(
        @Size(max = 255, message = "Product name must be at most 255 characters")
        String name,

        @Size(max = 255, message = "Slug must be at most 255 characters")
        String slug,

        String description,

        @Positive(message = "Price must be positive")
        BigDecimal price,

        @Positive(message = "Compare at price must be positive")
        BigDecimal compareAtPrice,

        UUID categoryId,

        Map<String, Object> attributes,

        @Size(max = 100, message = "SKU must be at most 100 characters")
        String sku,

        Boolean active,

        Boolean featured
) {
}
