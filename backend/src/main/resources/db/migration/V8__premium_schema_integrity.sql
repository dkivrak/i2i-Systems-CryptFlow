ALTER TABLE limit_orders ALTER COLUMN symbol TYPE VARCHAR(50);
ALTER TABLE price_alerts ALTER COLUMN symbol TYPE VARCHAR(50);

-- Normalize values written before the API enforced these domains. Invalid legacy
-- records remain available for audit/history, but no longer stay active.
UPDATE limit_orders
SET side = UPPER(TRIM(side)),
    type = UPPER(TRIM(type)),
    status = UPPER(TRIM(status));

UPDATE limit_orders
SET status = 'CANCELLED'
WHERE status = 'PENDING'
  AND (
    side NOT IN ('BUY', 'SELL')
    OR type NOT IN ('LIMIT', 'STOP_LOSS')
    OR (type = 'STOP_LOSS' AND side <> 'SELL')
    OR target_price <= 0
    OR quantity <= 0
  );

UPDATE price_alerts
SET condition = UPPER(TRIM(condition));

UPDATE price_alerts
SET is_triggered = TRUE,
    triggered_at = COALESCE(triggered_at, CURRENT_TIMESTAMP)
WHERE is_triggered = FALSE
  AND (
    condition NOT IN ('ABOVE', 'BELOW')
    OR target_price <= 0
  );

ALTER TABLE portfolio_assets
  ADD CONSTRAINT ck_portfolio_assets_average_price_nonnegative
  CHECK (average_price >= 0) NOT VALID;

ALTER TABLE equity_history
  ADD CONSTRAINT ck_equity_history_total_value_nonnegative
  CHECK (total_value >= 0) NOT VALID;

ALTER TABLE limit_orders
  ADD CONSTRAINT ck_limit_orders_target_price_positive
  CHECK (target_price > 0) NOT VALID,
  ADD CONSTRAINT ck_limit_orders_quantity_positive
  CHECK (quantity > 0) NOT VALID,
  ADD CONSTRAINT ck_limit_orders_side
  CHECK (side IN ('BUY', 'SELL')) NOT VALID,
  ADD CONSTRAINT ck_limit_orders_type
  CHECK (type IN ('LIMIT', 'STOP_LOSS')) NOT VALID,
  ADD CONSTRAINT ck_limit_orders_status
  CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED')) NOT VALID,
  ADD CONSTRAINT ck_limit_orders_stop_loss_sell_only
  CHECK (type <> 'STOP_LOSS' OR side = 'SELL') NOT VALID;

ALTER TABLE price_alerts
  ADD CONSTRAINT ck_price_alerts_target_price_positive
  CHECK (target_price > 0) NOT VALID,
  ADD CONSTRAINT ck_price_alerts_condition
  CHECK (condition IN ('ABOVE', 'BELOW')) NOT VALID;

-- A clean database (and any valid upgraded database) ends with fully validated
-- constraints. If a legacy database contains invalid historical records, the
-- NOT VALID checks still reject every new or updated invalid row without making
-- the application upgrade destructive.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM portfolio_assets WHERE average_price < 0) THEN
    ALTER TABLE portfolio_assets VALIDATE CONSTRAINT ck_portfolio_assets_average_price_nonnegative;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM equity_history WHERE total_value < 0) THEN
    ALTER TABLE equity_history VALIDATE CONSTRAINT ck_equity_history_total_value_nonnegative;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM limit_orders WHERE target_price <= 0) THEN
    ALTER TABLE limit_orders VALIDATE CONSTRAINT ck_limit_orders_target_price_positive;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM limit_orders WHERE quantity <= 0) THEN
    ALTER TABLE limit_orders VALIDATE CONSTRAINT ck_limit_orders_quantity_positive;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM limit_orders WHERE side NOT IN ('BUY', 'SELL')) THEN
    ALTER TABLE limit_orders VALIDATE CONSTRAINT ck_limit_orders_side;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM limit_orders WHERE type NOT IN ('LIMIT', 'STOP_LOSS')) THEN
    ALTER TABLE limit_orders VALIDATE CONSTRAINT ck_limit_orders_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM limit_orders WHERE status NOT IN ('PENDING', 'EXECUTED', 'CANCELLED')) THEN
    ALTER TABLE limit_orders VALIDATE CONSTRAINT ck_limit_orders_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM limit_orders WHERE type = 'STOP_LOSS' AND side <> 'SELL') THEN
    ALTER TABLE limit_orders VALIDATE CONSTRAINT ck_limit_orders_stop_loss_sell_only;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM price_alerts WHERE target_price <= 0) THEN
    ALTER TABLE price_alerts VALIDATE CONSTRAINT ck_price_alerts_target_price_positive;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM price_alerts WHERE condition NOT IN ('ABOVE', 'BELOW')) THEN
    ALTER TABLE price_alerts VALIDATE CONSTRAINT ck_price_alerts_condition;
  END IF;
END
$$;

CREATE INDEX idx_orders_user_time ON limit_orders(user_id, created_at DESC);
CREATE INDEX idx_alerts_user_time ON price_alerts(user_id, created_at DESC);
