DROP TABLE IF EXISTS client_cash_accounts;
ALTER TABLE users ADD COLUMN trade_access_revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN trade_access_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN trade_access_reason VARCHAR(500);
CREATE TABLE client_cash_accounts (
    client_id INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    posted DECIMAL(28,12) NOT NULL DEFAULT 0,
    reserved DECIMAL(28,12) NOT NULL DEFAULT 0,
    PRIMARY KEY (client_id, currency)
);
INSERT INTO client_cash_accounts(client_id, currency, posted, reserved) VALUES (7, 'USD', 900.25, 20.25);
UPDATE users SET trade_access_revoked_at = CURRENT_TIMESTAMP, trade_access_until = NULL WHERE user_id = 14;
