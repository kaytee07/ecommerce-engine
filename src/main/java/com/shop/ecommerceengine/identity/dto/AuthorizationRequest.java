package com.shop.ecommerceengine.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for OAuth2 authorization endpoint.
 * Used for Authorization Code + PKCE flow.
 */
public record AuthorizationRequest(
        @JsonProperty("response_type")
        String responseType,

        @JsonProperty("client_id")
        String clientId,

        @JsonProperty("redirect_uri")
        String redirectUri,

        String scope,

        String state,

        @JsonProperty("code_challenge")
        String codeChallenge,

        @JsonProperty("code_challenge_method")
        String codeChallengeMethod
) {
    public static final String RESPONSE_TYPE_CODE = "code";
    public static final String CODE_CHALLENGE_METHOD_S256 = "S256";
}
