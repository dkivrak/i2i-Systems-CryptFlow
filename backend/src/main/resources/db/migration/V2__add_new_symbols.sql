-- Expand check constraints for supported symbols to include new coins
ALTER TABLE portfolio_assets DROP CONSTRAINT IF EXISTS portfolio_assets_symbol_check;
ALTER TABLE portfolio_assets ADD CONSTRAINT portfolio_assets_symbol_check CHECK (symbol IN ('BTC','ETH','SOL','BNB','ADA','XRP','DOGE','DOT','AVAX','LINK'));

ALTER TABLE trade_transactions DROP CONSTRAINT IF EXISTS trade_transactions_symbol_check;
ALTER TABLE trade_transactions ADD CONSTRAINT trade_transactions_symbol_check CHECK (symbol IN ('BTC','ETH','SOL','BNB','ADA','XRP','DOGE','DOT','AVAX','LINK'));

ALTER TABLE price_snapshots DROP CONSTRAINT IF EXISTS price_snapshots_symbol_check;
ALTER TABLE price_snapshots ADD CONSTRAINT price_snapshots_symbol_check CHECK (symbol IN ('BTC','ETH','SOL','BNB','ADA','XRP','DOGE','DOT','AVAX','LINK'));
