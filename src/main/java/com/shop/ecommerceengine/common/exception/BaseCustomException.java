package com.shop.ecommerceengine.common.exception;

import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Base exception class for all custom exceptions in the application.
 * Provides consistent error handling with HTTP status, error code, and additional details.
 */
public class BaseCustomException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final Map<String, Object> details;

    public BaseCustomException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = "ERR_" + httpStatus.value();
        this.details = new HashMap<>();
    }

    public BaseCustomException(String message, HttpStatus httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    public BaseCustomException(String message, HttpStatus httpStatus, String errorCode, Map<String, Object> details) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.details = details != null ? details : new HashMap<>();
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public BaseCustomException addDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }
}
