-- Seed data for the revised Tadpoles schema.
-- This is intended for local/dev test databases and follows database/schema/revised_schema.sql.

BEGIN;

INSERT INTO users (user_id, username, email, password_hash, display_name, enabled, created_at, updated_at) VALUES
(1,  'admin01',    'admin01@tadpoles.test',    '$2b$12$admin01hash',    'Admin One',        TRUE,  '2026-01-02 08:00:00', '2026-09-24 08:00:00'),
(2,  'auditor01',   'auditor01@tadpoles.test',  '$2b$12$auditor01hash',  'Auditor One',      TRUE,  '2026-01-03 08:00:00', '2026-09-24 08:00:00'),
(3,  'analyst01',   'analyst01@tadpoles.test',  '$2b$12$analyst01hash',  'Analyst One',      TRUE,  '2026-01-04 08:00:00', '2026-09-24 08:00:00'),
(4,  'advisor01',   'advisor01@tadpoles.test',  '$2b$12$advisor01hash',  'Advisor One',      TRUE,  '2026-01-05 08:00:00', '2026-09-24 08:00:00'),
(5,  'advisor02',   'advisor02@tadpoles.test',  '$2b$12$advisor02hash',  'Advisor Two',      TRUE,  '2026-01-06 08:00:00', '2026-09-24 08:00:00'),
(6,  'advisor03',   'advisor03@tadpoles.test',  '$2b$12$advisor03hash',  'Advisor Three',    TRUE,  '2026-01-07 08:00:00', '2026-09-24 08:00:00'),
(7,  'advisor04',   'advisor04@tadpoles.test',  '$2b$12$advisor04hash',  'Advisor Four',     TRUE,  '2026-01-08 08:00:00', '2026-09-24 08:00:00'),
(8,  'advisor05',   'advisor05@tadpoles.test',  '$2b$12$advisor05hash',  'Advisor Five',     TRUE,  '2026-01-09 08:00:00', '2026-09-24 08:00:00'),
(9,  'advisor06',   'advisor06@tadpoles.test',  '$2b$12$advisor06hash',  'Advisor Six',      TRUE,  '2026-01-10 08:00:00', '2026-09-24 08:00:00'),
(10, 'advisor07',   'advisor07@tadpoles.test',  '$2b$12$advisor07hash',  'Advisor Seven',    TRUE,  '2026-01-11 08:00:00', '2026-09-24 08:00:00'),
(11, 'advisor08',   'advisor08@tadpoles.test',  '$2b$12$advisor08hash',  'Advisor Eight',    TRUE,  '2026-01-12 08:00:00', '2026-09-24 08:00:00'),
(12, 'advisor09',   'advisor09@tadpoles.test',  '$2b$12$advisor09hash',  'Advisor Nine',     TRUE,  '2026-01-13 08:00:00', '2026-09-24 08:00:00'),
(13, 'advisor10',   'advisor10@tadpoles.test',  '$2b$12$advisor10hash',  'Advisor Ten',      TRUE,  '2026-01-14 08:00:00', '2026-09-24 08:00:00'),
(14, 'client01',    'client01@tadpoles.test',   '$2b$12$client01hash',   'Client One',       TRUE,  '2026-01-15 08:00:00', '2026-09-24 08:00:00'),
(15, 'client02',    'client02@tadpoles.test',   '$2b$12$client02hash',   'Client Two',       TRUE,  '2026-01-16 08:00:00', '2026-09-24 08:00:00'),
(16, 'client03',    'client03@tadpoles.test',   '$2b$12$client03hash',   'Client Three',     TRUE,  '2026-01-17 08:00:00', '2026-09-24 08:00:00'),
(17, 'client04',    'client04@tadpoles.test',   '$2b$12$client04hash',   'Client Four',      TRUE,  '2026-01-18 08:00:00', '2026-09-24 08:00:00'),
(18, 'client05',    'client05@tadpoles.test',   '$2b$12$client05hash',   'Client Five',      TRUE,  '2026-01-19 08:00:00', '2026-09-24 08:00:00'),
(19, 'client06',    'client06@tadpoles.test',   '$2b$12$client06hash',   'Client Six',       TRUE,  '2026-01-20 08:00:00', '2026-09-24 08:00:00'),
(20, 'client07',    'client07@tadpoles.test',   '$2b$12$client07hash',   'Client Seven',     TRUE,  '2026-01-21 08:00:00', '2026-09-24 08:00:00'),
(21, 'client08',    'client08@tadpoles.test',   '$2b$12$client08hash',   'Client Eight',     TRUE,  '2026-01-22 08:00:00', '2026-09-24 08:00:00'),
(22, 'client09',    'client09@tadpoles.test',   '$2b$12$client09hash',   'Client Nine',      TRUE,  '2026-01-23 08:00:00', '2026-09-24 08:00:00'),
(23, 'client10',    'client10@tadpoles.test',   '$2b$12$client10hash',   'Client Ten',       TRUE,  '2026-01-24 08:00:00', '2026-09-24 08:00:00');

