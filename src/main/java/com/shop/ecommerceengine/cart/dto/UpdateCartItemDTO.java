package com.shop.ecommerceengine.cart.dto;

import jakarta.validation.constraints.Min;

/**
 * DTO for updating cart item quantity.
 */
public record UpdateCartItemDTO(
        @Min(value = 0, message = "Quantity cannot be negative")
        int quantity
) {
}
