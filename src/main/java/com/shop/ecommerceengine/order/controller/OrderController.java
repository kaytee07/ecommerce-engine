package com.shop.ecommerceengine.order.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.service.UserService;
import com.shop.ecommerceengine.order.dto.OrderDTO;
import com.shop.ecommerceengine.order.dto.OrderHistoryDTO;
import com.shop.ecommerceengine.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for customer order operations.
 * Requires ROLE_USER authentication.
 */
@RestController
@RequestMapping("/api/v1/store/orders")
@Tag(name = "Orders", description = "Customer order operations")
@PreAuthorize("hasRole('ROLE_USER')")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final UserService userService;

    public OrderController(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    /**
     * Create an order from the user's current cart.
     */
    @PostMapping
    @Operation(summary = "Create order from cart", description = "Creates a new order from the current cart contents")
    public ResponseEntity<ApiResponse<OrderDTO>> createOrder(Authentication authentication) {
        UUID userId = getUserId(authentication);

        OrderDTO order = orderService.createOrderFromCart(userId);

        log.info("User {} created order {}", userId, order.id());

        return ResponseEntity.ok(ApiResponse.success(order, "Order created successfully"));
    }

    /**
     * Get current user's order history.
     */
    @GetMapping("/my")
    @Operation(summary = "Get order history", description = "Returns the current user's order history")
    public ResponseEntity<ApiResponse<List<OrderHistoryDTO>>> getMyOrders(Authentication authentication) {
        UUID userId = getUserId(authentication);

        List<OrderHistoryDTO> orders = orderService.getUserOrders(userId);

        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    /**
     * Get a specific order by ID.
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "Get order details", description = "Returns details of a specific order")
    public ResponseEntity<ApiResponse<OrderDTO>> getOrder(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId,
            Authentication authentication) {

        UUID userId = getUserId(authentication);

        OrderDTO order = orderService.getOrderById(orderId, userId);

        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /**
     * Cancel an order.
     */
    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel order", description = "Cancels an order if it's in a cancellable state")
    public ResponseEntity<ApiResponse<OrderDTO>> cancelOrder(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId,
            @RequestParam(required = false, defaultValue = "Customer requested cancellation")
            String reason,
            Authentication authentication) {

        UUID userId = getUserId(authentication);

        OrderDTO order = orderService.cancelOrder(orderId, userId, reason);

        log.info("User {} cancelled order {}", userId, orderId);

        return ResponseEntity.ok(ApiResponse.success(order, "Order cancelled successfully"));
    }

    // ===================== Private helpers =====================

    private UUID getUserId(Authentication authentication) {
        try {
            return userService.getUserByUsername(authentication.getName()).id();
        } catch (Exception e) {
            log.error("Failed to get user ID for {}: {}", authentication.getName(), e.getMessage());
            throw new RuntimeException("Unable to identify user");
        }
    }
}
