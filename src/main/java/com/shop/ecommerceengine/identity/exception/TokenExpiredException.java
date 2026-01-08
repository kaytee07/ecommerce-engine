package com.shop.ecommerceengine.identity.exception;

/**
 * Exception thrown when a token has expired.
 */
public class TokenExpiredException extends AuthException {

    public TokenExpiredException() {
        super("Token has expired", TOKEN_EXPIRED);
    }

    public TokenExpiredException(String message) {
        super(message, TOKEN_EXPIRED);
    }
}
