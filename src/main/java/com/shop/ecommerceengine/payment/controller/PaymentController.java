package com.shop.ecommerceengine.payment.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.service.UserService;
import com.shop.ecommerceengine.payment.dto.PaymentDTO;
import com.shop.ecommerceengine.payment.dto.PaymentInitiateDTO;
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
 * Controller for customer payment operations.
 * Requires ROLE_USER authentication.
 */
@RestController
@RequestMapping("/api/v1/store/payments")
@Tag(name = "Payments", description = "Customer payment operations")
@PreAuthorize("hasRole('ROLE_USER')")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final UserService userService;

    public PaymentController(PaymentService paymentService, UserService userService) {
        this.paymentService = paymentService;
        this.userService = userService;
    }

    /**
     * Initiate a payment for an order.
     */
    @PostMapping("/{orderId}/initiate")
    @Operation(summary = "Initiate payment", description = "Initiates a payment for an order")
    public ResponseEntity<ApiResponse<PaymentDTO>> initiatePayment(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId,
            @Valid @RequestBody PaymentInitiateDTO request,
            Authentication authentication) {

        UUID userId = getUserId(authentication);

        // Ensure orderId in path matches request
        PaymentInitiateDTO effectiveRequest = new PaymentInitiateDTO(
                orderId,
                request.idempotencyKey(),
                request.callbackUrl()
        );

        PaymentDTO payment = paymentService.initiatePayment(effectiveRequest, userId);

        log.info("User {} initiated payment {} for order {}", userId, payment.id(), orderId);

        return ResponseEntity.ok(ApiResponse.success(payment, "Payment initiated. Redirect to checkout URL."));
    }

    /**
     * Get payment status.
     */
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment status", description = "Returns payment details")
    public ResponseEntity<ApiResponse<PaymentDTO>> getPayment(
            @Parameter(description = "Payment UUID")
            @PathVariable UUID paymentId,
            Authentication authentication) {

        UUID userId = getUserId(authentication);
        PaymentDTO payment = paymentService.getPaymentById(paymentId);

        // Verify user owns this payment
        if (!payment.userId().equals(userId)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied"));
        }

        return ResponseEntity.ok(ApiResponse.success(payment));
    }

    /**
     * Get payments for an order.
     */
    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payments for order", description = "Returns all payments for an order")
    public ResponseEntity<ApiResponse<List<PaymentDTO>>> getPaymentsForOrder(
            @Parameter(description = "Order UUID")
            @PathVariable UUID orderId,
            Authentication authentication) {

        List<PaymentDTO> payments = paymentService.getPaymentsByOrderId(orderId);

        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    /**
     * Get user's payment history.
     */
    @GetMapping("/my")
    @Operation(summary = "Get payment history", description = "Returns the current user's payment history")
    public ResponseEntity<ApiResponse<List<PaymentDTO>>> getMyPayments(Authentication authentication) {

        UUID userId = getUserId(authentication);
        List<PaymentDTO> payments = paymentService.getPaymentsByUserId(userId);

        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    /**
     * Verify payment status (poll after redirect from checkout).
     */
    @GetMapping("/{paymentId}/verify")
    @Operation(summary = "Verify payment", description = "Verifies payment status with gateway")
    public ResponseEntity<ApiResponse<PaymentDTO>> verifyPayment(
            @Parameter(description = "Payment UUID")
            @PathVariable UUID paymentId,
            Authentication authentication) {

        UUID userId = getUserId(authentication);
        PaymentDTO existingPayment = paymentService.getPaymentById(paymentId);

        // Verify user owns this payment
        if (!existingPayment.userId().equals(userId)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied"));
        }

        PaymentDTO payment = paymentService.verifyPayment(existingPayment.transactionRef());

        return ResponseEntity.ok(ApiResponse.success(payment));
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
