package com.shop.ecommerceengine.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for refunding a payment.
 */
public record PaymentRefundDTO(
        @NotBlank(message = "Refund reason is required")
        String reason
) {
}
