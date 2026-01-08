package com.shop.ecommerceengine.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 error response following RFC 6749.
 */
public record OAuth2ErrorResponse(
        String error,

        @JsonProperty("error_description")
        String errorDescription,

        @JsonProperty("error_uri")
        String errorUri
) {
    public OAuth2ErrorResponse(String error, String errorDescription) {
        this(error, errorDescription, null);
    }

    // Standard OAuth2 error codes
    public static final String INVALID_REQUEST = "invalid_request";
    public static final String INVALID_CLIENT = "invalid_client";
    public static final String INVALID_GRANT = "invalid_grant";
    public static final String UNAUTHORIZED_CLIENT = "unauthorized_client";
    public static final String UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type";
    public static final String INVALID_SCOPE = "invalid_scope";
    public static final String ACCESS_DENIED = "access_denied";
    public static final String SERVER_ERROR = "server_error";
}
