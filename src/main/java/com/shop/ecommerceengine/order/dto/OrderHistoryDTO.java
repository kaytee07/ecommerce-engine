package com.shop.ecommerceengine.order.dto;

import com.shop.ecommerceengine.order.entity.OrderStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for order history summary (lighter than full OrderDTO).
 * Used for listing user's order history.
 */
public record OrderHistoryDTO(
        UUID id,
        OrderStatus status,
        String statusDisplayName,
        int itemCount,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) implements Serializable {
}
