INSERT INTO users (user_id, username, email, password_hash, display_name, enabled, created_at, updated_at)
VALUES (1, 'admin', 'admin@tadpoles.dev', 'hash', 'Admin User', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (14, 'client14', 'client14@tadpoles.dev', 'hash', 'Client Fourteen', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO advisors (advisor_id, advisor_name, user_id)
VALUES (3, 'Advisor One', 1);

INSERT INTO model_portfolios (model_portfolio_id, model_name, description, is_active, created_by_user_id, created_at)
VALUES (5, 'Growth', 'Growth portfolio', TRUE, 1, CURRENT_TIMESTAMP);

INSERT INTO clients (client_id, client_name, advisor_id, model_portfolio_id, created_by_user_id, created_at, cash_balance)
VALUES (7, 'Alice Investor', 3, 5, 1, CURRENT_TIMESTAMP, 1000.00);

INSERT INTO instruments (instrument_id, instrument_name, ticker, currency, asset_class, security_type, is_active)
VALUES (11, 'Apple Inc', 'AAPL', 'USD', 'Equity', 'Stock', TRUE);

INSERT INTO client_holdings (holding_id, client_id, instrument_id, quantity, as_of_date)
VALUES (13, 7, 11, 7.000000, DATE '2026-09-23');

INSERT INTO client_trades (trade_id, client_id, instrument_id, submitted_by_user_id, approved_by_user_id, trade_type, quantity, price, trade_date, status, executed_at, reason)
VALUES (21, 7, 11, 14, 1, 'BUY', 2.500000, 80.20, DATE '2026-09-24', 'APPROVED', NULL, 'Add position');

