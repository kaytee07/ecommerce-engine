package com.shop.ecommerceengine.order.service;

import com.shop.ecommerceengine.order.dto.OrderDTO;
import com.shop.ecommerceengine.order.dto.OrderHistoryDTO;
import com.shop.ecommerceengine.order.entity.OrderEntity;
import com.shop.ecommerceengine.order.entity.OrderItem;
import com.shop.ecommerceengine.order.entity.OrderStatus;
import com.shop.ecommerceengine.order.exception.InvalidOrderStateException;
import com.shop.ecommerceengine.order.exception.OrderNotFoundException;
import com.shop.ecommerceengine.order.mapper.OrderMapper;
import com.shop.ecommerceengine.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for order operations.
 * Handles order creation from cart, status updates, and queries.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderStateMachine stateMachine;
    private final CartServiceInterface cartService;

    public OrderService(OrderRepository orderRepository,
                        OrderMapper orderMapper,
                        OrderStateMachine stateMachine,
                        CartServiceInterface cartService) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.stateMachine = stateMachine;
        this.cartService = cartService;
    }

    /**
     * Create an order from the user's cart.
     * Transactional: validates cart, creates order, clears cart.
     *
     * @param userId the user ID
     * @return the created order
     * @throws InvalidOrderStateException if cart is empty
     */
    @Transactional
    public OrderDTO createOrderFromCart(UUID userId) {
        // Validate cart is not empty
        if (cartService.isCartEmpty(userId)) {
            throw new InvalidOrderStateException("Cannot create order from empty cart");
        }

        // Get cart items
        List<CartServiceInterface.CartItemSnapshot> cartItems = cartService.getCartItems(userId);

        // Create order entity
        OrderEntity order = new OrderEntity(userId);

        // Convert cart items to order items
        List<OrderItem> orderItems = cartItems.stream()
                .map(item -> new OrderItem(
                        item.productId(),
                        item.productName(),
                        item.sku(),
                        item.quantity(),
                        item.priceAtAdd()
                ))
                .toList();

        order.setItemsAndRecalculate(orderItems);

        // Save order
        order = orderRepository.save(order);

        // Clear the cart after successful order creation
        cartService.clearCart(userId);

        log.info("Created order {} for user {} with {} items, total: {}",
                order.getId(), userId, orderItems.size(), order.getTotalAmount());

        return orderMapper.toDTO(order);
    }

    /**
     * Get order by ID for a specific user.
     *
     * @param orderId the order ID
     * @param userId the user ID (for ownership validation)
     * @return the order
     * @throws OrderNotFoundException if order not found or not owned by user
     */
    @Transactional(readOnly = true)
    public OrderDTO getOrderById(UUID orderId, UUID userId) {
        OrderEntity order = orderRepository.findByIdAndUserIdAndNotDeleted(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId, userId));

        return orderMapper.toDTO(order);
    }

    /**
     * Get order by ID (admin - no user check).
     *
     * @param orderId the order ID
     * @return the order
     * @throws OrderNotFoundException if order not found
     */
    @Transactional(readOnly = true)
    public OrderDTO getOrderByIdAdmin(UUID orderId) {
        OrderEntity order = orderRepository.findByIdAndNotDeleted(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return orderMapper.toDTO(order);
    }

    /**
     * Get user's order history.
     *
     * @param userId the user ID
     * @return list of order history summaries
     */
    @Transactional(readOnly = true)
    public List<OrderHistoryDTO> getUserOrders(UUID userId) {
        List<OrderEntity> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return orderMapper.toHistoryDTOList(orders);
    }

    /**
     * Update order status with validation.
     *
     * @param orderId the order ID
     * @param newStatus the target status
     * @return the updated order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidOrderStateException if transition is invalid
     */
    @Transactional
    public OrderDTO updateStatus(UUID orderId, OrderStatus newStatus) {
        return updateStatus(orderId, newStatus, null, null);
    }

    /**
     * Update order status with validation and reason.
     *
     * @param orderId the order ID
     * @param newStatus the target status
     * @param changedBy the user making the change
     * @param reason reason for the change
     * @return the updated order
     */
    @Transactional
    public OrderDTO updateStatus(UUID orderId, OrderStatus newStatus, UUID changedBy, String reason) {
        OrderEntity order = orderRepository.findByIdAndNotDeleted(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Use state machine for validated transition
        stateMachine.transition(order, newStatus, changedBy, reason);

        order = orderRepository.save(order);

        log.info("Updated order {} status to {}", orderId, newStatus);

        return orderMapper.toDTO(order);
    }

    /**
     * Admin update status (any order, no ownership check).
     *
     * @param orderId the order ID
     * @param newStatus the target status
     * @return the updated order
     */
    @Transactional
    public OrderDTO adminUpdateStatus(UUID orderId, OrderStatus newStatus) {
        return updateStatus(orderId, newStatus, null, "Admin update");
    }

    /**
     * Admin update status with admin ID and reason.
     *
     * @param orderId the order ID
     * @param newStatus the target status
     * @param adminId the admin user ID
     * @param reason the reason for the change
     * @return the updated order
     */
    @Transactional
    public OrderDTO adminUpdateStatus(UUID orderId, OrderStatus newStatus, UUID adminId, String reason) {
        return updateStatus(orderId, newStatus, adminId, reason);
    }

    /**
     * Soft delete an order.
     *
     * @param orderId the order ID
     */
    @Transactional
    public void softDeleteOrder(UUID orderId) {
        int updated = orderRepository.softDelete(orderId, Instant.now());
        if (updated == 0) {
            throw new OrderNotFoundException(orderId);
        }
        log.info("Soft deleted order {}", orderId);
    }

    /**
     * Get all orders (admin).
     *
     * @return list of all orders
     */
    @Transactional(readOnly = true)
    public List<OrderDTO> getAllOrders() {
        List<OrderEntity> orders = orderRepository.findAllNotDeleted();
        return orderMapper.toDTOList(orders);
    }

    /**
     * Get orders by status (admin).
     *
     * @param status the order status
     * @return list of orders with the specified status
     */
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByStatus(OrderStatus status) {
        List<OrderEntity> orders = orderRepository.findByStatusAndNotDeleted(status);
        return orderMapper.toDTOList(orders);
    }

    /**
     * Get pending orders for fulfillment (admin/warehouse).
     *
     * @return list of pending orders
     */
    @Transactional(readOnly = true)
    public List<OrderDTO> getPendingOrders() {
        List<OrderEntity> orders = orderRepository.findPendingOrders();
        return orderMapper.toDTOList(orders);
    }

    /**
     * Get orders awaiting fulfillment (CONFIRMED or PROCESSING).
     *
     * @return list of orders awaiting fulfillment
     */
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersAwaitingFulfillment() {
        List<OrderEntity> orders = orderRepository.findOrdersAwaitingFulfillment();
        return orderMapper.toDTOList(orders);
    }

    /**
     * Cancel an order if allowed.
     *
     * @param orderId the order ID
     * @param userId the user ID (for ownership validation)
     * @param reason the cancellation reason
     * @return the cancelled order
     */
    @Transactional
    public OrderDTO cancelOrder(UUID orderId, UUID userId, String reason) {
        OrderEntity order = orderRepository.findByIdAndUserIdAndNotDeleted(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId, userId));

        if (!order.getStatus().isCancellable()) {
            throw new InvalidOrderStateException(
                    String.format("Order %s cannot be cancelled in status %s", orderId, order.getStatus())
            );
        }

        stateMachine.transition(order, OrderStatus.CANCELLED, userId, reason);
        order = orderRepository.save(order);

        log.info("Cancelled order {} by user {}: {}", orderId, userId, reason);

        return orderMapper.toDTO(order);
    }
}
