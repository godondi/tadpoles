-- HISTORICAL TEST FIXTURE ONLY: requires migrations 003/004 and role_workspaces.sql.
-- Load before migration 006/007 when rehearsing an old simulation-volume upgrade.
-- Incompatible with the current fresh schema: fixture columns/financial_model were removed.
-- Deterministic prices and fictitious currency openings; never a current startup seed.

BEGIN;

INSERT INTO instruments (instrument_name, ticker, currency, asset_class, security_type)
    VALUES ('Simulation US equity', 'SIM-US', 'USD', 'EQUITY', 'STOCK'),
    ('Simulation UK equity', 'SIM-UK', 'GBP', 'EQUITY', 'STOCK'),
    ('Simulation Indian equity', 'SIM-IN', 'INR', 'EQUITY', 'STOCK'),
    ('Simulation EUR/USD', 'SIM-EURUSD', 'USD', 'FX', 'PAIR'),
    ('Simulation BTC/USD', 'SIM-BTCUSD', 'USD', 'CRYPTO', 'PAIR')
ON CONFLICT (ticker)
    DO NOTHING;

INSERT INTO instrument_trading_policies
SELECT
    instrument_id,
    'deterministic-test-v1',
    v.venue,
    v.units,
    currency,
    currency,
    v.step,
    2,
    v.maxqty,
    v.maxnotional,
    30,
    2,
    120,
    3,
    'SIMULATION_24_7',
    true,
    v.rawunit,
    v.multiplier,
    v.bid,
    v.ask
FROM
    instruments
    JOIN (
    VALUES ('SIM-US', 'TEST-US', 'shares', 1::numeric, 10000::numeric,
        1000000::numeric, 'USD', 1::numeric, 49::numeric, 50::numeric),
            ('SIM-UK', 'TEST-UK', 'shares', 1, 10000, 1000000, 'GBX', 0.01, 2499, 2500),
            ('SIM-IN', 'TEST-IN', 'shares', 1, 10000, 10000000, 'INR', 1, 999, 1000),
            ('SIM-EURUSD', 'TEST-FX', 'EUR base units', 0.01, 100000, 1000000, 'USD', 1, 1.09, 1.10),
        ('SIM-BTCUSD', 'TEST-CRYPTO', 'BTC base units', 0.00000001, 10, 1000000, 'USD', 1,
        59999, 60000)) AS v (ticker, venue, units, step, maxqty, maxnotional, rawunit, multiplier,
        bid, ask)
    USING (ticker)
ON CONFLICT (instrument_id)
    DO NOTHING;

-- Serialize setup with admission and other setup invocations.
SELECT
    client_id
FROM
    clients
    JOIN users USING (user_id)
WHERE
    username IN ('demo.client', 'demo.client2')
ORDER BY
    client_id
FOR UPDATE
    OF clients;

UPDATE
    clients
SET
    financial_model = 'SIMULATION'
WHERE
    user_id IN (
        SELECT
            user_id
        FROM
            users
        WHERE
            username IN ('demo.client', 'demo.client2'));

INSERT INTO client_cash_accounts (client_id, currency)
SELECT
    client_id,
    currency
FROM
    clients
    JOIN users USING (user_id)
    CROSS JOIN (
        VALUES ('USD'),
            ('GBP'),
            ('INR')) c (currency)
WHERE
    username IN ('demo.client', 'demo.client2')
ON CONFLICT
    DO NOTHING;

WITH openings AS (
INSERT INTO cash_journal (posting_id, client_id, currency, delta, setup_reference, posting_group, actor, recorded_at)
    SELECT
        gen_random_uuid (),
        client_id,
        currency,
        CASE WHEN currency = 'INR' THEN
            100000
        ELSE
            10000
        END,
        'demo-simulation-v1:' || client_id || ':' || currency,
        gen_random_uuid (),
        'opt-in local simulation setup',
        now()
    FROM
        client_cash_accounts
        JOIN clients USING (client_id)
        JOIN users USING (user_id)
    WHERE
        username IN ('demo.client', 'demo.client2')
    ON CONFLICT (setup_reference)
        DO NOTHING
    RETURNING
        *)
UPDATE
    client_cash_accounts c
SET
    posted = c.posted + o.delta,
    version = c.version + 1,
    updated_at = now()
FROM
    openings o
WHERE
    c.client_id = o.client_id
    AND c.currency = o.currency;

COMMIT;
