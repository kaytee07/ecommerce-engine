package com.shop.ecommerceengine.inventory.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Event published when stock falls below threshold.
 */
public class LowStockEvent extends ApplicationEvent {

    private final UUID productId;
    private final Integer currentStock;
    private final Integer threshold;

    public LowStockEvent(Object source, UUID productId, Integer currentStock, Integer threshold) {
        super(source);
        this.productId = productId;
        this.currentStock = currentStock;
        this.threshold = threshold;
    }

    public UUID getProductId() {
        return productId;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public Integer getThreshold() {
        return threshold;
    }

    @Override
    public String toString() {
        return String.format("LowStockEvent{productId=%s, currentStock=%d, threshold=%d}",
                productId, currentStock, threshold);
    }
}
