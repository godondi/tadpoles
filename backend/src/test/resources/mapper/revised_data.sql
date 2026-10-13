INSERT INTO users (user_id, username, email, password_hash, display_name, enabled, created_at, updated_at)
VALUES
    (1, 'admin01', 'admin01@tadpoles.dev', 'hash', 'Admin User', TRUE, TIMESTAMP '2026-09-24 08:00:00', TIMESTAMP '2026-09-24 08:00:00'),
    (2, 'auditor01', 'auditor01@tadpoles.dev', 'hash', 'Auditor User', TRUE, TIMESTAMP '2026-09-24 08:05:00', TIMESTAMP '2026-09-24 08:05:00'),
    (3, 'analyst01', 'analyst01@tadpoles.dev', 'hash', 'Analyst User', TRUE, TIMESTAMP '2026-09-24 08:10:00', TIMESTAMP '2026-09-24 08:10:00'),
    (4, 'advisor01', 'advisor01@tadpoles.dev', 'hash', 'Advisor User', TRUE, TIMESTAMP '2026-09-24 08:15:00', TIMESTAMP '2026-09-24 08:15:00'),
    (14, 'client01', 'client01@tadpoles.dev', 'hash', 'Client User', TRUE, TIMESTAMP '2026-09-24 08:20:00', TIMESTAMP '2026-09-24 08:20:00');

INSERT INTO roles (role_id, role_name)
VALUES
    (1, 'ADMIN'),
    (2, 'AUDITOR'),
    (3, 'ANALYST'),
    (4, 'ADVISOR'),
    (5, 'CLIENT');

INSERT INTO user_roles (user_id, role_id)
VALUES
    (1, 1),
    (2, 2),
    (3, 3),
    (4, 4),
    (14, 5);

INSERT INTO advisors (advisor_id, advisor_name, user_id)
VALUES (3, 'Advisor One', 4);

INSERT INTO model_portfolios (model_portfolio_id, model_name, description, is_active, created_by_user_id, created_at)
VALUES
    (5, 'Growth', 'Growth portfolio', TRUE, 1, TIMESTAMP '2026-09-24 09:00:00'),
    (6, 'Income', 'Income portfolio', TRUE, 1, TIMESTAMP '2026-09-24 09:05:00');

INSERT INTO instruments (instrument_id, instrument_name, ticker, currency, asset_class, security_type, is_active)
VALUES
    (11, 'Apple Inc', 'AAPL', 'USD', 'Equity', 'Stock', TRUE),
    (12, 'Microsoft Corp', 'MSFT', 'USD', 'Equity', 'Stock', TRUE);

INSERT INTO clients (client_id, user_id, client_name, advisor_id, model_portfolio_id, created_by_user_id, created_at, cash_balance)
VALUES (7, 14, 'Alice Investor', 3, 5, 1, TIMESTAMP '2026-09-24 10:00:00', 1200.50);

INSERT INTO client_profiles (
    client_profile_id,
    user_id,
    client_id,
    phone,
    date_of_birth,
    address_line_1,
    address_line_2,
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
    11,
    14,
    7,
    '+1-555-111-0007',
    DATE '1992-03-15',
    '100 Main Street',
    'Unit 9',
    'New York',
    'NY',
    '10001',
    'United States',
    'Employed',
    250000.00,
    'Moderate',
    'Long-term growth',
    'Email',
    TRUE,
    FALSE,
    TRUE,
    TIMESTAMP '2026-09-24 10:05:00',
    TIMESTAMP '2026-09-24 10:05:00'
);

INSERT INTO client_subscriptions (subscription_id, client_id, model_portfolio_id, subscribed_date, ended_date, status, approved_by_user_id)
VALUES (9, 7, 5, DATE '2026-09-24', NULL, 'ACTIVE', 1);

INSERT INTO trade_suggestions (
    suggestion_id,
    advisor_id,
    client_id,
    instrument_id,
    trade_type,
    quantity,
    proposed_price,
    suggested_at,
    status,
    notes
)
VALUES
    (15, 3, 7, 11, 'BUY', 5.500000, 189.25, TIMESTAMP '2026-09-24 11:00:00', 'SUGGESTED', 'Increase technology exposure');

INSERT INTO refresh_tokens (
    refresh_token_id,
    user_id,
    token_hash,
    expires_at,
    revoked_at,
    created_at
)
VALUES
    (21, 1, 'seed-token-hash-1', TIMESTAMP '2026-10-24 12:00:00', NULL, TIMESTAMP '2026-09-24 12:00:00'),
    (22, 4, 'seed-token-hash-2', TIMESTAMP '2026-10-24 12:05:00', TIMESTAMP '2026-09-25 09:00:00', TIMESTAMP '2026-09-24 12:05:00');

INSERT INTO audit_logs (audit_log_id, user_id, entity_name, entity_id, action_type, old_values, new_values, created_at)
VALUES
    (31, 1, 'client_subscriptions', '9', 'CREATE', NULL, '{"status":"ACTIVE"}', TIMESTAMP '2026-09-24 12:00:00'),
    (32, 2, 'users', '14', 'UPDATE', '{"enabled":true}', '{"enabled":false}', TIMESTAMP '2026-09-24 12:05:00');
