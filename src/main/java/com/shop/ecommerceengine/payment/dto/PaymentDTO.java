package com.shop.ecommerceengine.payment.dto;

import com.shop.ecommerceengine.payment.entity.PaymentGateway;
import com.shop.ecommerceengine.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a payment.
 */
public record PaymentDTO(
        UUID id,
        UUID orderId,
        UUID userId,
        PaymentStatus status,
        PaymentGateway gateway,
        String transactionRef,
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        String checkoutUrl,
        String failureReason,
        String refundReason,
        UUID refundedBy,
        Instant refundedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
