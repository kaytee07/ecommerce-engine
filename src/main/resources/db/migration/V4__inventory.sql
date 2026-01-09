-- V4__inventory.sql
-- Inventory management table for stock tracking

CREATE TABLE IF NOT EXISTS inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL UNIQUE,
    stock_quantity INT NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);

-- Index for low stock alerts (quantity < 5)
CREATE INDEX IF NOT EXISTS idx_inventory_low_stock ON inventory(stock_quantity)
    WHERE stock_quantity < 5;

-- Index for product lookups
CREATE INDEX IF NOT EXISTS idx_inventory_product_id ON inventory(product_id);

-- Inventory adjustment history for audit trail
CREATE TABLE IF NOT EXISTS inventory_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL,
    product_id UUID NOT NULL,
    adjustment_type VARCHAR(50) NOT NULL,
    quantity_change INT NOT NULL,
    quantity_before INT NOT NULL,
    quantity_after INT NOT NULL,
    reason VARCHAR(255),
    admin_id UUID,
    admin_username VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_adjustment_inventory FOREIGN KEY (inventory_id) REFERENCES inventory(id) ON DELETE CASCADE
);

-- Index for adjustment history lookups
CREATE INDEX IF NOT EXISTS idx_inventory_adjustments_product ON inventory_adjustments(product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_adjustments_created ON inventory_adjustments(created_at);

COMMENT ON TABLE inventory IS 'Tracks stock levels for products with optimistic locking';
COMMENT ON TABLE inventory_adjustments IS 'Audit trail for all stock changes';
COMMENT ON COLUMN inventory.version IS 'Optimistic locking version for concurrent stock updates';
COMMENT ON COLUMN inventory.reserved_quantity IS 'Stock reserved for pending orders but not yet shipped';
