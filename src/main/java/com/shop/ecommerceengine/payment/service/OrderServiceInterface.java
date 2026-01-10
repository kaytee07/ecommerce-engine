package com.shop.ecommerceengine.payment.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Interface for Order module operations needed by Payment module.
 * Enables loose coupling between modules.
 */
public interface OrderServiceInterface {

    /**
     * Get the order amount for payment.
     */
    BigDecimal getOrderAmount(UUID orderId);

    /**
     * Get user ID for the order.
     */
    UUID getOrderUserId(UUID orderId);

    /**
     * Check if order exists and is in a payable state.
     */
    boolean isOrderPayable(UUID orderId);

    /**
     * Update order status to PROCESSING (after successful payment).
     */
    void markOrderPaid(UUID orderId, UUID paymentId);

    /**
     * Update order status to REFUNDED.
     */
    void markOrderRefunded(UUID orderId, UUID paymentId, String reason);

    /**
     * Order snapshot for payment processing.
     */
    record OrderSnapshot(
            UUID orderId,
            UUID userId,
            BigDecimal amount,
            String currency,
            boolean payable
    ) {
    }

    /**
     * Get order snapshot for payment.
     */
    OrderSnapshot getOrderSnapshot(UUID orderId);
}
