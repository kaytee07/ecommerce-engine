-- V7: Payments table for payment processing
-- Supports Hubtel (primary) and Paystack (fallback) gateways
-- Includes soft delete and idempotency key support

-- Create payments table
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    gateway VARCHAR(50) NOT NULL,
    transaction_ref VARCHAR(255),
    idempotency_key VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'GHS',
    checkout_url TEXT,
    gateway_response TEXT,
    failure_reason TEXT,
    refund_reason TEXT,
    refunded_by UUID,
    refunded_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED', 'CANCELLED')),
    CONSTRAINT chk_payment_gateway CHECK (gateway IN ('HUBTEL', 'PAYSTACK')),
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key)
);

-- Index for order lookups
CREATE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);

-- Index for user lookups
CREATE INDEX IF NOT EXISTS idx_payments_user_id ON payments(user_id);

-- Index for transaction reference lookups
CREATE INDEX IF NOT EXISTS idx_payments_transaction_ref ON payments(transaction_ref);

-- Index for status queries
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

-- Index for gateway queries
CREATE INDEX IF NOT EXISTS idx_payments_gateway ON payments(gateway);

-- Partial index for active (non-deleted) payments
CREATE INDEX IF NOT EXISTS idx_payments_active ON payments(id) WHERE deleted_at IS NULL;

-- Index for idempotency key lookups
CREATE INDEX IF NOT EXISTS idx_payments_idempotency_key ON payments(idempotency_key);

-- Payment audit log for tracking payment status changes
CREATE TABLE IF NOT EXISTS payment_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    changed_by UUID,
    change_reason TEXT,
    gateway_response JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

-- Index for payment audit lookups
CREATE INDEX IF NOT EXISTS idx_payment_audit_payment_id ON payment_audit_log(payment_id);

-- Index for chronological audit queries
CREATE INDEX IF NOT EXISTS idx_payment_audit_created_at ON payment_audit_log(created_at);

-- Add comments for documentation
COMMENT ON TABLE payments IS 'Payment records for orders, supporting Hubtel and Paystack gateways';
COMMENT ON COLUMN payments.idempotency_key IS 'Unique key to prevent duplicate payment processing';
COMMENT ON COLUMN payments.gateway_response IS 'Raw JSON response from payment gateway';
COMMENT ON COLUMN payments.deleted_at IS 'Soft delete timestamp - NULL means active';
COMMENT ON TABLE payment_audit_log IS 'Audit trail for payment status changes';
