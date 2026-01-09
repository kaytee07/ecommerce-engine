package com.shop.ecommerceengine.catalog.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Category response DTO.
 * Immutable record for returning category data to clients.
 * Implements Serializable for Redis caching support.
 */
public record CategoryDTO(
        UUID id,
        String name,
        String slug,
        String description,
        UUID parentId,
        String parentName,
        Integer displayOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}