INSERT INTO roles (role_id, role_name) VALUES
(1, 'ADMIN'),
(2, 'AUDITOR'),
(3, 'ANALYST'),
(4, 'ADVISOR'),
(5, 'CLIENT'),
(6, 'COMPLIANCE'),
(7, 'SUPPORT'),
(8, 'OPERATIONS'),
(9, 'REPORTING'),
(10, 'GUEST');

INSERT INTO user_roles (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4),
(5, 4),
(6, 4),
(7, 4),
(8, 4),
(9, 4),
(10, 4),
(11, 4),
(12, 4),
(13, 4),
(14, 5),
(15, 5),
(16, 5),
(17, 5),
(18, 5),
(19, 5),
(20, 5),
(21, 5),
(22, 5),
(23, 5);

INSERT INTO advisors (advisor_id, advisor_name, user_id) VALUES
(1,  'North Star Advisory',    4),
(2,  'Summit Wealth Partners', 5),
(3,  'Harbor Capital',         6),
(4,  'Blue Ridge Advisory',    7),
(5,  'Evergreen Private',      8),
(6,  'Cedar Lane Advisors',    9),
(7,  'Ironwood Planning',      10),
(8,  'Silver Oak Wealth',      11),
(9,  'Prairie View Capital',   12),
(10, 'Metro Bridge Advisory',  13);

INSERT INTO model_portfolios (model_portfolio_id, model_name, description, is_active, created_by_user_id, created_at) VALUES
(1,  'Growth Core',        'Balanced growth allocation for long-term investors', TRUE, 3, '2026-02-01 09:00:00'),
(2,  'Income Focus',       'Income-oriented model with dividend and bond exposure', TRUE, 3, '2026-02-02 09:00:00'),
(3,  'Conservative Plus',  'Lower-volatility allocation with defensive tilt', TRUE, 3, '2026-02-03 09:00:00'),
(4,  'Aggressive Growth',  'High-growth allocation with larger equity allocation', TRUE, 3, '2026-02-04 09:00:00'),
(5,  'ETF Blend',          'Low-cost ETF portfolio covering broad market segments', TRUE, 1, '2026-02-05 09:00:00'),
(6,  'Tech Momentum',      'Growth portfolio focused on technology leaders', TRUE, 1, '2026-02-06 09:00:00'),
(7,  'Dividend Growth',    'Dividend-focused income with quality screen', TRUE, 1, '2026-02-07 09:00:00'),
(8,  'Balanced Global',    'Diversified global allocation across regions and sectors', TRUE, 1, '2026-02-08 09:00:00'),
(9,  'Capital Preservation','Low-risk allocation with cash and short-duration exposure', FALSE, 1, '2026-02-09 09:00:00'),
(10, 'ESG Opportunity',    'Sustainable investing model with ESG screens', TRUE, 1, '2026-02-10 09:00:00');

INSERT INTO clients (client_id, client_name, advisor_id, model_portfolio_id, created_by_user_id, created_at) VALUES
(1,  'Acme Family Office',      1,  1,  1,  '2026-03-01 10:00:00'),
(2,  'Barton Holdings',         2,  2,  1,  '2026-03-02 10:00:00'),
(3,  'Crescent Trust',          3,  3,  1,  '2026-03-03 10:00:00'),
(4,  'Delta Capital',           4,  4,  1,  '2026-03-04 10:00:00'),
(5,  'Emerald Retirement',      5,  5,  1,  '2026-03-05 10:00:00'),
(6,  'Fjord Partners',          6,  6,  1,  '2026-03-06 10:00:00'),
(7,  'Granite Investors',       7,  7,  1,  '2026-03-07 10:00:00'),
(8,  'Harbor Street Wealth',    8,  8,  1,  '2026-03-08 10:00:00'),
(9,  'Iron Gate Capital',       9,  9,  1,  '2026-03-09 10:00:00'),
(10, 'Juniper Growth Group',   10, 10,  1,  '2026-03-10 10:00:00');

