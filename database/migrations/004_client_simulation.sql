-- Requires the legacy base schema; normally follows migration 003.
-- Historical simulation-to-ledger foundation, still required by upgrades.
-- Later migrations replace simulation policies, but retain these journals/guards.
-- Run once through migrate.sh; the ALTER/CREATE statements are not repeatable.

BEGIN;

CREATE TABLE IF NOT EXISTS schema_migrations (
    version integer PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE clients
    ADD COLUMN financial_model text NOT NULL DEFAULT 'LEGACY' CHECK (financial_model IN
    ('LEGACY', 'SIMULATION'));

ALTER TABLE clients
    ADD COLUMN event_cursor bigint NOT NULL DEFAULT 0;

ALTER TABLE client_trades
    ADD COLUMN record_kind text NOT NULL DEFAULT 'LEGACY' CHECK (record_kind IN ('LEGACY', 'SELF_DIRECTED'));

ALTER TABLE client_trades
    ALTER COLUMN price DROP NOT NULL;

ALTER TABLE client_trades
    ALTER COLUMN quantity TYPE numeric(28, 12);

ALTER TABLE client_trades
    DROP CONSTRAINT client_trades_status_check;

ALTER TABLE client_trades
    ADD CONSTRAINT client_trades_lifecycle_check CHECK (
        (
            record_kind = 'LEGACY'
            AND status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXECUTED')
            AND price IS NOT NULL
        ) OR (
            record_kind = 'SELF_DIRECTED'
            AND status IN ('ACCEPTED', 'EXECUTING', 'FILLED', 'REJECTED')
            AND price IS NULL
        )
    );

ALTER TABLE client_trades
    ADD COLUMN idempotency_key text;

ALTER TABLE client_trades
    ADD COLUMN request_hash text;

ALTER TABLE client_trades
    ADD COLUMN submitted_at timestamptz;

ALTER TABLE client_trades
    ADD COLUMN accepted_at timestamptz;

ALTER TABLE client_trades
    ADD COLUMN updated_at timestamptz;

ALTER TABLE client_trades
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE client_trades
    ADD COLUMN order_type text;

ALTER TABLE client_trades
    ADD COLUMN currency_snapshot text;

ALTER TABLE client_trades
    ADD COLUMN policy_snapshot jsonb;

ALTER TABLE client_trades
    ADD COLUMN rejection_stage text;

ALTER TABLE client_trades
    ADD COLUMN rejection_code text;

CREATE UNIQUE INDEX order_request_key ON client_trades (client_id, idempotency_key)
WHERE
    record_kind = 'SELF_DIRECTED';

ALTER TABLE client_trades
    ADD CONSTRAINT self_directed_intent CHECK (
        record_kind = 'LEGACY' OR (
            idempotency_key IS NOT NULL
            AND request_hash IS NOT NULL
            AND submitted_at IS NOT NULL
            AND updated_at IS NOT NULL
            AND order_type = 'MARKET'
            AND submitted_by_user_id IS NOT NULL
            AND (status = 'REJECTED' OR accepted_at IS NOT NULL)
        )
    );

-- Policies store exact units, limits and quote rules captured when an order is accepted.
CREATE TABLE instrument_trading_policies (
    instrument_id integer PRIMARY KEY REFERENCES instruments,
    policy_version text NOT NULL,
    venue text NOT NULL,
    quantity_unit text NOT NULL,
    price_unit text NOT NULL,
    currency text NOT NULL,
    quantity_step numeric(28, 12) NOT NULL CHECK (quantity_step > 0),
    currency_scale integer NOT NULL CHECK (currency_scale BETWEEN 0 AND 12),
    max_quantity numeric(28, 12) NOT NULL CHECK (max_quantity > 0),
    max_notional numeric(28, 12) NOT NULL CHECK (max_notional > 0),
    quote_max_age_seconds integer NOT NULL CHECK (quote_max_age_seconds BETWEEN 1 AND 300),
    future_skew_seconds integer NOT NULL CHECK (future_skew_seconds BETWEEN 0 AND 30),
    retry_seconds integer NOT NULL CHECK (retry_seconds BETWEEN 1 AND 3600),
    max_attempts integer NOT NULL CHECK (max_attempts BETWEEN 1 AND 10),
    session text NOT NULL CHECK (session IN ('SIMULATION_24_7', 'CLOSED')),
    enabled boolean NOT NULL DEFAULT false,
    raw_price_unit text NOT NULL,
    price_multiplier numeric(28, 12) NOT NULL CHECK (price_multiplier > 0),
    fixture_bid numeric(28, 12) NOT NULL CHECK (fixture_bid > 0),
    fixture_ask numeric(28, 12) NOT NULL CHECK (fixture_ask >= fixture_bid)
);

-- Mutable balance projections: available = posted - reserved; negatives are rejected.
CREATE TABLE client_cash_accounts (
    client_id integer NOT NULL REFERENCES clients,
    currency text NOT NULL,
    posted numeric(28, 12) NOT NULL DEFAULT 0,
    reserved numeric(28, 12) NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (client_id, currency),
    CHECK (posted >= reserved AND reserved >= 0)
);

CREATE TABLE client_positions (
    client_id integer NOT NULL REFERENCES clients,
    instrument_id integer NOT NULL REFERENCES instruments,
    posted numeric(28, 12) NOT NULL DEFAULT 0,
    reserved numeric(28, 12) NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (client_id, instrument_id),
    CHECK (posted >= reserved AND reserved >= 0)
);

-- An accepted order reserves cash or units until consumed by a fill or released.
CREATE TABLE order_reservations (
    order_id integer PRIMARY KEY REFERENCES client_trades,
    resource text NOT NULL CHECK (resource IN ('CASH', 'UNITS')),
    amount numeric(28, 12) NOT NULL CHECK (amount > 0),
    state text NOT NULL CHECK (state IN ('ACTIVE', 'CONSUMED', 'RELEASED')),
    created_at timestamptz NOT NULL,
    ended_at timestamptz,
    CHECK ((state = 'ACTIVE') = (ended_at IS NULL))
);

-- Append-only evidence explains which quotes/rules produced each trading decision.
CREATE TABLE order_quote_observations (
    quote_id uuid PRIMARY KEY,
    order_id integer NOT NULL REFERENCES client_trades,
    stage text NOT NULL,
    facts jsonb NOT NULL,
    recorded_at timestamptz NOT NULL
);

CREATE TABLE order_rule_decisions (
    decision_id uuid PRIMARY KEY,
    order_id integer NOT NULL REFERENCES client_trades,
    stage text NOT NULL,
    policy_version text NOT NULL,
    passed boolean NOT NULL,
    code text NOT NULL,
    explanation text NOT NULL,
    quote_id uuid REFERENCES order_quote_observations,
    recorded_at timestamptz NOT NULL
);

CREATE TABLE order_fills (
    fill_id uuid PRIMARY KEY,
    order_id integer NOT NULL UNIQUE REFERENCES client_trades,
    execution_reference text NOT NULL UNIQUE,
    quote_id uuid NOT NULL REFERENCES order_quote_observations,
    quantity numeric(28, 12) NOT NULL CHECK (quantity > 0),
    price numeric(28, 12) NOT NULL CHECK (price > 0),
    gross numeric(28, 12) NOT NULL CHECK (gross > 0),
    fee numeric(28, 12) NOT NULL CHECK (fee = 0),
    currency text NOT NULL,
    posting_group uuid NOT NULL UNIQUE,
    executed_at timestamptz NOT NULL,
    booked_at timestamptz NOT NULL
);

-- Journals are the source facts; account/position tables are their current projections.
CREATE TABLE cash_journal (
    posting_id uuid PRIMARY KEY,
    client_id integer NOT NULL REFERENCES clients,
    currency text NOT NULL,
    delta numeric(28, 12) NOT NULL,
    fill_id uuid UNIQUE REFERENCES order_fills,
    setup_reference text UNIQUE,
    posting_group uuid NOT NULL,
    actor text NOT NULL,
    recorded_at timestamptz NOT NULL,
    CHECK ((fill_id IS NULL) <> (setup_reference IS NULL))
);

CREATE TABLE position_journal (
    posting_id uuid PRIMARY KEY,
    client_id integer NOT NULL REFERENCES clients,
    instrument_id integer NOT NULL REFERENCES instruments,
    delta numeric(28, 12) NOT NULL,
    fill_id uuid UNIQUE REFERENCES order_fills,
    setup_reference text UNIQUE,
    posting_group uuid NOT NULL,
    actor text NOT NULL,
    recorded_at timestamptz NOT NULL,
    CHECK ((fill_id IS NULL) <> (setup_reference IS NULL))
);

CREATE TABLE order_events (
    event_id uuid PRIMARY KEY,
    order_id integer NOT NULL REFERENCES client_trades,
    client_id integer NOT NULL REFERENCES clients,
    cursor bigint NOT NULL,
    sequence bigint
        NOT NULL,
        from_state text,
        to_state text NOT NULL,
        reason text NOT NULL,
        actor text NOT NULL,
        quote_id uuid REFERENCES order_quote_observations,
        fill_id uuid REFERENCES order_fills,
        occurred_at timestamptz NOT NULL,
        UNIQUE (order_id, sequence),
        UNIQUE (client_id, cursor)
);

-- Durable jobs allow a worker to resume after a crash without duplicating fills.
CREATE TABLE order_execution_jobs (
    order_id integer PRIMARY KEY REFERENCES client_trades,
    claim_token uuid,
    lease_until timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt timestamptz NOT NULL,
    deadline timestamptz NOT NULL,
    completed boolean NOT NULL DEFAULT false
);

CREATE INDEX ready_execution_jobs ON order_execution_jobs (next_attempt)
WHERE
    NOT completed;

CREATE TABLE order_execution_attempts (
    attempt_id uuid PRIMARY KEY,
    order_id integer NOT NULL REFERENCES client_trades,
    attempt integer NOT NULL,
    outcome text NOT NULL,
    reason text NOT NULL,
    quote_id uuid REFERENCES order_quote_observations,
    recorded_at timestamptz NOT NULL
);

-- Commit event facts with the order; delivery status may change after commit.
CREATE TABLE outbox_events (
    event_id uuid PRIMARY KEY REFERENCES order_events,
    order_id integer NOT NULL REFERENCES client_trades,
    version bigint NOT NULL,
    event_type text NOT NULL,
    schema_version integer NOT NULL DEFAULT 1,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    delivered_at timestamptz,
    attempts integer NOT NULL DEFAULT 0
);

-- Persisted sessions support revocation independently of access-token expiry.
CREATE TABLE auth_sessions (
    session_id uuid PRIMARY KEY,
    user_id integer NOT NULL REFERENCES users,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL
);

-- Database guards defend invariants even when a caller bypasses the application.
CREATE FUNCTION reject_evidence_change ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Trading evidence is append-only';
END
$$;

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON order_fills
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON cash_journal
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON position_journal
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON order_events
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON order_quote_observations
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON order_rule_decisions
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON order_execution_attempts
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

CREATE FUNCTION guard_legacy_financial_write ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    cid integer;
BEGIN
    cid := CASE WHEN TG_OP = 'DELETE' THEN
        OLD.client_id
    ELSE
        NEW.client_id
    END;
    IF TG_TABLE_NAME = 'client_trades' AND TG_OP <> 'DELETE' AND NEW.record_kind = 'SELF_DIRECTED' THEN
        RETURN NEW;
    END IF;
    IF EXISTS (
        SELECT
            1
        FROM
            clients
        WHERE
            client_id = cid
            AND financial_model = 'SIMULATION') THEN
    RAISE EXCEPTION 'Legacy financial writes are disabled for simulation accounts';
END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER guard_legacy_trade
    BEFORE INSERT OR UPDATE OR DELETE ON client_trades
    FOR EACH ROW
    EXECUTE FUNCTION guard_legacy_financial_write ();

CREATE TRIGGER guard_legacy_holding
    BEFORE INSERT OR UPDATE OR DELETE ON client_holdings
    FOR EACH ROW
    EXECUTE FUNCTION guard_legacy_financial_write ();

CREATE FUNCTION guard_client_cash ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.financial_model = 'SIMULATION' AND (NEW.cash_balance IS DISTINCT FROM OLD.cash_balance OR
    NEW.financial_model <> OLD.financial_model) THEN
        RAISE EXCEPTION 'Use authoritative currency accounts';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER guard_client_cash
    BEFORE UPDATE ON clients
    FOR EACH ROW
    EXECUTE FUNCTION guard_client_cash ();

CREATE FUNCTION guard_order_intent ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.record_kind = 'SELF_DIRECTED' THEN
        RAISE EXCEPTION 'Order intent is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    IF OLD.record_kind = 'SELF_DIRECTED' AND ((to_jsonb (NEW) - ARRAY['status', 'updated_at',
    'version', 'rejection_stage', 'rejection_code', 'reason']) IS DISTINCT FROM (to_jsonb (OLD)
    - ARRAY['status', 'updated_at', 'version', 'rejection_stage', 'rejection_code',
    'reason']) OR OLD.status IN ('FILLED', 'REJECTED')) THEN
        RAISE EXCEPTION 'Order intent or terminal outcome is immutable';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER guard_order_intent
    BEFORE UPDATE OR DELETE ON client_trades
    FOR EACH ROW
    EXECUTE FUNCTION guard_order_intent ();

