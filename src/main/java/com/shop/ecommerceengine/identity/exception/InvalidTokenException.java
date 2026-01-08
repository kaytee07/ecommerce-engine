package com.shop.ecommerceengine.identity.exception;

/**
 * Exception thrown when a token is invalid (malformed, wrong signature, etc.).
 */
public class InvalidTokenException extends AuthException {

    public InvalidTokenException() {
        super("Invalid token", INVALID_TOKEN);
    }

    public InvalidTokenException(String message) {
        super(message, INVALID_TOKEN);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, INVALID_TOKEN, cause);
    }
}
