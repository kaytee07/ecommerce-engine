package com.shop.ecommerceengine.order.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.service.UserService;
import com.shop.ecommerceengine.order.dto.OrderDTO;
import com.shop.ecommerceengine.order.dto.UpdateOrderStatusDTO;
import com.shop.ecommerceengine.order.entity.OrderStatus;
import com.shop.ecommerceengine.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for admin order operations.
 * Requires ROLE_WAREHOUSE, ROLE_CONTENT_MANAGER, or ROLE_SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin Orders", description = "Admin order management operations")
public class AdminOrderController {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderController.class);

    private final OrderService orderService;
    private final UserService userService;

    public AdminOrderController(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    /**
     * Get all orders.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_WAREHOUSE', 'ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get all orders", description = "Returns all orders (admin view)")
    public ResponseEntity<ApiResponse<List<OrderDTO>>> getAllOrders() {
        List<OrderDTO> orders = orderService.getAllOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    /**
     * Get order by ID (admin - no ownership check).
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ROLE_WAREHOUSE', 'ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get order details", description = "Returns order details (admin view)")
    public ResponseEntity<ApiResponse<OrderDTO>> getOrder(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId) {

        OrderDTO order = orderService.getOrderByIdAdmin(orderId);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /**
     * Get orders by status.
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ROLE_WAREHOUSE', 'ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get orders by status", description = "Returns orders filtered by status")
    public ResponseEntity<ApiResponse<List<OrderDTO>>> getOrdersByStatus(
            @Parameter(description = "Order status")
            @PathVariable OrderStatus status) {

        List<OrderDTO> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    /**
     * Get pending orders (for fulfillment).
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ROLE_WAREHOUSE', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get pending orders", description = "Returns orders pending fulfillment")
    public ResponseEntity<ApiResponse<List<OrderDTO>>> getPendingOrders() {
        List<OrderDTO> orders = orderService.getPendingOrders();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    /**
     * Get orders awaiting fulfillment (CONFIRMED or PROCESSING).
     */
    @GetMapping("/fulfillment")
    @PreAuthorize("hasAnyRole('ROLE_WAREHOUSE', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get orders awaiting fulfillment", description = "Returns confirmed/processing orders")
    public ResponseEntity<ApiResponse<List<OrderDTO>>> getOrdersAwaitingFulfillment() {
        List<OrderDTO> orders = orderService.getOrdersAwaitingFulfillment();
        return ResponseEntity.ok(ApiResponse.success(orders));
    }

    /**
     * Update order status.
     */
    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('ROLE_WAREHOUSE', 'ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Update order status", description = "Updates the status of an order")
    public ResponseEntity<ApiResponse<OrderDTO>> updateOrderStatus(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusDTO request,
            Authentication authentication) {

        UUID adminId = getAdminId(authentication);

        OrderDTO order = orderService.adminUpdateStatus(orderId, request.status(), adminId, request.reason());

        log.info("Admin {} updated order {} status to {}", adminId, orderId, request.status());

        return ResponseEntity.ok(ApiResponse.success(order, "Order status updated"));
    }

    /**
     * Fulfill an order (transition to SHIPPED).
     */
    @PutMapping("/{orderId}/fulfill")
    @PreAuthorize("hasAnyRole('ROLE_WAREHOUSE', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Fulfill order", description = "Marks an order as shipped")
    public ResponseEntity<ApiResponse<OrderDTO>> fulfillOrder(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId,
            @RequestParam(required = false) String trackingNumber,
            Authentication authentication) {

        UUID adminId = getAdminId(authentication);

        // First confirm if pending
        OrderDTO currentOrder = orderService.getOrderByIdAdmin(orderId);
        if (currentOrder.status() == OrderStatus.PENDING) {
            orderService.adminUpdateStatus(orderId, OrderStatus.CONFIRMED, adminId, "Confirmed for fulfillment");
        }

        // Then process
        if (currentOrder.status() == OrderStatus.PENDING || currentOrder.status() == OrderStatus.CONFIRMED) {
            orderService.adminUpdateStatus(orderId, OrderStatus.PROCESSING, adminId, "Processing for shipment");
        }

        // Finally ship
        String reason = trackingNumber != null
                ? "Shipped with tracking: " + trackingNumber
                : "Shipped";
        OrderDTO order = orderService.adminUpdateStatus(orderId, OrderStatus.SHIPPED, adminId, reason);

        log.info("Admin {} fulfilled order {} - shipped", adminId, orderId);

        return ResponseEntity.ok(ApiResponse.success(order, "Order fulfilled and shipped"));
    }

    /**
     * Mark order as delivered.
     */
    @PutMapping("/{orderId}/deliver")
    @PreAuthorize("hasAnyRole('ROLE_WAREHOUSE', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Mark as delivered", description = "Marks an order as delivered")
    public ResponseEntity<ApiResponse<OrderDTO>> markDelivered(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId,
            Authentication authentication) {

        UUID adminId = getAdminId(authentication);

        OrderDTO order = orderService.adminUpdateStatus(orderId, OrderStatus.DELIVERED, adminId, "Marked as delivered");

        log.info("Admin {} marked order {} as delivered", adminId, orderId);

        return ResponseEntity.ok(ApiResponse.success(order, "Order marked as delivered"));
    }

    /**
     * Cancel an order (admin).
     */
    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Cancel order", description = "Cancels an order (admin)")
    public ResponseEntity<ApiResponse<OrderDTO>> cancelOrder(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId,
            @RequestParam(required = false, defaultValue = "Cancelled by admin")
            String reason,
            Authentication authentication) {

        UUID adminId = getAdminId(authentication);

        OrderDTO order = orderService.adminUpdateStatus(orderId, OrderStatus.CANCELLED, adminId, reason);

        log.info("Admin {} cancelled order {}: {}", adminId, orderId, reason);

        return ResponseEntity.ok(ApiResponse.success(order, "Order cancelled"));
    }

    /**
     * Soft delete an order.
     */
    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Delete order", description = "Soft deletes an order (SUPER_ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> deleteOrder(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId,
            Authentication authentication) {

        UUID adminId = getAdminId(authentication);

        orderService.softDeleteOrder(orderId);

        log.info("Admin {} soft deleted order {}", adminId, orderId);

        return ResponseEntity.ok(ApiResponse.success(null, "Order deleted"));
    }

    // ===================== Private helpers =====================

    private UUID getAdminId(Authentication authentication) {
        try {
            return userService.getUserByUsername(authentication.getName()).id();
        } catch (Exception e) {
            log.error("Failed to get admin ID for {}: {}", authentication.getName(), e.getMessage());
            throw new RuntimeException("Unable to identify admin user");
        }
    }
}
