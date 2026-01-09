package com.shop.ecommerceengine.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for inventory stock adjustment requests.
 *
 * @param quantity The quantity change (positive for restock, negative for sale)
 * @param adjustmentType The type of adjustment (SALE, RESTOCK, ADJUSTMENT, RESERVE, RELEASE)
 * @param reason The reason for the adjustment
 */
public record InventoryAdjustDTO(
        @NotNull(message = "Quantity is required")
        Integer quantity,

        @NotBlank(message = "Adjustment type is required")
        String adjustmentType,

        String reason
) {
}
