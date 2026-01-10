package com.shop.ecommerceengine.cart.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for cart item information.
 */
public record CartItemDTO(
        UUID productId,
        String productName,
        int quantity,
        BigDecimal priceAtAdd,
        BigDecimal subtotal
) implements Serializable {
}
