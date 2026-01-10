package com.shop.ecommerceengine.analytics.controller;

import com.shop.ecommerceengine.analytics.dto.*;
import com.shop.ecommerceengine.analytics.service.AnalyticsService;
import com.shop.ecommerceengine.analytics.service.ExportService;
import com.shop.ecommerceengine.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controller for admin analytics and reporting operations.
 * Provides dashboard metrics, sales data, and data exports.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@Tag(name = "Admin Analytics", description = "Analytics dashboard and reporting operations")
public class AdminAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AdminAnalyticsController.class);

    private final AnalyticsService analyticsService;
    private final ExportService exportService;

    public AdminAnalyticsController(AnalyticsService analyticsService, ExportService exportService) {
        this.analyticsService = analyticsService;
        this.exportService = exportService;
    }

    // ==================== Dashboard ====================

    /**
     * Get the analytics dashboard with all key metrics.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get analytics dashboard", description = "Returns dashboard with all key business metrics")
    public ResponseEntity<ApiResponse<AnalyticsDashboardDTO>> getDashboard() {
        log.info("Admin requesting analytics dashboard");
        AnalyticsDashboardDTO dashboard = analyticsService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }

    /**
     * Evict the dashboard cache (force refresh).
     */
    @PostMapping("/dashboard/refresh")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Refresh dashboard cache", description = "Forces refresh of cached dashboard data")
    public ResponseEntity<ApiResponse<AnalyticsDashboardDTO>> refreshDashboard() {
        log.info("Admin refreshing dashboard cache");
        analyticsService.evictDashboardCache();
        AnalyticsDashboardDTO dashboard = analyticsService.getDashboard();
        return ResponseEntity.ok(ApiResponse.success(dashboard, "Dashboard cache refreshed"));
    }

    // ==================== Daily Sales ====================

    /**
     * Get daily sales statistics for a date range.
     */
    @GetMapping("/sales/daily")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_CONTENT_MANAGER')")
    @Operation(summary = "Get daily sales", description = "Returns daily sales statistics for a date range")
    public ResponseEntity<ApiResponse<List<DailySalesDTO>>> getDailySales(
            @Parameter(description = "Start date (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        log.info("Admin requesting daily sales from {} to {}", fromDate, toDate);
        List<DailySalesDTO> dailySales = analyticsService.getDailySales(fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success(dailySales));
    }

    /**
     * Get today's sales summary.
     */
    @GetMapping("/sales/today")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_CONTENT_MANAGER')")
    @Operation(summary = "Get today's sales", description = "Returns today's sales summary")
    public ResponseEntity<ApiResponse<DailySalesDTO>> getTodaySales() {
        log.info("Admin requesting today's sales");
        DailySalesDTO todaySales = analyticsService.getTodaySales();
        return ResponseEntity.ok(ApiResponse.success(todaySales));
    }

    // ==================== Top Products ====================

    /**
     * Get top selling products.
     */
    @GetMapping("/products/top")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_CONTENT_MANAGER')")
    @Operation(summary = "Get top products", description = "Returns top selling products")
    public ResponseEntity<ApiResponse<List<TopProductDTO>>> getTopProducts(
            @Parameter(description = "Number of products to return (default 10)")
            @RequestParam(defaultValue = "10") int limit) {

        log.info("Admin requesting top {} products", limit);
        List<TopProductDTO> topProducts = analyticsService.getTopProducts(limit);
        return ResponseEntity.ok(ApiResponse.success(topProducts));
    }

    // ==================== Customer Lifetime Value ====================

    /**
     * Get customer lifetime value for a specific user.
     */
    @GetMapping("/customers/{userId}/clv")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_SUPPORT_AGENT')")
    @Operation(summary = "Get customer CLV", description = "Returns customer lifetime value for a user")
    public ResponseEntity<ApiResponse<CustomerLifetimeValueDTO>> getCustomerLifetimeValue(
            @Parameter(description = "User UUID")
            @PathVariable UUID userId) {

        log.info("Admin requesting CLV for user {}", userId);
        CustomerLifetimeValueDTO clv = analyticsService.getCustomerLifetimeValue(userId);
        return ResponseEntity.ok(ApiResponse.success(clv));
    }

    // ==================== Sales Funnel ====================

    /**
     * Get sales funnel metrics.
     */
    @GetMapping("/funnel")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Get sales funnel", description = "Returns sales funnel conversion metrics")
    public ResponseEntity<ApiResponse<SalesFunnelDTO>> getSalesFunnel() {
        log.info("Admin requesting sales funnel");
        SalesFunnelDTO funnel = analyticsService.getSalesFunnel();
        return ResponseEntity.ok(ApiResponse.success(funnel));
    }

    // ==================== Low Stock Alerts ====================

    /**
     * Get products with low stock.
     */
    @GetMapping("/inventory/low-stock")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_CONTENT_MANAGER', 'ROLE_WAREHOUSE')")
    @Operation(summary = "Get low stock products", description = "Returns products with stock below threshold")
    public ResponseEntity<ApiResponse<List<LowStockProductDTO>>> getLowStockProducts(
            @Parameter(description = "Stock threshold (default 5)")
            @RequestParam(defaultValue = "5") int threshold) {

        log.info("Admin requesting low stock products with threshold {}", threshold);
        List<LowStockProductDTO> lowStock = analyticsService.getLowStockProducts(threshold);
        return ResponseEntity.ok(ApiResponse.success(lowStock));
    }

    // ==================== Data Exports ====================

    /**
     * Export orders to CSV.
     */
    @GetMapping("/export/orders")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Export orders to CSV", description = "Streams orders data to CSV file")
    public void exportOrders(
            @Parameter(description = "Start date (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            HttpServletResponse response) throws IOException {

        log.info("Admin exporting orders from {} to {}", fromDate, toDate);
        exportService.exportOrdersToCsv(fromDate, toDate, response);
    }

    /**
     * Export payments to CSV.
     */
    @GetMapping("/export/payments")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Export payments to CSV", description = "Streams payments data to CSV file")
    public void exportPayments(
            @Parameter(description = "Start date (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            HttpServletResponse response) throws IOException {

        log.info("Admin exporting payments from {} to {}", fromDate, toDate);
        exportService.exportPaymentsToCsv(fromDate, toDate, response);
    }

    /**
     * Export inventory to CSV.
     */
    @GetMapping("/export/inventory")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_WAREHOUSE')")
    @Operation(summary = "Export inventory to CSV", description = "Streams inventory data to CSV file")
    public void exportInventory(HttpServletResponse response) throws IOException {
        log.info("Admin exporting inventory");
        exportService.exportInventoryToCsv(response);
    }

    /**
     * Export daily sales summary to CSV.
     */
    @GetMapping("/export/daily-sales")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Export daily sales to CSV", description = "Streams daily sales summary to CSV file")
    public void exportDailySales(
            @Parameter(description = "Start date (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date (yyyy-MM-dd)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            HttpServletResponse response) throws IOException {

        log.info("Admin exporting daily sales from {} to {}", fromDate, toDate);
        exportService.exportDailySalesToCsv(fromDate, toDate, response);
    }

    // ==================== View Management ====================

    /**
     * Manually refresh materialized views.
     */
    @PostMapping("/views/refresh")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    @Operation(summary = "Refresh materialized views", description = "Manually triggers refresh of analytics views")
    public ResponseEntity<ApiResponse<String>> refreshViews() {
        log.info("Admin manually refreshing materialized views");

        analyticsService.refreshDailySalesView();
        analyticsService.refreshTopProductsView();

        return ResponseEntity.ok(ApiResponse.success("Views refreshed", "Materialized views refreshed successfully"));
    }
}
