package com.shop.ecommerceengine.payment.gateway;

import com.shop.ecommerceengine.payment.entity.PaymentGateway;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Interface for payment gateway clients.
 * Abstracts the differences between Hubtel and Paystack.
 */
public interface PaymentGatewayClient {

    /**
     * Get the gateway type.
     */
    PaymentGateway getGateway();

    /**
     * Check if gateway is enabled.
     */
    boolean isEnabled();

    /**
     * Initiate a payment.
     *
     * @param request Payment initiation request
     * @return Payment initiation response with checkout URL
     */
    PaymentInitiationResponse initiatePayment(PaymentInitiationRequest request);

    /**
     * Verify a payment status.
     *
     * @param transactionRef The transaction reference to verify
     * @return Payment verification response
     */
    PaymentVerificationResponse verifyPayment(String transactionRef);

    /**
     * Process a refund.
     *
     * @param transactionRef The transaction reference to refund
     * @param amount The amount to refund
     * @param reason Reason for refund
     * @return Refund response
     */
    RefundResponse refundPayment(String transactionRef, BigDecimal amount, String reason);

    /**
     * Validate webhook signature.
     *
     * @param payload The webhook payload
     * @param signature The signature header value
     * @return true if valid
     */
    boolean validateWebhookSignature(String payload, String signature);

    // ==================== Request/Response Records ====================

    record PaymentInitiationRequest(
            String clientReference,
            BigDecimal amount,
            String currency,
            String description,
            String customerEmail,
            String customerName,
            String customerPhone,
            String callbackUrl,
            String returnUrl,
            Map<String, String> metadata
    ) {
    }

    record PaymentInitiationResponse(
            boolean success,
            String transactionRef,
            String checkoutUrl,
            String errorMessage,
            Map<String, Object> rawResponse
    ) {
    }

    record PaymentVerificationResponse(
            boolean success,
            String transactionRef,
            String status,
            BigDecimal amount,
            String currency,
            String errorMessage,
            Map<String, Object> rawResponse
    ) {
        public boolean isPaid() {
            return success && ("success".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status));
        }

        public boolean isFailed() {
            return !success || "failed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status);
        }
    }

    record RefundResponse(
            boolean success,
            String refundId,
            String errorMessage,
            Map<String, Object> rawResponse
    ) {
    }
}
