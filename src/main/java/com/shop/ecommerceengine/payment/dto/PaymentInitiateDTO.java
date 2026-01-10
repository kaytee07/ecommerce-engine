package com.shop.ecommerceengine.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO for initiating a payment.
 */
public record PaymentInitiateDTO(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotBlank(message = "Idempotency key is required")
        String idempotencyKey,

        String callbackUrl
) {
}
