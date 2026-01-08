package com.shop.ecommerceengine.identity.exception;

import com.shop.ecommerceengine.common.exception.BaseCustomException;
import org.springframework.http.HttpStatus;

/**
 * Base exception for authentication-related errors.
 */
public class AuthException extends BaseCustomException {

    public AuthException(String message, String errorCode) {
        super(message, HttpStatus.UNAUTHORIZED, errorCode);
    }

    public AuthException(String message, String errorCode, HttpStatus httpStatus) {
        super(message, httpStatus, errorCode);
    }

    public AuthException(String message, String errorCode, Throwable cause) {
        super(message, HttpStatus.UNAUTHORIZED, errorCode);
        initCause(cause);
    }

    // Standard error codes
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String TOKEN_REVOKED = "TOKEN_REVOKED";
    public static final String INVALID_GRANT = "INVALID_GRANT";
    public static final String INVALID_CLIENT = "INVALID_CLIENT";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String REPLAY_ATTACK = "REPLAY_ATTACK";
    public static final String USER_DISABLED = "USER_DISABLED";
    public static final String USER_LOCKED = "USER_LOCKED";
}
