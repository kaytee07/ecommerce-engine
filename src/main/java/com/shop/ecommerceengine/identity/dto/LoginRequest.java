package com.shop.ecommerceengine.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for user login (used internally for form-based auth, not OAuth2 password grant).
 * This is for first-party login before issuing tokens via Authorization Code flow.
 */
public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}
