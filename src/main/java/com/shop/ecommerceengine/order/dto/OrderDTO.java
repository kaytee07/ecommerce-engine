package com.shop.ecommerceengine.order.dto;

import com.shop.ecommerceengine.order.entity.OrderStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for full order details.
 */
public record OrderDTO(
        UUID id,
        UUID userId,
        OrderStatus status,
        List<OrderItemDTO> items,
        int itemCount,
        BigDecimal totalAmount,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements Serializable {

    /**
     * Create an empty order DTO for a user.
     */
    public static OrderDTO empty(UUID userId) {
        return new OrderDTO(
                null,
                userId,
                OrderStatus.PENDING,
                List.of(),
                0,
                BigDecimal.ZERO,
                null,
                null,
                null
        );
    }
}
