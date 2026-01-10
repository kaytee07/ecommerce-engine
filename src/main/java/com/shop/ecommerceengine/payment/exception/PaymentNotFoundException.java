package com.shop.ecommerceengine.payment.exception;

import java.util.UUID;

/**
 * Exception thrown when a payment is not found.
 */
public class PaymentNotFoundException extends RuntimeException {

    private final UUID paymentId;

    public PaymentNotFoundException(UUID paymentId) {
        super(String.format("Payment not found: %s", paymentId));
        this.paymentId = paymentId;
    }

    public PaymentNotFoundException(String transactionRef) {
        super(String.format("Payment not found with transaction reference: %s", transactionRef));
        this.paymentId = null;
    }

    public UUID getPaymentId() {
        return paymentId;
    }
}