INSERT INTO instruments (instrument_id, instrument_name, ticker, currency, asset_class, security_type, is_active) VALUES
(1,  'Apple Inc.',                 'AAPL',  'USD', 'Equity',     'COMMON_STOCK', TRUE),
(2,  'Microsoft Corp.',            'MSFT',  'USD', 'Equity',     'COMMON_STOCK', TRUE),
(3,  'Alphabet Inc.',              'GOOGL', 'USD', 'Equity',     'COMMON_STOCK', TRUE),
(4,  'Amazon.com Inc.',            'AMZN',  'USD', 'Equity',     'COMMON_STOCK', TRUE),
(5,  'Tesla Inc.',                 'TSLA',  'USD', 'Equity',     'COMMON_STOCK', TRUE),
(6,  'Vanguard S&P 500 ETF',       'VOO',   'USD', 'ETF',        'ETF',          TRUE),
(7,  'iShares Core U.S. Aggregate Bond ETF', 'AGG', 'USD', 'Fixed Income', 'ETF', TRUE),
(8,  'Johnson & Johnson',          'JNJ',   'USD', 'Equity',     'COMMON_STOCK', TRUE),
(9,  'Procter & Gamble Co.',       'PG',    'USD', 'Equity',     'COMMON_STOCK', TRUE),
(10, 'Cash Reserve',               'CASH',  'USD', 'Cash',       'CASH_EQUIVALENT', TRUE),
(11, 'NVIDIA Corp.',               'NVDA',  'USD', 'Equity',     'COMMON_STOCK', TRUE),
(12, 'JPMorgan Chase & Co.',       'JPM',   'USD', 'Equity',     'COMMON_STOCK', TRUE),
(13, 'UnitedHealth Group',         'UNH',   'USD', 'Equity',     'COMMON_STOCK', TRUE),
(14, 'Exxon Mobil Corp.',          'XOM',   'USD', 'Equity',     'COMMON_STOCK', TRUE),
(15, 'Berkshire Hathaway Class B', 'BRK.B', 'USD', 'Equity',     'COMMON_STOCK', TRUE);

INSERT INTO model_portfolio_holdings (model_portfolio_id, instrument_id, target_weight_pct) VALUES
(1,  1, 15.00),
(2,  2, 12.50),
(3,  6, 20.00),
(4, 11, 18.00),
(5,  7, 25.00),
(6,  5, 22.50),
(7,  9, 10.00),
(8, 12, 14.50),
(9, 10, 50.00),
(10,13, 16.25);

INSERT INTO client_subscriptions (subscription_id, client_id, model_portfolio_id, subscribed_date, ended_date, status, approved_by_user_id) VALUES
(1,  1,  1, '2026-03-01', NULL,         'ACTIVE', 1),
(2,  2,  2, '2026-03-02', NULL,         'ACTIVE', 1),
(3,  3,  3, '2026-03-03', NULL,         'PAUSED', 1),
(4,  4,  4, '2026-03-04', NULL,         'ACTIVE', 1),
(5,  5,  5, '2026-03-05', NULL,         'ACTIVE', 1),
(6,  6,  6, '2026-03-06', NULL,         'ENDED',  1),
(7,  7,  7, '2026-03-07', NULL,         'ACTIVE', 1),
(8,  8,  8, '2026-03-08', NULL,         'ACTIVE', 1),
(9,  9,  9, '2026-03-09', '2026-08-31',  'ENDED',  1),
(10, 10, 10, '2026-03-10', NULL,         'ACTIVE', 1);

