package com.shop.ecommerceengine.analytics.repository;

import com.shop.ecommerceengine.analytics.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for analytics queries using materialized and regular views.
 * Uses JdbcTemplate for efficient view queries.
 */
@Repository
public class AnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== Daily Sales Queries ====================

    /**
     * Get daily sales statistics from materialized view.
     */
    public List<DailySalesDTO> getDailySales(LocalDate fromDate, LocalDate toDate) {
        String sql = """
            SELECT sale_date, order_count, total_revenue, avg_order_value, unique_customers
            FROM mv_daily_sales_stats
            WHERE sale_date BETWEEN ? AND ?
            ORDER BY sale_date DESC
            """;

        return jdbcTemplate.query(sql, new DailySalesRowMapper(), fromDate, toDate);
    }

    /**
     * Get today's sales summary.
     */
    public Optional<DailySalesDTO> getTodaySales() {
        String sql = """
            SELECT sale_date, order_count, total_revenue, avg_order_value, unique_customers
            FROM mv_daily_sales_stats
            WHERE sale_date = CURRENT_DATE
            """;

        List<DailySalesDTO> results = jdbcTemplate.query(sql, new DailySalesRowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Get aggregated revenue for a date range.
     */
    public BigDecimal getRevenueForPeriod(LocalDate fromDate, LocalDate toDate) {
        String sql = """
            SELECT COALESCE(SUM(total_revenue), 0)
            FROM mv_daily_sales_stats
            WHERE sale_date BETWEEN ? AND ?
            """;

        return jdbcTemplate.queryForObject(sql, BigDecimal.class, fromDate, toDate);
    }

    /**
     * Get order count for a date range.
     */
    public long getOrderCountForPeriod(LocalDate fromDate, LocalDate toDate) {
        String sql = """
            SELECT COALESCE(SUM(order_count), 0)
            FROM mv_daily_sales_stats
            WHERE sale_date BETWEEN ? AND ?
            """;

        Long result = jdbcTemplate.queryForObject(sql, Long.class, fromDate, toDate);
        return result != null ? result : 0;
    }

    // ==================== Top Products Queries ====================

    /**
     * Get top selling products from materialized view.
     */
    public List<TopProductDTO> getTopProducts(int limit) {
        String sql = """
            SELECT product_id, product_name, total_sold, total_revenue, order_count
            FROM mv_top_products
            ORDER BY total_sold DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, new TopProductRowMapper(), limit);
    }

    // ==================== Customer Lifetime Value Queries ====================

    /**
     * Get customer lifetime value for a specific user.
     */
    public Optional<CustomerLifetimeValueDTO> getCustomerLifetimeValue(UUID userId) {
        String sql = """
            SELECT user_id, order_count, total_spent, avg_order_value,
                   first_order_date, last_order_date, customer_tenure_days
            FROM v_customer_lifetime_value
            WHERE user_id = ?
            """;

        List<CustomerLifetimeValueDTO> results = jdbcTemplate.query(
            sql,
            new CustomerLifetimeValueRowMapper(),
            userId
        );

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // ==================== Sales Funnel Queries ====================

    /**
     * Get sales funnel metrics.
     */
    public SalesFunnelDTO getSalesFunnel() {
        String sql = """
            SELECT active_carts, total_orders, pending_orders,
                   successful_orders, successful_payments, failed_payments
            FROM v_sales_funnel
            """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            long carts = rs.getLong("active_carts");
            long orders = rs.getLong("total_orders");
            long paid = rs.getLong("successful_payments");
            long failed = rs.getLong("failed_payments");

            return SalesFunnelDTO.fromCounts(carts, orders, paid, failed);
        });
    }

    // ==================== Low Stock Queries ====================

    /**
     * Get products with low stock below threshold.
     */
    public List<LowStockProductDTO> getLowStockProducts(int threshold) {
        String sql = """
            SELECT inventory_id, product_id, product_name, sku,
                   stock_quantity, reserved_quantity, available_quantity
            FROM v_low_stock_products
            WHERE stock_quantity < ?
            ORDER BY stock_quantity ASC
            """;

        return jdbcTemplate.query(sql, new LowStockProductRowMapper(), threshold);
    }

    /**
     * Get count of products with low stock.
     */
    public long getLowStockCount(int threshold) {
        String sql = """
            SELECT COUNT(*)
            FROM v_low_stock_products
            WHERE stock_quantity < ?
            """;

        Long result = jdbcTemplate.queryForObject(sql, Long.class, threshold);
        return result != null ? result : 0;
    }

    // ==================== Payment Statistics Queries ====================

    /**
     * Get payment statistics by status.
     */
    public long getPaymentCountByStatus(String status) {
        String sql = """
            SELECT COALESCE(SUM(payment_count), 0)
            FROM v_payment_stats
            WHERE status = ?
            """;

        Long result = jdbcTemplate.queryForObject(sql, Long.class, status);
        return result != null ? result : 0;
    }

    /**
     * Get total payment volume.
     */
    public BigDecimal getTotalPaymentVolume() {
        String sql = """
            SELECT COALESCE(SUM(total_amount), 0)
            FROM v_payment_stats
            WHERE status = 'SUCCESS'
            """;

        return jdbcTemplate.queryForObject(sql, BigDecimal.class);
    }

    // ==================== Materialized View Refresh ====================

    /**
     * Refresh the daily sales materialized view.
     */
    public void refreshDailySalesView() {
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_sales_stats");
    }

    /**
     * Refresh the top products materialized view.
     */
    public void refreshTopProductsView() {
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_top_products");
    }

    // ==================== Row Mappers ====================

    private static class DailySalesRowMapper implements RowMapper<DailySalesDTO> {
        @Override
        public DailySalesDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DailySalesDTO(
                rs.getDate("sale_date").toLocalDate(),
                rs.getLong("order_count"),
                rs.getBigDecimal("total_revenue"),
                rs.getBigDecimal("avg_order_value"),
                rs.getLong("unique_customers")
            );
        }
    }

    private static class TopProductRowMapper implements RowMapper<TopProductDTO> {
        @Override
        public TopProductDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TopProductDTO(
                UUID.fromString(rs.getString("product_id")),
                rs.getString("product_name"),
                rs.getLong("total_sold"),
                rs.getBigDecimal("total_revenue"),
                rs.getLong("order_count")
            );
        }
    }

    private static class CustomerLifetimeValueRowMapper implements RowMapper<CustomerLifetimeValueDTO> {
        @Override
        public CustomerLifetimeValueDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp firstOrder = rs.getTimestamp("first_order_date");
            Timestamp lastOrder = rs.getTimestamp("last_order_date");

            return new CustomerLifetimeValueDTO(
                UUID.fromString(rs.getString("user_id")),
                rs.getLong("order_count"),
                rs.getBigDecimal("total_spent"),
                rs.getBigDecimal("avg_order_value"),
                firstOrder != null ? firstOrder.toInstant() : null,
                lastOrder != null ? lastOrder.toInstant() : null,
                rs.getLong("customer_tenure_days")
            );
        }
    }

    private static class LowStockProductRowMapper implements RowMapper<LowStockProductDTO> {
        @Override
        public LowStockProductDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new LowStockProductDTO(
                UUID.fromString(rs.getString("inventory_id")),
                UUID.fromString(rs.getString("product_id")),
                rs.getString("product_name"),
                rs.getString("sku"),
                rs.getInt("stock_quantity"),
                rs.getInt("reserved_quantity"),
                rs.getInt("available_quantity")
            );
        }
    }
}
