INSERT INTO instruments (instrument_id, instrument_name, ticker, currency, asset_class, security_type, is_active)
VALUES (12, 'Microsoft Corp', 'MSFT', 'USD', 'Equity', 'Stock', TRUE);

INSERT INTO clients (client_id, client_name, advisor_id, model_portfolio_id, created_by_user_id, created_at, cash_balance)
VALUES (8, 'Bob Trader', 3, 5, 1, TIMESTAMP '2026-09-24 10:30:00', 2500.00);

INSERT INTO client_trades (trade_id, client_id, instrument_id, submitted_by_user_id, approved_by_user_id, trade_type, quantity, price, trade_date, status, executed_at, reason)
VALUES
    (22, 7, 11, 1, 1, 'SELL', 1.000000, 115.00, DATE '2026-09-25', 'EXECUTED', TIMESTAMP '2026-09-25 09:00:00', 'Reduce position'),
    (23, 8, 11, 1, 1, 'BUY', 3.000000, 90.00, DATE '2026-09-25', 'EXECUTED', TIMESTAMP '2026-09-25 10:00:00', 'Add position'),
    (24, 8, 12, 1, NULL, 'BUY', 5.000000, 50.00, DATE '2026-09-26', 'PENDING', NULL, 'Open new position');
