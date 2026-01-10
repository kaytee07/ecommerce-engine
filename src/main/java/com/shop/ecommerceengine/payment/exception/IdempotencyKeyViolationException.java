package com.shop.ecommerceengine.payment.exception;

/**
 * Exception thrown when an idempotency key is reused with different parameters.
 */
public class IdempotencyKeyViolationException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyKeyViolationException(String idempotencyKey) {
        super(String.format("Idempotency key already used for a different request: %s", idempotencyKey));
        this.idempotencyKey = idempotencyKey;
    }

    public IdempotencyKeyViolationException(String idempotencyKey, String message) {
        super(String.format("Idempotency key already used: %s - %s", idempotencyKey, message));
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
