package com.shop.ecommerceengine.identity.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * DTO for assigning roles to a user by admin.
 */
public record RoleAssignDTO(
        @NotEmpty(message = "At least one role is required")
        Set<String> roles
) {}
