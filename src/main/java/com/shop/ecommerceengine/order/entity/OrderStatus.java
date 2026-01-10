package com.shop.ecommerceengine.order.entity;

import java.util.Set;

/**
 * Order status enum with state machine logic.
 * Defines valid transitions between order states.
 *
 * State flow:
 * PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED
 *    |          |            |
 *    v          v            v
 * CANCELLED  CANCELLED   CANCELLED
 *    |
 *    v
 * REFUNDED (only from CANCELLED after payment)
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    /**
     * Check if transition to the target status is valid.
     * Uses switch expression for clean state machine logic.
     *
     * @param target the target status to transition to
     * @return true if transition is valid
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (this == target) {
            return false; // No self-transitions
        }

        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == PROCESSING || target == CANCELLED;
            case PROCESSING -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED -> false; // Terminal state
            case CANCELLED -> target == REFUNDED; // Only refund from cancelled
            case REFUNDED -> false; // Terminal state
        };
    }

    /**
     * Get all valid target states from current status.
     *
     * @return set of valid target statuses
     */
    public Set<OrderStatus> getValidTransitions() {
        return switch (this) {
            case PENDING -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(PROCESSING, CANCELLED);
            case PROCESSING -> Set.of(SHIPPED, CANCELLED);
            case SHIPPED -> Set.of(DELIVERED);
            case DELIVERED -> Set.of();
            case CANCELLED -> Set.of(REFUNDED);
            case REFUNDED -> Set.of();
        };
    }

    /**
     * Check if this status is a terminal state (no further transitions allowed).
     *
     * @return true if terminal state
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == REFUNDED;
    }

    /**
     * Check if this status can be cancelled.
     *
     * @return true if cancellation is allowed
     */
    public boolean isCancellable() {
        return this == PENDING || this == CONFIRMED || this == PROCESSING;
    }

    /**
     * Check if this status represents a completed order.
     *
     * @return true if order is fulfilled
     */
    public boolean isFulfilled() {
        return this == DELIVERED;
    }

    /**
     * Check if this status indicates active processing.
     *
     * @return true if order is being actively processed
     */
    public boolean isActive() {
        return this == PENDING || this == CONFIRMED || this == PROCESSING || this == SHIPPED;
    }

    /**
     * Get display-friendly name for the status.
     *
     * @return human-readable status name
     */
    public String getDisplayName() {
        return switch (this) {
            case PENDING -> "Pending";
            case CONFIRMED -> "Confirmed";
            case PROCESSING -> "Processing";
            case SHIPPED -> "Shipped";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
            case REFUNDED -> "Refunded";
        };
    }
}
