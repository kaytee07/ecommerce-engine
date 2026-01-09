-- Phase 5: Catalog Admin - Product Discounts and Soft Delete
-- Add discount fields to products table
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_percentage DECIMAL(5, 2) CHECK (discount_percentage >= 0 AND discount_percentage <= 100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_start TIMESTAMP;
ALTER TABLE products ADD COLUMN IF NOT EXISTS discount_end TIMESTAMP;
ALTER TABLE products ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Index for discount queries (active discounts)
CREATE INDEX IF NOT EXISTS idx_products_discount_active ON products(discount_percentage, discount_start, discount_end)
    WHERE discount_percentage IS NOT NULL AND discount_percentage > 0;

-- Index for soft delete queries
CREATE INDEX IF NOT EXISTS idx_products_deleted_at ON products(deleted_at) WHERE deleted_at IS NULL;

-- Constraint to ensure discount end is after discount start
ALTER TABLE products ADD CONSTRAINT chk_discount_dates
    CHECK (discount_start IS NULL OR discount_end IS NULL OR discount_end > discount_start);
