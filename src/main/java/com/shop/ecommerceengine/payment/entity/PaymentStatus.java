package com.shop.ecommerceengine.payment.entity;

/**
 * Payment status enum representing the lifecycle of a payment.
 * PENDING -> SUCCESS/FAILED
 * SUCCESS -> REFUNDED
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED,
    CANCELLED;

    /**
     * Check if payment can be refunded.
     * Only successful payments can be refunded.
     */
    public boolean isRefundable() {
        return this == SUCCESS;
    }

    /**
     * Check if payment is in a terminal state.
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == REFUNDED || this == CANCELLED;
    }

    /**
     * Check if payment can transition to the target status.
     */
    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case PENDING -> target == SUCCESS || target == FAILED || target == CANCELLED;
            case SUCCESS -> target == REFUNDED;
            case FAILED -> false;
            case REFUNDED -> false;
            case CANCELLED -> false;
        };
    }
}
