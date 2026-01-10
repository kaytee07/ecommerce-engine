package com.shop.ecommerceengine.order.exception;

import java.util.UUID;

/**
 * Exception thrown when an order is not found.
 */
public class OrderNotFoundException extends RuntimeException {

    private final UUID orderId;
    private final UUID userId;

    public OrderNotFoundException(UUID orderId) {
        super("Order not found with ID: " + orderId);
        this.orderId = orderId;
        this.userId = null;
    }

    public OrderNotFoundException(UUID orderId, UUID userId) {
        super("Order not found with ID: " + orderId + " for user: " + userId);
        this.orderId = orderId;
        this.userId = userId;
    }

    public OrderNotFoundException(String message) {
        super(message);
        this.orderId = null;
        this.userId = null;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }
}
