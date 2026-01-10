package com.shop.ecommerceengine.payment.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.service.UserService;
import com.shop.ecommerceengine.payment.dto.PaymentDTO;
import com.shop.ecommerceengine.payment.dto.PaymentRefundDTO;
import com.shop.ecommerceengine.payment.entity.PaymentStatus;
import com.shop.ecommerceengine.payment.service.PaymentService;
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
 * Controller for admin payment operations.
 * Requires admin roles for access.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@Tag(name = "Admin Payments", description = "Admin payment management operations")
public class AdminPaymentController {

    private static final Logger log = LoggerFactory.getLogger(AdminPaymentController.class);

    private final PaymentService paymentService;
    private final UserService userService;

    public AdminPaymentController(PaymentService paymentService, UserService userService) {
        this.paymentService = paymentService;
        this.userService = userService;
    }

    /**
     * Get all payments.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_SUPPORT_AGENT', 'ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get all payments", description = "Returns all payments (admin view)")
    public ResponseEntity<ApiResponse<List<PaymentDTO>>> getAllPayments() {
        List<PaymentDTO> payments = paymentService.getAllPayments();
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    /**
     * Get payment by ID.
     */
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('ROLE_SUPPORT_AGENT', 'ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get payment details", description = "Returns payment details (admin view)")
    public ResponseEntity<ApiResponse<PaymentDTO>> getPayment(
            @Parameter(description = "Payment UUID")
            @PathVariable UUID paymentId) {

        PaymentDTO payment = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    /**
     * Get payments by status.
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('ROLE_SUPPORT_AGENT', 'ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get payments by status", description = "Returns payments filtered by status")
    public ResponseEntity<ApiResponse<List<PaymentDTO>>> getPaymentsByStatus(
            @Parameter(description = "Payment status")
            @PathVariable PaymentStatus status) {

        List<PaymentDTO> payments = paymentService.getPaymentsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    /**
     * Get payments for an order.
     */
    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('ROLE_SUPPORT_AGENT', 'ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get payments for order", description = "Returns all payments for an order")
    public ResponseEntity<ApiResponse<List<PaymentDTO>>> getPaymentsForOrder(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId) {

        List<PaymentDTO> payments = paymentService.getPaymentsByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    /**
     * Refund a payment.
     */
    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasAnyRole('ROLE_SUPPORT_AGENT', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Refund payment", description = "Processes a refund for a payment")
    public ResponseEntity<ApiResponse<PaymentDTO>> refundPayment(
            @Parameter(description = "Payment UUID")
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentRefundDTO request,
            Authentication authentication) {

        UUID adminId = getAdminId(authentication);

        PaymentDTO payment = paymentService.refundPayment(paymentId, adminId, request.reason());

        log.info("Admin {} refunded payment {}: {}", adminId, paymentId, request.reason());

        return ResponseEntity.ok(ApiResponse.success(payment, "Payment refunded successfully"));
    }

    /**
     * Verify payment status with gateway.
     */
    @PostMapping("/{paymentId}/verify")
    @PreAuthorize("hasAnyRole('ROLE_SUPPORT_AGENT', 'ROLE_CONTENT_MANAGER', 'ROLE_SUPER_ADMIN')")
    @Operation(summary = "Verify payment", description = "Verifies payment status with the gateway")
    public ResponseEntity<ApiResponse<PaymentDTO>> verifyPayment(
            @Parameter(description = "Payment UUID")
            @PathVariable UUID paymentId,
            Authentication authentication) {

        UUID adminId = getAdminId(authentication);

        PaymentDTO existingPayment = paymentService.getPaymentById(paymentId);
        PaymentDTO payment = paymentService.verifyPayment(existingPayment.transactionRef());

        log.info("Admin {} verified payment {}", adminId, paymentId);

        return ResponseEntity.ok(ApiResponse.success(payment, "Payment verified"));
    }

    /**
     * Soft delete a payment.
     */
    @DeleteMapping("/{paymentId}")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Delete payment", description = "Soft deletes a payment (SUPER_ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> deletePayment(
            @Parameter(description = "Payment UUID")
            @PathVariable UUID paymentId,
            Authentication authentication) {

        UUID adminId = getAdminId(authentication);

        paymentService.softDeletePayment(paymentId);

        log.info("Admin {} soft deleted payment {}", adminId, paymentId);

        return ResponseEntity.ok(ApiResponse.success(null, "Payment deleted"));
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
