package com.shop.ecommerceengine.payment.service;

import com.shop.ecommerceengine.payment.dto.PaymentDTO;
import com.shop.ecommerceengine.payment.dto.PaymentInitiateDTO;
import com.shop.ecommerceengine.payment.entity.PaymentEntity;
import com.shop.ecommerceengine.payment.entity.PaymentGateway;
import com.shop.ecommerceengine.payment.entity.PaymentStatus;
import com.shop.ecommerceengine.payment.exception.IdempotencyKeyViolationException;
import com.shop.ecommerceengine.payment.exception.PaymentFailedException;
import com.shop.ecommerceengine.payment.exception.PaymentNotFoundException;
import com.shop.ecommerceengine.payment.gateway.HubtelPaymentClient;
import com.shop.ecommerceengine.payment.gateway.PaymentGatewayClient;
import com.shop.ecommerceengine.payment.gateway.PaystackPaymentClient;
import com.shop.ecommerceengine.payment.mapper.PaymentMapper;
import com.shop.ecommerceengine.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for payment operations.
 * Handles payment initiation with Hubtel (primary) and Paystack (fallback).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final IdempotencyService idempotencyService;
    private final OrderServiceInterface orderService;
    private final HubtelPaymentClient hubtelClient;
    private final PaystackPaymentClient paystackClient;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentMapper paymentMapper,
                          IdempotencyService idempotencyService,
                          OrderServiceInterface orderService,
                          HubtelPaymentClient hubtelClient,
                          PaystackPaymentClient paystackClient,
                          ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
        this.idempotencyService = idempotencyService;
        this.orderService = orderService;
        this.hubtelClient = hubtelClient;
        this.paystackClient = paystackClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Initiate a payment for an order.
     * Uses Hubtel as primary gateway, falls back to Paystack if Hubtel fails.
     */
    @Transactional
    public PaymentDTO initiatePayment(PaymentInitiateDTO request, UUID userId) {
        // Check for existing payment with this idempotency key
        Optional<PaymentEntity> existingPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingPayment.isPresent()) {
            PaymentEntity payment = existingPayment.get();
            // If same order, return existing payment (idempotent)
            if (payment.getOrderId().equals(request.orderId())) {
                log.info("Returning existing payment for idempotency key: {}", request.idempotencyKey());
                return paymentMapper.toDTO(payment);
            }
            // Different order - violation
            throw new IdempotencyKeyViolationException(request.idempotencyKey());
        }

        // Try Redis idempotency check
        if (!idempotencyService.tryAcquire(request.idempotencyKey(), request.orderId())) {
            // Check if it's for a different order
            UUID existingOrderId = idempotencyService.getOrderIdForKey(request.idempotencyKey());
            if (existingOrderId != null && !existingOrderId.equals(request.orderId())) {
                throw new IdempotencyKeyViolationException(request.idempotencyKey());
            }
            // Same order - check DB for existing payment
            Optional<PaymentEntity> dbPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
            if (dbPayment.isPresent()) {
                return paymentMapper.toDTO(dbPayment.get());
            }
        }

        // Validate order
        OrderServiceInterface.OrderSnapshot orderSnapshot = orderService.getOrderSnapshot(request.orderId());
        if (!orderSnapshot.payable()) {
            throw new PaymentFailedException(request.orderId(), "Order is not in a payable state");
        }

        // Verify user owns the order
        if (!orderSnapshot.userId().equals(userId)) {
            throw new PaymentFailedException(request.orderId(), "User does not own this order");
        }

        BigDecimal amount = orderSnapshot.amount();
        String currency = orderSnapshot.currency();

        // Try Hubtel first, then Paystack
        PaymentGatewayClient.PaymentInitiationResponse response = null;
        PaymentGateway usedGateway = null;
        Exception lastException = null;

        // Try Hubtel (primary)
        if (hubtelClient.isEnabled()) {
            try {
                response = hubtelClient.initiatePayment(createGatewayRequest(request, amount, currency));
                if (response.success()) {
                    usedGateway = PaymentGateway.HUBTEL;
                    log.info("Payment initiated via Hubtel: {}", response.transactionRef());
                }
            } catch (Exception e) {
                log.warn("Hubtel payment initiation failed, trying Paystack: {}", e.getMessage());
                lastException = e;
            }
        }

        // Try Paystack (fallback)
        if (usedGateway == null && paystackClient.isEnabled()) {
            try {
                response = paystackClient.initiatePayment(createGatewayRequest(request, amount, currency));
                if (response.success()) {
                    usedGateway = PaymentGateway.PAYSTACK;
                    log.info("Payment initiated via Paystack: {}", response.transactionRef());
                }
            } catch (Exception e) {
                log.error("Paystack payment initiation also failed: {}", e.getMessage());
                lastException = e;
            }
        }

        // Check if any gateway succeeded
        if (usedGateway == null || response == null || !response.success()) {
            // Remove idempotency key on failure
            idempotencyService.remove(request.idempotencyKey());

            String errorMessage = response != null ? response.errorMessage() : "Gateway unavailable";
            if (lastException != null) {
                errorMessage = "All payment gateways failed: " + lastException.getMessage();
            }
            throw new PaymentFailedException(request.orderId(), errorMessage);
        }

        // Create payment entity
        PaymentEntity payment = new PaymentEntity(
                request.orderId(),
                userId,
                amount,
                request.idempotencyKey(),
                usedGateway
        );
        payment.setTransactionRef(response.transactionRef());
        payment.setCheckoutUrl(response.checkoutUrl());
        payment.setGatewayResponse(response.rawResponse());

        payment = paymentRepository.save(payment);

        log.info("Payment {} created for order {} via {} - Checkout: {}",
                payment.getId(), request.orderId(), usedGateway, response.checkoutUrl());

        return paymentMapper.toDTO(payment);
    }

    /**
     * Verify a payment status with the gateway.
     */
    @Transactional
    public PaymentDTO verifyPayment(String transactionRef) {
        PaymentEntity payment = paymentRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new PaymentNotFoundException(transactionRef));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already in terminal state: {}", payment.getId(), payment.getStatus());
            return paymentMapper.toDTO(payment);
        }

        // Get appropriate gateway client
        PaymentGatewayClient client = payment.getGateway() == PaymentGateway.HUBTEL ? hubtelClient : paystackClient;

        PaymentGatewayClient.PaymentVerificationResponse response = client.verifyPayment(transactionRef);

        if (response.isPaid()) {
            payment.markSuccess(transactionRef, response.rawResponse());
            payment = paymentRepository.save(payment);

            // Update order status
            orderService.markOrderPaid(payment.getOrderId(), payment.getId());

            // Publish event
            eventPublisher.publishEvent(new OrderPaidEvent(this, payment.getOrderId(), payment.getId()));

            log.info("Payment {} verified as SUCCESS", payment.getId());
        } else if (response.isFailed()) {
            payment.markFailed(response.errorMessage(), response.rawResponse());
            payment = paymentRepository.save(payment);

            log.info("Payment {} verified as FAILED: {}", payment.getId(), response.errorMessage());
        }

        return paymentMapper.toDTO(payment);
    }

    /**
     * Process Hubtel webhook callback.
     */
    @Transactional
    public void processHubtelWebhook(String transactionRef, String status, String message) {
        log.info("Processing Hubtel webhook: {} - {} - {}", transactionRef, status, message);

        PaymentEntity payment = paymentRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> new PaymentNotFoundException(transactionRef));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed, ignoring webhook", payment.getId());
            return;
        }

        if ("success".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
            payment.markSuccess(transactionRef, Map.of("webhookStatus", status, "message", message));
            payment = paymentRepository.save(payment);

            orderService.markOrderPaid(payment.getOrderId(), payment.getId());
            eventPublisher.publishEvent(new OrderPaidEvent(this, payment.getOrderId(), payment.getId()));

            log.info("Hubtel webhook: Payment {} marked as SUCCESS", payment.getId());
        } else if ("failed".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status)) {
            payment.markFailed(message, Map.of("webhookStatus", status, "message", message));
            paymentRepository.save(payment);

            log.info("Hubtel webhook: Payment {} marked as FAILED", payment.getId());
        }
    }

    /**
     * Process Paystack webhook callback.
     */
    @Transactional
    public void processPaystackWebhook(String reference, String status) {
        log.info("Processing Paystack webhook: {} - {}", reference, status);

        PaymentEntity payment = paymentRepository.findByTransactionRef(reference)
                .orElseThrow(() -> new PaymentNotFoundException(reference));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed, ignoring webhook", payment.getId());
            return;
        }

        if ("success".equalsIgnoreCase(status)) {
            payment.markSuccess(reference, Map.of("webhookStatus", status));
            payment = paymentRepository.save(payment);

            orderService.markOrderPaid(payment.getOrderId(), payment.getId());
            eventPublisher.publishEvent(new OrderPaidEvent(this, payment.getOrderId(), payment.getId()));

            log.info("Paystack webhook: Payment {} marked as SUCCESS", payment.getId());
        } else if ("failed".equalsIgnoreCase(status)) {
            payment.markFailed("Payment failed", Map.of("webhookStatus", status));
            paymentRepository.save(payment);

            log.info("Paystack webhook: Payment {} marked as FAILED", payment.getId());
        }
    }

    /**
     * Refund a payment (admin operation).
     */
    @Transactional
    public PaymentDTO refundPayment(UUID paymentId, UUID adminId, String reason) {
        PaymentEntity payment = paymentRepository.findByIdAndNotDeleted(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (!payment.getStatus().isRefundable()) {
            throw new PaymentFailedException("Cannot refund payment in status: " + payment.getStatus());
        }

        // Get appropriate gateway client
        PaymentGatewayClient client = payment.getGateway() == PaymentGateway.HUBTEL ? hubtelClient : paystackClient;

        PaymentGatewayClient.RefundResponse response = client.refundPayment(
                payment.getTransactionRef(),
                payment.getAmount(),
                reason
        );

        if (!response.success()) {
            throw new PaymentFailedException("Refund failed: " + response.errorMessage());
        }

        payment.markRefunded(adminId, reason);
        payment = paymentRepository.save(payment);

        // Update order status
        orderService.markOrderRefunded(payment.getOrderId(), payment.getId(), reason);

        log.info("Payment {} refunded by admin {} - Reason: {}", paymentId, adminId, reason);

        return paymentMapper.toDTO(payment);
    }

    /**
     * Get payment by ID.
     */
    @Transactional(readOnly = true)
    public PaymentDTO getPaymentById(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findByIdAndNotDeleted(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return paymentMapper.toDTO(payment);
    }

    /**
     * Get payments for an order.
     */
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByOrderId(UUID orderId) {
        return paymentMapper.toDTOList(paymentRepository.findByOrderIdAndNotDeleted(orderId));
    }

    /**
     * Get payments for a user.
     */
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByUserId(UUID userId) {
        return paymentMapper.toDTOList(paymentRepository.findByUserIdAndNotDeleted(userId));
    }

    /**
     * Soft delete a payment.
     */
    @Transactional
    public void softDeletePayment(UUID paymentId) {
        int updated = paymentRepository.softDelete(paymentId, Instant.now());
        if (updated == 0) {
            throw new PaymentNotFoundException(paymentId);
        }
        log.info("Soft deleted payment: {}", paymentId);
    }

    /**
     * Get all payments (admin).
     */
    @Transactional(readOnly = true)
    public List<PaymentDTO> getAllPayments() {
        return paymentMapper.toDTOList(paymentRepository.findAllNotDeleted());
    }

    /**
     * Get payments by status (admin).
     */
    @Transactional(readOnly = true)
    public List<PaymentDTO> getPaymentsByStatus(PaymentStatus status) {
        return paymentMapper.toDTOList(paymentRepository.findByStatusAndNotDeleted(status));
    }

    // ==================== Helper Methods ====================

    private PaymentGatewayClient.PaymentInitiationRequest createGatewayRequest(
            PaymentInitiateDTO dto,
            BigDecimal amount,
            String currency) {
        return new PaymentGatewayClient.PaymentInitiationRequest(
                dto.idempotencyKey(), // Use idempotency key as client reference
                amount,
                currency,
                "Order payment",
                null, // customerEmail - can be enhanced to get from user
                null, // customerName
                null, // customerPhone
                dto.callbackUrl(),
                null, // returnUrl
                Map.of("orderId", dto.orderId().toString())
        );
    }

    // ==================== Event Classes ====================

    /**
     * Event published when an order is paid.
     */
    public static class OrderPaidEvent extends org.springframework.context.ApplicationEvent {
        private final UUID orderId;
        private final UUID paymentId;

        public OrderPaidEvent(Object source, UUID orderId, UUID paymentId) {
            super(source);
            this.orderId = orderId;
            this.paymentId = paymentId;
        }

        public UUID getOrderId() {
            return orderId;
        }

        public UUID getPaymentId() {
            return paymentId;
        }
    }
}
