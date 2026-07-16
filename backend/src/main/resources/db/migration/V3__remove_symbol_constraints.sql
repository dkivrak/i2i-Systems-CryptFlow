-- Remove all symbol CHECK constraints to allow any symbol from Binance API
ALTER TABLE portfolio_assets DROP CONSTRAINT IF EXISTS portfolio_assets_symbol_check;
ALTER TABLE trade_transactions DROP CONSTRAINT IF EXISTS trade_transactions_symbol_check;
ALTER TABLE price_snapshots DROP CONSTRAINT IF EXISTS price_snapshots_symbol_check;
