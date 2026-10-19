-- Requires migration 007. Adds provider setup/reconciliation evidence and identity guards.
-- A SYSTEM policy audit may lack a user actor; user-initiated audits retain their identity.

BEGIN;

ALTER TABLE instrument_policy_audit
    ALTER COLUMN actor DROP NOT NULL;

ALTER TABLE instrument_policy_audit
    ADD COLUMN actor_type text NOT NULL DEFAULT 'USER' CHECK (actor_type IN ('USER', 'SYSTEM'));

CREATE TABLE provider_setup_checks (
    check_id uuid PRIMARY KEY,
    symbol text NOT NULL,
    checked_at timestamptz NOT NULL,
    result text NOT NULL,
    detail text NOT NULL
);

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON provider_setup_checks
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

-- Persist Finance reconciliation results instead of treating a transient screen as evidence.
CREATE TABLE reconciliation_runs (
    run_id uuid PRIMARY KEY,
    actor integer NOT NULL REFERENCES users,
    checked_at timestamptz NOT NULL,
    matched boolean NOT NULL,
    report jsonb NOT NULL
);

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON reconciliation_runs
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

-- Ticker/currency/provider mapping are economic identity: create a replacement to change them.
CREATE OR REPLACE FUNCTION guard_instrument_currency ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF (NEW.currency,
        NEW.ticker) IS DISTINCT FROM (OLD.currency,
    OLD.ticker) THEN
        RAISE EXCEPTION 'Economic instrument identity is immutable; create a replacement';
    END IF;
    RETURN NEW;
END
$$;

CREATE FUNCTION guard_provider_identity ()
    RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.provider_symbol IS NOT NULL AND NEW.provider_symbol IS DISTINCT FROM OLD.provider_symbol THEN
        RAISE EXCEPTION 'Provider identity is immutable; create a replacement instrument';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER guard_provider_identity
    BEFORE UPDATE ON instrument_trading_policies
    FOR EACH ROW
    EXECUTE FUNCTION guard_provider_identity ();

INSERT INTO schema_migrations (version)
    VALUES (8);

COMMIT;
