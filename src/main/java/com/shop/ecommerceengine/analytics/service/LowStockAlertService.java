package com.shop.ecommerceengine.analytics.service;

import com.shop.ecommerceengine.analytics.dto.LowStockProductDTO;
import com.shop.ecommerceengine.analytics.repository.AnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for low stock alerts.
 * Monitors inventory levels and triggers alerts when products fall below threshold.
 */
@Service
public class LowStockAlertService {

    private static final Logger log = LoggerFactory.getLogger(LowStockAlertService.class);

    private final AnalyticsRepository analyticsRepository;

    @Value("${app.inventory.low-stock-threshold:5}")
    private int lowStockThreshold;

    @Value("${app.inventory.critical-stock-threshold:2}")
    private int criticalStockThreshold;

    public LowStockAlertService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    /**
     * Check for low stock products and trigger alerts.
     * Called by scheduled task.
     */
    public void checkAndAlertLowStock() {
        log.info("Checking for low stock products (threshold: {})", lowStockThreshold);

        List<LowStockProductDTO> lowStockProducts = analyticsRepository.getLowStockProducts(lowStockThreshold);

        if (lowStockProducts.isEmpty()) {
            log.info("No low stock products found");
            return;
        }

        log.warn("Found {} products with low stock", lowStockProducts.size());

        // Process alerts by severity
        List<LowStockProductDTO> outOfStock = lowStockProducts.stream()
            .filter(LowStockProductDTO::isOutOfStock)
            .toList();

        List<LowStockProductDTO> criticalStock = lowStockProducts.stream()
            .filter(p -> !p.isOutOfStock() && p.stockQuantity() <= criticalStockThreshold)
            .toList();

        List<LowStockProductDTO> lowStock = lowStockProducts.stream()
            .filter(p -> !p.isOutOfStock() && p.stockQuantity() > criticalStockThreshold)
            .toList();

        // Log alerts by severity
        if (!outOfStock.isEmpty()) {
            alertOutOfStock(outOfStock);
        }

        if (!criticalStock.isEmpty()) {
            alertCriticalStock(criticalStock);
        }

        if (!lowStock.isEmpty()) {
            alertLowStock(lowStock);
        }
    }

    /**
     * Alert for out of stock products (highest severity).
     */
    @Async
    public void alertOutOfStock(List<LowStockProductDTO> products) {
        log.error("OUT OF STOCK ALERT: {} products are out of stock!", products.size());

        for (LowStockProductDTO product : products) {
            log.error("  - {} (SKU: {}) - Stock: {}, Reserved: {}",
                product.productName(),
                product.sku(),
                product.stockQuantity(),
                product.reservedQuantity());
        }

        // In production: Send email/SMS/Slack notification
        sendNotification("OUT_OF_STOCK", products);
    }

    /**
     * Alert for critically low stock products.
     */
    @Async
    public void alertCriticalStock(List<LowStockProductDTO> products) {
        log.warn("CRITICAL STOCK ALERT: {} products are critically low!", products.size());

        for (LowStockProductDTO product : products) {
            log.warn("  - {} (SKU: {}) - Stock: {}, Available: {}",
                product.productName(),
                product.sku(),
                product.stockQuantity(),
                product.availableQuantity());
        }

        // In production: Send email notification
        sendNotification("CRITICAL_STOCK", products);
    }

    /**
     * Alert for low stock products.
     */
    @Async
    public void alertLowStock(List<LowStockProductDTO> products) {
        log.info("LOW STOCK ALERT: {} products are low on stock", products.size());

        for (LowStockProductDTO product : products) {
            log.info("  - {} (SKU: {}) - Stock: {}, Available: {}",
                product.productName(),
                product.sku(),
                product.stockQuantity(),
                product.availableQuantity());
        }

        // In production: Send daily digest email
        sendNotification("LOW_STOCK", products);
    }

    /**
     * Send notification (placeholder for email/SMS/Slack integration).
     */
    private void sendNotification(String alertType, List<LowStockProductDTO> products) {
        // This is a placeholder for actual notification implementation
        // In production, integrate with:
        // - Email service (SendGrid, SES)
        // - SMS service (Twilio)
        // - Slack webhook
        // - PagerDuty for critical alerts

        log.debug("Notification sent: {} - {} products", alertType, products.size());
    }

    /**
     * Get count of products with low stock.
     */
    public long getLowStockCount() {
        return analyticsRepository.getLowStockCount(lowStockThreshold);
    }

    /**
     * Get all low stock products.
     */
    public List<LowStockProductDTO> getLowStockProducts() {
        return analyticsRepository.getLowStockProducts(lowStockThreshold);
    }
}
