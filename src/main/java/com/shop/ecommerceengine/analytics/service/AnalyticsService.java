package com.shop.ecommerceengine.analytics.service;

import com.shop.ecommerceengine.analytics.dto.*;
import com.shop.ecommerceengine.analytics.repository.AnalyticsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for analytics operations.
 * Provides dashboard metrics, sales data, and reporting.
 * Uses Redis caching with 10 minute TTL for dashboard.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final int TOP_PRODUCTS_LIMIT = 5;

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    // ==================== Dashboard ====================

    /**
     * Get the analytics dashboard with all key metrics.
     * Cached in Redis for 10 minutes.
     */
    @Cacheable(value = "analytics:dashboard", key = "'dashboard'")
    public AnalyticsDashboardDTO getDashboard() {
        log.info("Building analytics dashboard (cache miss)");

        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);
        LocalDate monthAgo = today.minusDays(30);

        // Revenue metrics
        BigDecimal todayRevenue = analyticsRepository.getRevenueForPeriod(today, today);
        BigDecimal weekRevenue = analyticsRepository.getRevenueForPeriod(weekAgo, today);
        BigDecimal monthRevenue = analyticsRepository.getRevenueForPeriod(monthAgo, today);

        // Order counts
        long todayOrders = analyticsRepository.getOrderCountForPeriod(today, today);
        long weekOrders = analyticsRepository.getOrderCountForPeriod(weekAgo, today);
        long monthOrders = analyticsRepository.getOrderCountForPeriod(monthAgo, today);

        // Sales funnel
        SalesFunnelDTO salesFunnel = getSalesFunnel();

        // Top products
        List<TopProductDTO> topProducts = getTopProducts(TOP_PRODUCTS_LIMIT);

        // Low stock alerts
        List<LowStockProductDTO> lowStock = getLowStockProducts(LOW_STOCK_THRESHOLD);

        // Payment metrics
        long successfulPayments = analyticsRepository.getPaymentCountByStatus("SUCCESS");
        long failedPayments = analyticsRepository.getPaymentCountByStatus("FAILED");
        BigDecimal paymentVolume = analyticsRepository.getTotalPaymentVolume();

        return AnalyticsDashboardDTO.builder()
            .todayRevenue(todayRevenue != null ? todayRevenue : BigDecimal.ZERO)
            .weekRevenue(weekRevenue != null ? weekRevenue : BigDecimal.ZERO)
            .monthRevenue(monthRevenue != null ? monthRevenue : BigDecimal.ZERO)
            .todayOrderCount(todayOrders)
            .weekOrderCount(weekOrders)
            .monthOrderCount(monthOrders)
            .salesFunnel(salesFunnel)
            .topProducts(topProducts)
            .lowStockAlerts(lowStock)
            .successfulPayments(successfulPayments)
            .failedPayments(failedPayments)
            .totalPaymentVolume(paymentVolume != null ? paymentVolume : BigDecimal.ZERO)
            .generatedAt(Instant.now())
            .build();
    }

    /**
     * Evict the dashboard cache.
     */
    @CacheEvict(value = "analytics:dashboard", key = "'dashboard'")
    public void evictDashboardCache() {
        log.info("Dashboard cache evicted");
    }

    // ==================== Daily Sales ====================

    /**
     * Get daily sales statistics for a date range.
     */
    public List<DailySalesDTO> getDailySales(LocalDate fromDate, LocalDate toDate) {
        log.debug("Getting daily sales from {} to {}", fromDate, toDate);
        return analyticsRepository.getDailySales(fromDate, toDate);
    }

    /**
     * Get today's sales summary.
     */
    public DailySalesDTO getTodaySales() {
        return analyticsRepository.getTodaySales()
            .orElse(new DailySalesDTO(LocalDate.now(), 0, BigDecimal.ZERO, BigDecimal.ZERO, 0));
    }

    // ==================== Top Products ====================

    /**
     * Get top selling products.
     */
    public List<TopProductDTO> getTopProducts(int limit) {
        log.debug("Getting top {} products", limit);
        return analyticsRepository.getTopProducts(limit);
    }

    // ==================== Customer Lifetime Value ====================

    /**
     * Get customer lifetime value for a specific user.
     */
    public CustomerLifetimeValueDTO getCustomerLifetimeValue(UUID userId) {
        log.debug("Calculating CLV for user: {}", userId);
        return analyticsRepository.getCustomerLifetimeValue(userId)
            .orElse(CustomerLifetimeValueDTO.empty(userId));
    }

    // ==================== Sales Funnel ====================

    /**
     * Get sales funnel metrics (cart → order → payment conversion).
     */
    public SalesFunnelDTO getSalesFunnel() {
        log.debug("Getting sales funnel metrics");
        try {
            return analyticsRepository.getSalesFunnel();
        } catch (Exception e) {
            log.warn("Error getting sales funnel, returning empty: {}", e.getMessage());
            return SalesFunnelDTO.fromCounts(0, 0, 0, 0);
        }
    }

    // ==================== Low Stock ====================

    /**
     * Get products with low stock below threshold.
     */
    public List<LowStockProductDTO> getLowStockProducts(int threshold) {
        log.debug("Getting products with stock below {}", threshold);
        return analyticsRepository.getLowStockProducts(threshold);
    }

    /**
     * Get count of products with low stock.
     */
    public long getLowStockCount() {
        return analyticsRepository.getLowStockCount(LOW_STOCK_THRESHOLD);
    }

    // ==================== View Refresh ====================

    /**
     * Refresh the daily sales materialized view.
     * Called by scheduled task.
     */
    @Transactional
    public void refreshDailySalesView() {
        log.info("Refreshing mv_daily_sales_stats materialized view");
        try {
            analyticsRepository.refreshDailySalesView();
            log.info("mv_daily_sales_stats refreshed successfully");
        } catch (Exception e) {
            log.error("Error refreshing mv_daily_sales_stats: {}", e.getMessage());
        }
    }

    /**
     * Refresh the top products materialized view.
     * Called by scheduled task.
     */
    @Transactional
    public void refreshTopProductsView() {
        log.info("Refreshing mv_top_products materialized view");
        try {
            analyticsRepository.refreshTopProductsView();
            log.info("mv_top_products refreshed successfully");
        } catch (Exception e) {
            log.error("Error refreshing mv_top_products: {}", e.getMessage());
        }
    }
}
