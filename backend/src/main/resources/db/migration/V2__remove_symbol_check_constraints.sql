-- Remove CHECK constraints restricting symbols to 'BTC', 'ETH', 'SOL'
-- to support all 620+ USDT trading pairs dynamically loaded from Binance

ALTER TABLE portfolio_assets DROP CONSTRAINT IF EXISTS portfolio_assets_symbol_check;
ALTER TABLE trade_transactions DROP CONSTRAINT IF EXISTS trade_transactions_symbol_check;
ALTER TABLE price_snapshots DROP CONSTRAINT IF EXISTS price_snapshots_symbol_check;
