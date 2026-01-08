package com.shop.ecommerceengine.identity.dto;

import java.util.Set;

/**
 * Response DTO for current user information.
 */
public record UserInfoResponse(
        String id,
        String username,
        String email,
        Set<String> roles,
        boolean emailVerified
) {}
