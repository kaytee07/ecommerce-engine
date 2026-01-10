-- V10: Materialized Views for Analytics
-- OLAP-optimized views to avoid impacting OLTP performance
-- Supports dashboard metrics, sales analytics, and reporting

-- =====================================================
-- Daily Sales Statistics Materialized View
-- Aggregates daily revenue, order count, average order value
-- Refresh: Hourly via @Scheduled
-- =====================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_daily_sales_stats AS
SELECT
    DATE(o.created_at) AS sale_date,
    COUNT(DISTINCT o.id) AS order_count,
    COALESCE(SUM(o.total_amount), 0) AS total_revenue,
    COALESCE(AVG(o.total_amount), 0) AS avg_order_value,
    COUNT(DISTINCT o.user_id) AS unique_customers
FROM orders o
WHERE o.deleted_at IS NULL
  AND o.status NOT IN ('CANCELLED', 'REFUNDED')
GROUP BY DATE(o.created_at)
ORDER BY sale_date DESC;

-- Index for date range queries
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_daily_sales_date
    ON mv_daily_sales_stats(sale_date);

-- =====================================================
-- Top Products Materialized View
-- Ranks products by total units sold and revenue
-- Refresh: Every 6 hours via @Scheduled
-- =====================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_top_products AS
WITH order_items_expanded AS (
    SELECT
        o.id AS order_id,
        o.status,
        o.deleted_at,
        (jsonb_array_elements(o.items::jsonb)->>'productId')::UUID AS product_id,
        (jsonb_array_elements(o.items::jsonb)->>'productName')::TEXT AS product_name,
        (jsonb_array_elements(o.items::jsonb)->>'quantity')::INTEGER AS quantity,
        (jsonb_array_elements(o.items::jsonb)->>'price')::DECIMAL AS price
    FROM orders o
    WHERE o.deleted_at IS NULL
      AND o.status NOT IN ('CANCELLED', 'PENDING')
)
SELECT
    oie.product_id,
    MAX(oie.product_name) AS product_name,
    SUM(oie.quantity) AS total_sold,
    SUM(oie.quantity * oie.price) AS total_revenue,
    COUNT(DISTINCT oie.order_id) AS order_count
FROM order_items_expanded oie
GROUP BY oie.product_id
ORDER BY total_sold DESC;

-- Index for product lookups
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_top_products_id
    ON mv_top_products(product_id);

-- Index for ranking queries
CREATE INDEX IF NOT EXISTS idx_mv_top_products_sold
    ON mv_top_products(total_sold DESC);

-- =====================================================
-- Customer Lifetime Value View (Regular View)
-- Per-customer aggregations for CLV calculations
-- Used for real-time queries, not materialized
-- =====================================================
CREATE OR REPLACE VIEW v_customer_lifetime_value AS
SELECT
    o.user_id,
    COUNT(DISTINCT o.id) AS order_count,
    COALESCE(SUM(o.total_amount), 0) AS total_spent,
    COALESCE(AVG(o.total_amount), 0) AS avg_order_value,
    MIN(o.created_at) AS first_order_date,
    MAX(o.created_at) AS last_order_date,
    EXTRACT(DAY FROM (MAX(o.created_at) - MIN(o.created_at))) AS customer_tenure_days
FROM orders o
WHERE o.deleted_at IS NULL
  AND o.status NOT IN ('CANCELLED', 'REFUNDED', 'PENDING')
GROUP BY o.user_id;

-- =====================================================
-- Sales Funnel View (Regular View)
-- Tracks cart-to-order-to-payment conversion
-- =====================================================
CREATE OR REPLACE VIEW v_sales_funnel AS
SELECT
    (SELECT COUNT(*) FROM carts WHERE deleted_at IS NULL) AS active_carts,
    (SELECT COUNT(*) FROM orders WHERE deleted_at IS NULL) AS total_orders,
    (SELECT COUNT(*) FROM orders WHERE deleted_at IS NULL AND status = 'PENDING') AS pending_orders,
    (SELECT COUNT(*) FROM orders WHERE deleted_at IS NULL AND status IN ('CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED')) AS successful_orders,
    (SELECT COUNT(*) FROM payments WHERE deleted_at IS NULL AND status = 'SUCCESS') AS successful_payments,
    (SELECT COUNT(*) FROM payments WHERE deleted_at IS NULL AND status = 'FAILED') AS failed_payments;

-- =====================================================
-- Low Stock Alert View (Regular View)
-- Products with inventory below threshold
-- =====================================================
CREATE OR REPLACE VIEW v_low_stock_products AS
SELECT
    i.id AS inventory_id,
    i.product_id,
    p.name AS product_name,
    p.sku,
    i.stock_quantity,
    i.reserved_quantity,
    (i.stock_quantity - i.reserved_quantity) AS available_quantity
FROM inventory i
JOIN products p ON i.product_id = p.id
WHERE p.deleted_at IS NULL
  AND p.active = true
ORDER BY i.stock_quantity ASC;

-- =====================================================
-- Payment Statistics View (Regular View)
-- Payment gateway performance metrics
-- =====================================================
CREATE OR REPLACE VIEW v_payment_stats AS
SELECT
    gateway,
    status,
    COUNT(*) AS payment_count,
    COALESCE(SUM(amount), 0) AS total_amount,
    COALESCE(AVG(amount), 0) AS avg_amount
FROM payments
WHERE deleted_at IS NULL
GROUP BY gateway, status;

-- =====================================================
-- Monthly Revenue Summary (For Reports)
-- =====================================================
CREATE OR REPLACE VIEW v_monthly_revenue AS
SELECT
    DATE_TRUNC('month', o.created_at) AS month,
    COUNT(DISTINCT o.id) AS order_count,
    COALESCE(SUM(o.total_amount), 0) AS total_revenue,
    COUNT(DISTINCT o.user_id) AS unique_customers
FROM orders o
WHERE o.deleted_at IS NULL
  AND o.status NOT IN ('CANCELLED', 'REFUNDED', 'PENDING')
GROUP BY DATE_TRUNC('month', o.created_at)
ORDER BY month DESC;

-- =====================================================
-- Add comments for documentation
-- =====================================================
COMMENT ON MATERIALIZED VIEW mv_daily_sales_stats IS 'Daily aggregated sales statistics - refresh hourly';
COMMENT ON MATERIALIZED VIEW mv_top_products IS 'Top selling products by quantity - refresh every 6 hours';
COMMENT ON VIEW v_customer_lifetime_value IS 'Per-customer lifetime value calculations';
COMMENT ON VIEW v_sales_funnel IS 'Cart to order to payment conversion funnel';
COMMENT ON VIEW v_low_stock_products IS 'Products with low inventory levels';
COMMENT ON VIEW v_payment_stats IS 'Payment gateway performance by status';
COMMENT ON VIEW v_monthly_revenue IS 'Monthly revenue summary for reporting';
