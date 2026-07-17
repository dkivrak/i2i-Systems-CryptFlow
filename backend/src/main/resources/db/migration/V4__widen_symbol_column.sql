-- Widen symbol column to VARCHAR(50) to support longer coin symbols from Binance API
ALTER TABLE portfolio_assets ALTER COLUMN symbol TYPE VARCHAR(50);
ALTER TABLE trade_transactions ALTER COLUMN symbol TYPE VARCHAR(50);
ALTER TABLE price_snapshots ALTER COLUMN symbol TYPE VARCHAR(50);
