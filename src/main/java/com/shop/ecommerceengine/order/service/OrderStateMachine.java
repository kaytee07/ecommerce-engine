package com.shop.ecommerceengine.order.service;

import com.shop.ecommerceengine.order.entity.OrderEntity;
import com.shop.ecommerceengine.order.entity.OrderStatus;
import com.shop.ecommerceengine.order.entity.OrderStatusHistoryEntity;
import com.shop.ecommerceengine.order.exception.InvalidOrderStateException;
import com.shop.ecommerceengine.order.repository.OrderStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service responsible for order state transitions.
 * Validates transitions and logs status changes for audit.
 */
@Service
public class OrderStateMachine {

    private static final Logger log = LoggerFactory.getLogger(OrderStateMachine.class);

    private final OrderStatusHistoryRepository statusHistoryRepository;

    public OrderStateMachine(OrderStatusHistoryRepository statusHistoryRepository) {
        this.statusHistoryRepository = statusHistoryRepository;
    }

    /**
     * Validate and perform state transition.
     *
     * @param order the order to transition
     * @param targetStatus the target status
     * @param changedBy the user making the change (null for system)
     * @param reason optional reason for the change
     * @throws InvalidOrderStateException if transition is invalid
     */
    public void transition(OrderEntity order, OrderStatus targetStatus, UUID changedBy, String reason) {
        OrderStatus currentStatus = order.getStatus();

        // Validate transition
        if (!currentStatus.canTransitionTo(targetStatus)) {
            throw new InvalidOrderStateException(order.getId(), currentStatus, targetStatus);
        }

        // Log the transition
        logStatusChange(order.getId(), currentStatus, targetStatus, changedBy, reason);

        // Perform the transition
        order.setStatus(targetStatus);

        log.info("Order {} transitioned from {} to {} by user {}",
                order.getId(), currentStatus, targetStatus, changedBy);
    }

    /**
     * Validate transition without performing it.
     *
     * @param currentStatus current order status
     * @param targetStatus target status
     * @return true if transition is valid
     */
    public boolean canTransition(OrderStatus currentStatus, OrderStatus targetStatus) {
        return currentStatus.canTransitionTo(targetStatus);
    }

    /**
     * Get the next logical status in the happy path.
     *
     * @param currentStatus current order status
     * @return next status in normal flow, or null if terminal
     */
    public OrderStatus getNextHappyPathStatus(OrderStatus currentStatus) {
        return switch (currentStatus) {
            case PENDING -> OrderStatus.CONFIRMED;
            case CONFIRMED -> OrderStatus.PROCESSING;
            case PROCESSING -> OrderStatus.SHIPPED;
            case SHIPPED -> OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED, REFUNDED -> null;
        };
    }

    /**
     * Log status change for audit trail.
     */
    private void logStatusChange(UUID orderId, OrderStatus previousStatus, OrderStatus newStatus,
                                  UUID changedBy, String reason) {
        OrderStatusHistoryEntity history = new OrderStatusHistoryEntity(
                orderId,
                previousStatus,
                newStatus,
                changedBy,
                reason
        );
        statusHistoryRepository.save(history);

        log.debug("Logged status change for order {}: {} -> {}", orderId, previousStatus, newStatus);
    }
}
