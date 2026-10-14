-- Repeatable forward migration. Existing OPERATIONS assignments remain unchanged.
BEGIN;
INSERT INTO roles (role_name) VALUES ('TRADING_OPS'), ('FINANCE') ON CONFLICT (role_name) DO NOTHING;
CREATE INDEX IF NOT EXISTS client_trades_client_date_id_idx ON client_trades (client_id, trade_date DESC, trade_id DESC);
CREATE INDEX IF NOT EXISTS client_holdings_current_idx ON client_holdings (client_id, instrument_id, as_of_date DESC, holding_id DESC);
COMMIT;
