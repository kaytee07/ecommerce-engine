package com.shop.ecommerceengine.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when rate limit is exceeded for an endpoint.
 */
public class RateLimitExceededException extends BaseCustomException {

    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";

    public RateLimitExceededException() {
        super("Too many requests. Please try again later.", HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_EXCEEDED);
    }

    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_EXCEEDED);
    }

    public RateLimitExceededException(long retryAfterSeconds) {
        super(String.format("Too many requests. Please try again in %d seconds.", retryAfterSeconds), HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_EXCEEDED);
    }
}