CREATE FUNCTION guard_instrument_currency ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.currency <> OLD.currency AND (EXISTS (
        SELECT
            1
        FROM
            client_positions
        WHERE
            instrument_id = OLD.instrument_id AND posted > 0) OR EXISTS (
        SELECT
            1
        FROM
            client_trades
        WHERE
        instrument_id = OLD.instrument_id AND record_kind = 'SELF_DIRECTED' AND status IN
        ('ACCEPTED', 'EXECUTING'))) THEN
        RAISE EXCEPTION 'Instrument currency cannot change with open positions or orders';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER guard_instrument_currency
    BEFORE UPDATE ON instruments
    FOR EACH ROW
    EXECUTE FUNCTION guard_instrument_currency ();

CREATE FUNCTION guard_reservation ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' OR OLD.state <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Reservation outcome is immutable';
    END IF;
    IF (to_jsonb (NEW) - ARRAY['state', 'ended_at']) IS DISTINCT FROM (to_jsonb (OLD) -
    ARRAY['state', 'ended_at']) THEN
        RAISE EXCEPTION 'Reservation amount is immutable';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER guard_reservation
    BEFORE UPDATE OR DELETE ON order_reservations
    FOR EACH ROW
    EXECUTE FUNCTION guard_reservation ();

CREATE FUNCTION guard_outbox_fact ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Committed outbox facts cannot be deleted';
    END IF;
    IF (to_jsonb (NEW) - ARRAY['delivered_at', 'attempts']) IS DISTINCT FROM (to_jsonb (OLD) -
    ARRAY['delivered_at', 'attempts']) THEN
        RAISE EXCEPTION 'Committed outbox facts are immutable';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER guard_outbox_fact
    BEFORE UPDATE OR DELETE ON outbox_events
    FOR EACH ROW
    EXECUTE FUNCTION guard_outbox_fact ();

-- Deferred until COMMIT: order, event and fill writes may occur in one transaction.
CREATE FUNCTION require_lifecycle_evidence ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.record_kind = 'SELF_DIRECTED' AND NOT EXISTS (
        SELECT
            1
        FROM
            order_events
        WHERE
            order_id = NEW.trade_id AND sequence =
                NEW.version AND to_state = NEW.status) THEN
        RAISE EXCEPTION 'Order state must have matching committed lifecycle evidence';
    END IF;
    IF NEW.record_kind = 'SELF_DIRECTED' AND NEW.status = 'FILLED' AND NOT EXISTS (
        SELECT
            1
        FROM
            order_fills
        WHERE
            order_id = NEW.trade_id) THEN
        RAISE EXCEPTION 'Filled order must have an immutable fill';
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER require_lifecycle_evidence
    AFTER INSERT OR UPDATE ON client_trades DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION require_lifecycle_evidence ();

INSERT INTO schema_migrations (version)
    VALUES (4);

COMMIT;