INSERT INTO trade_suggestions (suggestion_id, advisor_id, client_id, instrument_id, trade_type, quantity, proposed_price, suggested_at, status, notes) VALUES
(1,  1,  1,  1, 'BUY',  12.500000, 215.40, '2026-04-01 11:00:00', 'VIEWED',    'Add exposure to large-cap growth.'),
(2,  2,  2,  2, 'BUY',   8.000000, 402.15, '2026-04-02 11:00:00', 'SUGGESTED', 'Increase core technology allocation.'),
(3,  3,  3,  3, 'SELL',  5.250000, 170.25, '2026-04-03 11:00:00', 'ACCEPTED',  'Trim overweight position after strong run.'),
(4,  4,  4,  4, 'BUY',   3.500000, 188.90, '2026-04-04 11:00:00', 'REJECTED',  'Rebalance toward e-commerce exposure.'),
(5,  5,  5,  5, 'BUY',   7.750000, 224.10, '2026-04-05 11:00:00', 'SUGGESTED', 'Add momentum sleeve.'),
(6,  6,  6,  6, 'BUY',  10.000000, 512.75, '2026-04-06 11:00:00', 'EXPIRED',   'ETF allocation refresh.'),
(7,  7,  7,  7, 'SELL',  4.000000,  97.65, '2026-04-07 11:00:00', 'VIEWED',    'Reduce fixed income duration.'),
(8,  8,  8,  8, 'BUY',   6.125000, 164.30, '2026-04-08 11:00:00', 'ACCEPTED',  'Healthcare defensive tilt.'),
(9,  9,  9,  9, 'BUY',   9.000000, 151.95, '2026-04-09 11:00:00', 'SUGGESTED', 'Consumer staples allocation.'),
(10, 10, 10, 11, 'BUY',  2.250000, 118.10, '2026-04-10 11:00:00', 'VIEWED',    'Add AI growth exposure.');

INSERT INTO client_trades (trade_id, client_id, instrument_id, submitted_by_user_id, approved_by_user_id, trade_type, quantity, price, trade_date, status, executed_at, reason) VALUES
(1,  1,  1, 14, 1, 'BUY',  10.500000, 214.95, '2026-04-11', 'EXECUTED', '2026-04-11 14:15:00', 'Client approved strategy rebalance.'),
(2,  2,  2, 15, 1, 'BUY',   4.000000, 401.10, '2026-04-12', 'APPROVED', NULL,                  'Awaiting market open execution.'),
(3,  3,  3, 16, NULL, 'SELL', 2.500000, 171.75, '2026-04-13', 'PENDING',  NULL,                  'Submitted after quarterly review.'),
(4,  4,  4, 17, 1, 'BUY',   6.750000, 187.45, '2026-04-14', 'REJECTED', NULL,                  'Position size exceeded policy.'),
(5,  5,  5, 18, 3, 'BUY',   3.125000, 225.30, '2026-04-15', 'EXECUTED', '2026-04-15 15:00:00', 'Client added to growth sleeve.'),
(6,  6,  6, 19, NULL, 'BUY',  8.000000, 513.80, '2026-04-16', 'APPROVED', NULL,                  'Staged purchase across two sessions.'),
(7,  7,  7, 20, 1, 'SELL',  1.750000,  98.05, '2026-04-17', 'EXECUTED', '2026-04-17 10:30:00', 'Harvested gains from bond sleeve.'),
(8,  8,  8, 21, NULL, 'BUY',  5.500000, 165.80, '2026-04-18', 'PENDING',  NULL,                  'Client requested healthcare tilt.'),
(9,  9,  9, 22, 1, 'BUY',   7.000000, 152.35, '2026-04-19', 'APPROVED', NULL,                  'Queued for execution next session.'),
(10, 10, 11, 23, 3, 'BUY',  2.000000, 119.20, '2026-04-20', 'EXECUTED', '2026-04-20 13:20:00', 'Fractional add to growth basket.');

INSERT INTO client_holdings (holding_id, client_id, instrument_id, quantity, as_of_date) VALUES
(1,  1,  1,  22.750000, '2026-04-11'),
(2,  2,  2,  14.000000, '2026-04-12'),
(3,  3,  3,   8.500000, '2026-04-13'),
(4,  4,  4,  16.250000, '2026-04-14'),
(5,  5,  5,  11.125000, '2026-04-15'),
(6,  6,  6,  19.000000, '2026-04-16'),
(7,  7,  7,   6.750000, '2026-04-17'),
(8,  8,  8,  13.500000, '2026-04-18'),
(9,  9,  9,   9.250000, '2026-04-19'),
(10, 10, 11,   4.500000, '2026-04-20');

