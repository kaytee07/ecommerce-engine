package com.shop.ecommerceengine.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * DTO for adding an item to cart.
 */
public record AddToCartDTO(
        @NotNull(message = "Product ID is required")
        UUID productId,

        @Positive(message = "Quantity must be positive")
        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity
) {
}
