package com.shop.ecommerceengine.catalog.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Category tree DTO for hierarchical representation.
 * Contains nested children for building category trees.
 * Implements Serializable for Redis caching support.
 */
public record CategoryTreeDTO(
        UUID id,
        String name,
        String slug,
        String description,
        Integer displayOrder,
        boolean active,
        List<CategoryTreeDTO> children
) implements Serializable {
}