INSERT INTO refresh_tokens (refresh_token_id, user_id, token_hash, expires_at, revoked_at, created_at) VALUES
(1,  1,  'hash-refresh-001', '2026-10-24 08:00:00', NULL,                 '2026-09-24 08:00:00'),
(2,  2,  'hash-refresh-002', '2026-10-24 08:00:00', NULL,                 '2026-09-24 08:05:00'),
(3,  3,  'hash-refresh-003', '2026-10-24 08:00:00', NULL,                 '2026-09-24 08:10:00'),
(4,  4,  'hash-refresh-004', '2026-10-24 08:00:00', NULL,                 '2026-09-24 08:15:00'),
(5, 14,  'hash-refresh-005', '2026-10-24 08:00:00', NULL,                 '2026-09-24 08:20:00'),
(6, 15,  'hash-refresh-006', '2026-10-24 08:00:00', NULL,                 '2026-09-24 08:25:00'),
(7, 16,  'hash-refresh-007', '2026-10-24 08:00:00', '2026-09-24 08:30:00', '2026-09-24 08:30:00'),
(8, 17,  'hash-refresh-008', '2026-10-24 08:00:00', NULL,                 '2026-09-24 08:35:00'),
(9, 18,  'hash-refresh-009', '2026-10-24 08:00:00', NULL,                 '2026-09-24 08:40:00'),
(10, 19, 'hash-refresh-010', '2026-10-24 08:00:00', NULL,                 '2026-09-24 08:45:00');

INSERT INTO audit_logs (audit_log_id, user_id, entity_name, entity_id, action_type, old_values, new_values, created_at) VALUES
(1,  1, 'users',               '14', 'CREATE',   NULL,                                                                 '{"username":"client01","enabled":true}'::jsonb,                           '2026-09-24 08:00:00'),
(2,  1, 'roles',               '5',  'CREATE',   NULL,                                                                 '{"role_name":"CLIENT"}'::jsonb,                                             '2026-09-24 08:01:00'),
(3,  3, 'model_portfolios',    '1',  'UPDATE',   '{"is_active":false}'::jsonb,                                        '{"is_active":true}'::jsonb,                                                   '2026-09-24 08:02:00'),
(4,  4, 'clients',             '1',  'UPDATE',   '{"advisor_id":2}'::jsonb,                                           '{"advisor_id":1}'::jsonb,                                                     '2026-09-24 08:03:00'),
(5,  5, 'trade_suggestions',   '5',  'CREATE',   NULL,                                                                 '{"status":"SUGGESTED","quantity":7.75}'::jsonb,                           '2026-09-24 08:04:00'),
(6,  1, 'client_trades',       '2',  'APPROVE',   '{"status":"PENDING"}'::jsonb,                                     '{"status":"APPROVED"}'::jsonb,                                              '2026-09-24 08:05:00'),
(7,  2, 'client_trades',       '4',  'REJECT',    '{"status":"PENDING"}'::jsonb,                                     '{"status":"REJECTED","reason":"Position size exceeded policy."}'::jsonb, '2026-09-24 08:06:00'),
(8,  3, 'client_holdings',     '10', 'UPDATE',    '{"quantity":4.0}'::jsonb,                                           '{"quantity":4.5}'::jsonb,                                                     '2026-09-24 08:07:00'),
(9,  1, 'refresh_tokens',      '7',  'REVOKE',    '{"revoked_at":null}'::jsonb,                                        '{"revoked_at":"2026-09-24T08:30:00"}'::jsonb,                               '2026-09-24 08:08:00'),
(10, 2, 'client_subscriptions','9',  'END',       '{"status":"ACTIVE"}'::jsonb,                                      '{"status":"ENDED","ended_date":"2026-08-31"}'::jsonb,                   '2026-09-24 08:09:00');

SELECT setval(pg_get_serial_sequence('users', 'user_id'), 23, true);
SELECT setval(pg_get_serial_sequence('roles', 'role_id'), 10, true);
SELECT setval(pg_get_serial_sequence('advisors', 'advisor_id'), 10, true);
SELECT setval(pg_get_serial_sequence('model_portfolios', 'model_portfolio_id'), 10, true);
SELECT setval(pg_get_serial_sequence('clients', 'client_id'), 10, true);
SELECT setval(pg_get_serial_sequence('instruments', 'instrument_id'), 15, true);
SELECT setval(pg_get_serial_sequence('client_subscriptions', 'subscription_id'), 10, true);
SELECT setval(pg_get_serial_sequence('trade_suggestions', 'suggestion_id'), 10, true);
SELECT setval(pg_get_serial_sequence('client_trades', 'trade_id'), 10, true);
SELECT setval(pg_get_serial_sequence('client_holdings', 'holding_id'), 10, true);
SELECT setval(pg_get_serial_sequence('refresh_tokens', 'refresh_token_id'), 10, true);
SELECT setval(pg_get_serial_sequence('audit_logs', 'audit_log_id'), 10, true);

COMMIT;

