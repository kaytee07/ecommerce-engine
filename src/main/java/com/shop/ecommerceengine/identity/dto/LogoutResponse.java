package com.shop.ecommerceengine.identity.dto;

/**
 * Response DTO for logout endpoint.
 */
public record LogoutResponse(
        boolean success,
        String message
) {
    public static LogoutResponse successful() {
        return new LogoutResponse(true, "Logged out successfully");
    }
}
