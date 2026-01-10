package com.shop.ecommerceengine.order.service;

import com.shop.ecommerceengine.order.entity.OrderEntity;
import com.shop.ecommerceengine.order.entity.OrderStatus;
import com.shop.ecommerceengine.order.exception.OrderNotFoundException;
import com.shop.ecommerceengine.order.repository.OrderRepository;
import com.shop.ecommerceengine.payment.service.OrderServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adapter implementing OrderServiceInterface for Payment module.
 * Bridges Order module to Payment module without tight coupling.
 */
@Service
public class OrderServiceAdapter implements OrderServiceInterface {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceAdapter.class);

    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;

    public OrderServiceAdapter(OrderRepository orderRepository, OrderStateMachine stateMachine) {
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getOrderAmount(UUID orderId) {
        return getOrder(orderId).getTotalAmount();
    }

    @Override
    @Transactional(readOnly = true)
    public UUID getOrderUserId(UUID orderId) {
        return getOrder(orderId).getUserId();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOrderPayable(UUID orderId) {
        OrderEntity order = orderRepository.findByIdAndNotDeleted(orderId).orElse(null);
        if (order == null) {
            return false;
        }
        // Order is payable if it's PENDING or CONFIRMED
        return order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED;
    }

    @Override
    @Transactional
    public void markOrderPaid(UUID orderId, UUID paymentId) {
        OrderEntity order = getOrder(orderId);

        // Transition to PROCESSING if payment successful
        if (order.getStatus() == OrderStatus.PENDING) {
            stateMachine.transition(order, OrderStatus.CONFIRMED, null, "Payment initiated");
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            stateMachine.transition(order, OrderStatus.PROCESSING, null, "Payment confirmed - Payment ID: " + paymentId);
        }

        orderRepository.save(order);
        log.info("Order {} marked as paid with payment {}", orderId, paymentId);
    }

    @Override
    @Transactional
    public void markOrderRefunded(UUID orderId, UUID paymentId, String reason) {
        OrderEntity order = getOrder(orderId);

        // First, check if we need to cancel the order before refunding
        if (order.getStatus().isCancellable()) {
            stateMachine.transition(order, OrderStatus.CANCELLED, null, "Payment refunded: " + reason);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            stateMachine.transition(order, OrderStatus.REFUNDED, null, "Refund processed - Payment ID: " + paymentId);
        }

        orderRepository.save(order);
        log.info("Order {} marked as refunded with payment {} - Reason: {}", orderId, paymentId, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderSnapshot getOrderSnapshot(UUID orderId) {
        OrderEntity order = orderRepository.findByIdAndNotDeleted(orderId).orElse(null);
        if (order == null) {
            return new OrderSnapshot(orderId, null, null, null, false);
        }

        boolean payable = order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED;

        return new OrderSnapshot(
                order.getId(),
                order.getUserId(),
                order.getTotalAmount(),
                "GHS", // Default currency
                payable
        );
    }

    private OrderEntity getOrder(UUID orderId) {
        return orderRepository.findByIdAndNotDeleted(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
