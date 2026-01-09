package com.shop.ecommerceengine.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * DTO for creating a new category.
 * Uses Jakarta Validation for input validation.
 */
public record CategoryCreateDTO(
        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must be at most 100 characters")
        String name,

        @Size(max = 100, message = "Slug must be at most 100 characters")
        String slug,

        String description,

        UUID parentId,

        Integer displayOrder,

        Boolean active
) {
    /**
     * Generates a slug from the category name if not provided.
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
     * Returns display order, defaulting to 0 if not specified.
     */
    public int effectiveDisplayOrder() {
        return displayOrder == null ? 0 : displayOrder;
    }

    /**
     * Returns active status, defaulting to true if not specified.
     */
    public boolean effectiveActive() {
        return active == null || active;
    }
}
