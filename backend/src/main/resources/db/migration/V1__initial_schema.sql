CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(320) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE wallets (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL UNIQUE REFERENCES users(id),
  usd_balance NUMERIC(19,2) NOT NULL CHECK (usd_balance >= 0),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE portfolio_assets (
  id UUID PRIMARY KEY,
  wallet_id UUID NOT NULL REFERENCES wallets(id),
  symbol VARCHAR(10) NOT NULL CHECK (symbol IN ('BTC','ETH','SOL')),
  quantity NUMERIC(28,8) NOT NULL CHECK (quantity >= 0),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_portfolio_wallet_symbol UNIQUE (wallet_id, symbol)
);
CREATE INDEX idx_portfolio_wallet ON portfolio_assets(wallet_id);

CREATE TABLE trade_transactions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  wallet_id UUID NOT NULL REFERENCES wallets(id),
  symbol VARCHAR(10) NOT NULL CHECK (symbol IN ('BTC','ETH','SOL')),
  side VARCHAR(4) NOT NULL CHECK (side IN ('BUY','SELL')),
  quantity NUMERIC(28,8) NOT NULL CHECK (quantity > 0),
  unit_price_usd NUMERIC(19,2) NOT NULL CHECK (unit_price_usd > 0),
  total_usd NUMERIC(19,2) NOT NULL CHECK (total_usd > 0),
  executed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_trade_user_time ON trade_transactions(user_id, executed_at DESC);
CREATE INDEX idx_trade_symbol_time ON trade_transactions(symbol, executed_at DESC);

CREATE TABLE price_snapshots (
  id BIGSERIAL PRIMARY KEY,
  symbol VARCHAR(10) NOT NULL CHECK (symbol IN ('BTC','ETH','SOL')),
  price_usd NUMERIC(19,2) NOT NULL CHECK (price_usd > 0),
  recorded_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uk_price_symbol_time UNIQUE (symbol, recorded_at)
);
CREATE INDEX idx_price_symbol_time ON price_snapshots(symbol, recorded_at DESC);

