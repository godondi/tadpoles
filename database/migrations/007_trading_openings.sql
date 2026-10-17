-- Requires migration 006. Preserve historical balances as evidence, not spendable USD.
-- Unknown currency/old positions are quarantined; no historical money is guessed.
-- Removes fixture pricing and makes the provider-backed ledger the only write path.

BEGIN;

-- Record originals before restricting their use. Evidence remains available for review.
INSERT INTO financial_migration_exceptions (source_reference, kind, client_id, facts)
SELECT
    'scalar-cash:' || client_id,
    'UNKNOWN_CASH_CURRENCY',
    client_id,
    jsonb_build_object('cashBalance', cash_balance::text, 'disposition', 'Historical only; no USD opening inferred')
FROM
    clients
WHERE
    cash_balance <> 0;

INSERT INTO financial_migration_exceptions (source_reference, kind, client_id, instrument_id, facts)
SELECT
    'dated-holding:' || holding_id,
    'UNVERIFIED_DATED_HOLDING',
    client_id,
    instrument_id,
    to_jsonb (h)
FROM
    client_holdings h;

INSERT INTO financial_migration_exceptions (source_reference, kind, client_id, facts)
SELECT
    'prior-cash:' || client_id || ':' || currency,
    'PRIOR_CASH_REQUIRES_DISPOSITION',
    client_id,
    to_jsonb (a)
FROM
    client_cash_accounts a
WHERE
    posted <> 0
    OR currency <> 'USD';

INSERT INTO financial_migration_exceptions (source_reference, kind, client_id, instrument_id, facts)
SELECT
    'prior-position:' || client_id || ':' || instrument_id,
    'PRIOR_POSITION_REQUIRES_DISPOSITION',
    client_id,
    instrument_id,
    to_jsonb (p)
FROM
    client_positions p
WHERE
    posted <> 0;

INSERT INTO financial_migration_exceptions (source_reference, kind, instrument_id, facts)
SELECT
    'prior-policy:' || instrument_id,
    'HISTORICAL_FIXTURE_POLICY',
    instrument_id,
    to_jsonb (p)
FROM
    instrument_trading_policies p;

-- Retain balances and all journals intact. Quarantined balances cannot fund any command.
UPDATE
    client_cash_accounts
SET
    available_for_trading = false
WHERE
    posted <> 0
    OR currency <> 'USD';

UPDATE
    client_positions
SET
    available_for_trading = false
WHERE
    posted <> 0;

INSERT INTO client_cash_accounts (client_id, currency)
SELECT
    client_id,
    'USD'
FROM
    clients
ON CONFLICT
    DO NOTHING;

UPDATE
    instruments
SET
    is_active = false
WHERE
    ticker LIKE 'SIM-%'
    OR ticker IN ('CASH', 'USD', 'GBP', 'INR', 'EUR');

UPDATE
    instrument_trading_policies
SET
    enabled = false,
    session = 'CLOSED';

ALTER TABLE instrument_trading_policies
    DROP COLUMN fixture_bid;

ALTER TABLE instrument_trading_policies
    DROP COLUMN fixture_ask;

ALTER TABLE instrument_trading_policies
    DROP CONSTRAINT provider_session;

ALTER TABLE instrument_trading_policies
    ADD CONSTRAINT provider_session CHECK (session IN ('PROVIDER_OPEN', 'CLOSED'));

-- Older endpoints must not change the historical financial model after cutover.
CREATE OR REPLACE FUNCTION guard_legacy_financial_write ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_TABLE_NAME = 'client_trades' AND TG_OP <> 'DELETE' AND NEW.record_kind = 'SELF_DIRECTED' THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Historical financial records are read-only; use the self-directed trading service';
END
$$;

CREATE OR REPLACE FUNCTION guard_client_cash ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.cash_balance <> 0 THEN
            RAISE EXCEPTION 'New accounts start at zero; use controlled cash movements';
        END IF;
    ELSIF NEW.cash_balance IS DISTINCT FROM OLD.cash_balance THEN
        RAISE EXCEPTION 'Historical scalar cash is read-only; use the journal-backed USD account';
    END IF;
    RETURN NEW;
END
$$;

DROP TRIGGER guard_client_cash ON clients;

CREATE TRIGGER guard_client_cash
    BEFORE INSERT OR UPDATE ON clients
    FOR EACH ROW
    EXECUTE FUNCTION guard_client_cash ();

ALTER TABLE clients
    DROP COLUMN financial_model;

INSERT INTO schema_migrations (version)
    VALUES (7);

COMMIT;
