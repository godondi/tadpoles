INSERT INTO users (user_id, username, email, password_hash, display_name, enabled, created_at, updated_at)
VALUES (1, 'admin', 'admin@tadpoles.dev', 'hash', 'Admin User', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO model_portfolios (model_portfolio_id, model_name, description, is_active, created_by_user_id, created_at)
VALUES (5, 'Growth', 'Growth portfolio', TRUE, 1, CURRENT_TIMESTAMP);

INSERT INTO instruments (instrument_id, instrument_name, ticker, currency, asset_class, security_type, is_active)
VALUES (11, 'Apple Inc', 'AAPL', 'USD', 'Equity', 'Stock', TRUE);

INSERT INTO instruments (instrument_id, instrument_name, ticker, currency, asset_class, security_type, is_active)
VALUES (12, 'Microsoft Corp', 'MSFT', 'USD', 'Equity', 'Stock', TRUE);

INSERT INTO model_portfolio_holdings (model_portfolio_id, instrument_id, target_weight_pct)
VALUES (5, 11, 60.00);

