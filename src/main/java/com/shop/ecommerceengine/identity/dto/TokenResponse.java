package com.shop.ecommerceengine.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for OAuth2 token endpoint.
 * Note: Refresh token is NOT included - it's sent via HttpOnly cookie only.
 */
public record TokenResponse(
        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        long expiresIn,

        String scope
) {
    public TokenResponse(String accessToken, long expiresIn, String scope) {
        this(accessToken, "Bearer", expiresIn, scope);
    }
}
