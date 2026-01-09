package com.shop.ecommerceengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for creating a new product.
 * Uses Jakarta Validation for input validation.
 */
public record ProductCreateDTO(
        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name must be at most 255 characters")
        String name,

        @Size(max = 255, message = "Slug must be at most 255 characters")
        String slug,

        String description,

        @NotNull(message = "Price is required")
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
    /**
     * Generates a slug from the product name if not provided.
     */
    public String effectiveSlug() {
        if (slug != null && !slug.isBlank()) {
            return slug;
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Returns active status, defaulting to true if not specified.
     */
    public boolean effectiveActive() {
        return active == null || active;
    }

    /**
     * Returns featured status, defaulting to false if not specified.
     */
    public boolean effectiveFeatured() {
        return featured != null && featured;
    }
}
