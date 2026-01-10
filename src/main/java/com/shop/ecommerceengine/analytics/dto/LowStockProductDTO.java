package com.shop.ecommerceengine.analytics.dto;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for products with low stock levels.
 */
public record LowStockProductDTO(
    UUID inventoryId,
    UUID productId,
    String productName,
    String sku,
    int stockQuantity,
    int reservedQuantity,
    int availableQuantity
) implements Serializable {

    /**
     * Check if product is critically low (below threshold).
     */
    public boolean isCriticallyLow(int threshold) {
        return availableQuantity < threshold;
    }

    /**
     * Check if product is out of stock.
     */
    public boolean isOutOfStock() {
        return availableQuantity <= 0;
    }
}
