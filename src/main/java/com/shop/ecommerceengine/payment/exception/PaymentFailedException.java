package com.shop.ecommerceengine.payment.exception;

import java.util.UUID;

/**
 * Exception thrown when a payment operation fails.
 */
public class PaymentFailedException extends RuntimeException {

    private final String errorCode;

    public PaymentFailedException(String message) {
        super(message);
        this.errorCode = "PAYMENT_FAILED";
    }

    public PaymentFailedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "PAYMENT_FAILED";
    }

    public PaymentFailedException(UUID orderId, String message) {
        super(String.format("Payment failed for order %s: %s", orderId, message));
        this.errorCode = "PAYMENT_FAILED";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
