package com.shop.ecommerceengine.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for product search parameters.
 * Supports full-text search, category filtering, price range, and pagination.
 */
public record ProductSearchRequest(
        String q,

        String category,

        UUID categoryId,

        @Positive(message = "Minimum price must be positive")
        BigDecimal minPrice,

        @Positive(message = "Maximum price must be positive")
        BigDecimal maxPrice,

        Boolean featured,

        Boolean active,

        String sortBy,

        String sortDirection,

        @Min(value = 0, message = "Page must be at least 0")
        Integer page,

        @Min(value = 1, message = "Size must be at least 1")
        @Max(value = 100, message = "Size must be at most 100")
        Integer size
) {
    /**
     * Returns page number, defaulting to 0 if not specified.
     */
    public int effectivePage() {
        return page == null ? 0 : page;
    }

    /**
     * Returns page size, defaulting to 20 if not specified.
     */
    public int effectiveSize() {
        return size == null ? 20 : size;
    }

    /**
     * Returns sort field, defaulting to "createdAt" if not specified.
     */
    public String effectiveSortBy() {
        return sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy;
    }

    /**
     * Returns sort direction, defaulting to "desc" if not specified.
     */
    public String effectiveSortDirection() {
        return sortDirection == null || sortDirection.isBlank() ? "desc" : sortDirection;
    }

    /**
     * Returns active filter, defaulting to true (only active products) if not specified.
     */
    public boolean effectiveActive() {
        return active == null || active;
    }
}
