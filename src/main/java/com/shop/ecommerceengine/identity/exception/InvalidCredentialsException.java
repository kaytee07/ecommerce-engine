package com.shop.ecommerceengine.identity.exception;

/**
 * Exception thrown when credentials are invalid.
 */
public class InvalidCredentialsException extends AuthException {

    public InvalidCredentialsException() {
        super("Invalid username or password", INVALID_CREDENTIALS);
    }

    public InvalidCredentialsException(String message) {
        super(message, INVALID_CREDENTIALS);
    }
}
