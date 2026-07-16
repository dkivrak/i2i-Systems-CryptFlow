-- Increase price column precision to NUMERIC(28,8) to support cheap coins without rounding to 0
ALTER TABLE price_snapshots ALTER COLUMN price_usd TYPE NUMERIC(28,8);
ALTER TABLE trade_transactions ALTER COLUMN unit_price_usd TYPE NUMERIC(28,8);
