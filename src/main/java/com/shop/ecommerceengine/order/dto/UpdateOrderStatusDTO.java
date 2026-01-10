package com.shop.ecommerceengine.order.dto;

import com.shop.ecommerceengine.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * DTO for updating order status.
 */
public record UpdateOrderStatusDTO(
        @NotNull(message = "Status is required")
        OrderStatus status,

        String reason
) implements Serializable {
}
