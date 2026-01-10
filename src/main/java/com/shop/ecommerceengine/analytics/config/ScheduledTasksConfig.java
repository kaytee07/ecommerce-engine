package com.shop.ecommerceengine.analytics.config;

import com.shop.ecommerceengine.analytics.service.AnalyticsService;
import com.shop.ecommerceengine.analytics.service.LowStockAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for scheduled analytics tasks.
 *
 * Tasks include:
 * - Hourly refresh of daily sales materialized view
 * - 6-hour refresh of top products materialized view
 * - 4-hour low stock alert checks
 *
 * Scheduling can be disabled with app.scheduling.enabled=false for testing.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTasksConfig {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasksConfig.class);

    private final AnalyticsService analyticsService;
    private final LowStockAlertService lowStockAlertService;

    public ScheduledTasksConfig(AnalyticsService analyticsService, LowStockAlertService lowStockAlertService) {
        this.analyticsService = analyticsService;
        this.lowStockAlertService = lowStockAlertService;
    }

    /**
     * Refresh the daily sales materialized view every hour.
     * Runs at minute 0 of every hour.
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void refreshDailySalesView() {
        log.info("Scheduled task: Refreshing mv_daily_sales_stats");
        try {
            analyticsService.refreshDailySalesView();
            log.info("Scheduled task: mv_daily_sales_stats refreshed successfully");
        } catch (Exception e) {
            log.error("Scheduled task: Failed to refresh mv_daily_sales_stats: {}", e.getMessage());
        }
    }

    /**
     * Refresh the top products materialized view every 6 hours.
     * Runs at 00:00, 06:00, 12:00, 18:00.
     */
    @Scheduled(fixedRate = 21600000) // 6 hours in milliseconds
    public void refreshTopProductsView() {
        log.info("Scheduled task: Refreshing mv_top_products");
        try {
            analyticsService.refreshTopProductsView();
            log.info("Scheduled task: mv_top_products refreshed successfully");
        } catch (Exception e) {
            log.error("Scheduled task: Failed to refresh mv_top_products: {}", e.getMessage());
        }
    }

    /**
     * Check for low stock products every 4 hours.
     * Sends alerts for products below threshold.
     */
    @Scheduled(fixedRate = 14400000) // 4 hours in milliseconds
    public void checkLowStock() {
        log.info("Scheduled task: Checking for low stock products");
        try {
            lowStockAlertService.checkAndAlertLowStock();
            log.info("Scheduled task: Low stock check completed");
        } catch (Exception e) {
            log.error("Scheduled task: Failed to check low stock: {}", e.getMessage());
        }
    }

    /**
     * Evict the dashboard cache every 10 minutes.
     * This ensures the dashboard doesn't serve stale data.
     */
    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    public void evictDashboardCache() {
        log.debug("Scheduled task: Evicting dashboard cache");
        try {
            analyticsService.evictDashboardCache();
        } catch (Exception e) {
            log.error("Scheduled task: Failed to evict dashboard cache: {}", e.getMessage());
        }
    }
}
