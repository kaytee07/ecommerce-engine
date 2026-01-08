package com.shop.ecommerceengine.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for user responses.
 * Excludes sensitive information like password.
 */
public record UserDTO(
        UUID id,
        String username,
        String email,
        @JsonProperty("full_name")
        String fullName,
        Set<String> roles,
        @JsonProperty("email_verified")
        boolean emailVerified,
        boolean enabled,
        boolean locked,
        @JsonProperty("created_at")
        Instant createdAt,
        @JsonProperty("updated_at")
        Instant updatedAt
) {}
