-- V5__cart.sql
-- Shopping cart table for persistent user carts

CREATE TABLE IF NOT EXISTS carts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    items JSONB NOT NULL DEFAULT '[]',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for user lookups
CREATE INDEX IF NOT EXISTS idx_carts_user_id ON carts(user_id);

-- Index for finding carts with items (for cleanup jobs)
CREATE INDEX IF NOT EXISTS idx_carts_updated_at ON carts(updated_at);

-- GIN index for JSONB items array queries
CREATE INDEX IF NOT EXISTS idx_carts_items ON carts USING GIN (items);

COMMENT ON TABLE carts IS 'Shopping carts for logged-in users with optimistic locking';
COMMENT ON COLUMN carts.items IS 'JSONB array of cart items: [{productId, quantity, priceAtAdd, productName}]';
COMMENT ON COLUMN carts.version IS 'Optimistic locking version for concurrent cart updates';
