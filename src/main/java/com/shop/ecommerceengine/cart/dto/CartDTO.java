package com.shop.ecommerceengine.cart.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for cart information.
 */
public record CartDTO(
        UUID id,
        UUID userId,
        List<CartItemDTO> items,
        int itemCount,
        BigDecimal totalAmount,
        LocalDateTime updatedAt
) implements Serializable {

    /**
     * Create an empty cart for a user.
     */
    public static CartDTO empty(UUID userId) {
        return new CartDTO(
                null,
                userId,
                List.of(),
                0,
                BigDecimal.ZERO,
                null
        );
    }
}
