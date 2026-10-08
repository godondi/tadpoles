INSERT INTO users (user_id, username, email, password_hash, display_name, enabled, created_at, updated_at)
VALUES (1, 'admin', 'admin@tadpoles.dev', 'hash', 'Admin User', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (user_id, username, email, password_hash, display_name, enabled, created_at, updated_at)
VALUES (2, 'alice', 'alice@tadpoles.dev', 'hash', 'Alice Investor', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO advisors (advisor_id, advisor_name, user_id)
VALUES (3, 'Advisor One', 1);

INSERT INTO model_portfolios (model_portfolio_id, model_name, description, is_active, created_by_user_id, created_at)
VALUES (5, 'Growth', 'Growth portfolio', TRUE, 1, CURRENT_TIMESTAMP);

INSERT INTO clients (client_id, client_name, advisor_id, model_portfolio_id, created_by_user_id, created_at, cash_balance)
VALUES (7, 'Alice Investor', 3, 5, 1, CURRENT_TIMESTAMP, 1200.50);

UPDATE clients SET user_id = 2 WHERE client_id = 7;

INSERT INTO client_profiles (
	client_profile_id,
	user_id,
	client_id,
	phone,
	date_of_birth,
	address_line_1,
	city,
	state,
	postal_code,
	country,
	employment_status,
	net_worth,
	risk_tolerance,
	investment_objective,
	preferred_contact_method,
	paperless_statements,
	marketing_opt_in,
	onboarding_complete,
	created_at,
	updated_at
)
VALUES (
	4,
	2,
	7,
	'+1-555-000-0007',
	DATE '1990-03-15',
	'123 Market Street',
	'New York',
	'NY',
	'10001',
	'United States',
	'Employed',
	1200.50,
	'Moderate',
	'Long-term growth',
	'Email',
	TRUE,
	FALSE,
	TRUE,
	CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP
);

INSERT INTO instruments (instrument_id, instrument_name, ticker, currency, asset_class, security_type, is_active)
VALUES (11, 'Apple Inc', 'AAPL', 'USD', 'Equity', 'Stock', TRUE);

INSERT INTO client_trades (trade_id, client_id, instrument_id, submitted_by_user_id, approved_by_user_id, trade_type, quantity, price, trade_date, status, executed_at, reason)
VALUES (21, 7, 11, 1, 1, 'BUY', 2.500000, 110.25, DATE '2026-09-24', 'APPROVED', TIMESTAMP '2026-09-24 10:15:00', 'Add position');

INSERT INTO client_holdings (holding_id, client_id, instrument_id, quantity, as_of_date)
VALUES (13, 7, 11, 9.500000, DATE '2026-09-24');

