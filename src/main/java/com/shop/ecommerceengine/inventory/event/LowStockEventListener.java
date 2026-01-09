package com.shop.ecommerceengine.inventory.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event listener for low stock alerts.
 * Processes low stock events asynchronously.
 */
@Component
public class LowStockEventListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockEventListener.class);

    /**
     * Handles low stock events asynchronously.
     * In production, this would send email notifications or queue messages.
     */
    @Async
    @EventListener
    public void handleLowStockEvent(LowStockEvent event) {
        log.warn("LOW STOCK ALERT: Product {} has {} units remaining (threshold: {})",
                event.getProductId(),
                event.getCurrentStock(),
                event.getThreshold());

        // In production, you would:
        // 1. Send email notification to warehouse manager
        // 2. Queue message for inventory management system
        // 3. Create alert in admin dashboard
        // 4. Trigger auto-reorder if configured
    }
}
