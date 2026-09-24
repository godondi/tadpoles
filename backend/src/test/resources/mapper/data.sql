INSERT INTO users (user_id, username, email, password_hash, display_name, enabled, created_at, updated_at)
VALUES (1, 'admin', 'admin@tadpoles.dev', 'hash', 'Admin User', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO advisors (advisor_id, advisor_name, user_id)
VALUES (3, 'Advisor One', 1);

INSERT INTO model_portfolios (model_portfolio_id, model_name, description, is_active, created_by_user_id, created_at)
VALUES (5, 'Growth', 'Growth portfolio', TRUE, 1, CURRENT_TIMESTAMP);

INSERT INTO clients (client_id, client_name, advisor_id, model_portfolio_id, created_by_user_id, created_at, cash_balance)
VALUES (7, 'Alice Investor', 3, 5, 1, CURRENT_TIMESTAMP, 1200.50);

INSERT INTO instruments (instrument_id, instrument_name, ticker, currency, asset_class, security_type, is_active)
VALUES (11, 'Apple Inc', 'AAPL', 'USD', 'Equity', 'Stock', TRUE);

