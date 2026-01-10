package com.shop.ecommerceengine.order.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for order item details.
 */
public record OrderItemDTO(
        UUID productId,
        String productName,
        String sku,
        int quantity,
        BigDecimal priceAtOrder,
        BigDecimal subtotal
) implements Serializable {
}
