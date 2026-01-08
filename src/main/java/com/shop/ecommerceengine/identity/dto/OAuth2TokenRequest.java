package com.shop.ecommerceengine.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for OAuth2 token endpoint.
 * Supports:
 * - authorization_code: Exchange code for tokens (with PKCE)
 * - client_credentials: Machine-to-machine auth
 * - refresh_token: Token refresh (token from cookie, not body)
 */
public record OAuth2TokenRequest(
        @JsonProperty("grant_type")
        String grantType,

        String code,

        @JsonProperty("redirect_uri")
        String redirectUri,

        @JsonProperty("client_id")
        String clientId,

        @JsonProperty("client_secret")
        String clientSecret,

        @JsonProperty("code_verifier")
        String codeVerifier,

        String scope
) {
    // Supported grant types
    public static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    public static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";
    public static final String GRANT_REFRESH_TOKEN = "refresh_token";
}
