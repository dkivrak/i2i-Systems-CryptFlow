-- Premium Features Database Schema Migration
-- 1. Add average_price tracking to portfolio assets
ALTER TABLE portfolio_assets ADD COLUMN IF NOT EXISTS average_price NUMERIC(28,8) NOT NULL DEFAULT 0.0;

-- 2. Create equity_history table for the Equity Curve chart
CREATE TABLE IF NOT EXISTS equity_history (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    total_value NUMERIC(19,2) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_equity_user_time ON equity_history(user_id, recorded_at ASC);

-- 3. Create limit_orders table for Limit and Stop Loss orders
CREATE TABLE IF NOT EXISTS limit_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol VARCHAR(10) NOT NULL,
    side VARCHAR(4) NOT NULL,
    type VARCHAR(10) NOT NULL, -- LIMIT, STOP_LOSS
    target_price NUMERIC(28,8) NOT NULL,
    quantity NUMERIC(28,8) NOT NULL,
    status VARCHAR(10) NOT NULL, -- PENDING, EXECUTED, CANCELLED
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_orders_pending ON limit_orders(status) WHERE status = 'PENDING';

-- 4. Create price_alerts table for the Price Alert notification system
CREATE TABLE IF NOT EXISTS price_alerts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol VARCHAR(10) NOT NULL,
    target_price NUMERIC(28,8) NOT NULL,
    condition VARCHAR(5) NOT NULL, -- ABOVE, BELOW
    is_triggered BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_alerts_active ON price_alerts(is_triggered) WHERE is_triggered = FALSE;
