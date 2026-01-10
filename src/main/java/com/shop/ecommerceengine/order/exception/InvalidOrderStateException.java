package com.shop.ecommerceengine.order.exception;

import com.shop.ecommerceengine.order.entity.OrderStatus;

import java.util.UUID;

/**
 * Exception thrown when an invalid order state transition is attempted.
 */
public class InvalidOrderStateException extends RuntimeException {

    private final UUID orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus targetStatus;

    public InvalidOrderStateException(String message) {
        super(message);
        this.orderId = null;
        this.currentStatus = null;
        this.targetStatus = null;
    }

    public InvalidOrderStateException(UUID orderId, OrderStatus currentStatus, OrderStatus targetStatus) {
        super(String.format(
                "Invalid order state transition for order %s: cannot transition from %s to %s",
                orderId, currentStatus, targetStatus
        ));
        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public InvalidOrderStateException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super(String.format(
                "Invalid order state transition: cannot transition from %s to %s",
                currentStatus, targetStatus
        ));
        this.orderId = null;
        this.currentStatus = currentStatus;
        this.targetStatus = targetStatus;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getTargetStatus() {
        return targetStatus;
    }
}
