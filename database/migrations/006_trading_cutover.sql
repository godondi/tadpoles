-- Requires migration 005. Adds provider-policy metadata and quarantine evidence.
-- Stop admitting orders and drain jobs/reservations with the old app before upgrade.
-- The opening check deliberately aborts rather than abandoning accepted commitments.

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT
            1
        FROM
            order_execution_jobs
        WHERE
            NOT completed)
        OR EXISTS (
            SELECT
                1
            FROM
                order_reservations
            WHERE
                state = 'ACTIVE') THEN
        RAISE EXCEPTION 'Cutover blocked: halt admission and drain existing commitments with the previous application before upgrading';
END IF;
END
$$;

CREATE TABLE financial_migration_exceptions (
    exception_id bigserial PRIMARY KEY,
    source_reference text NOT NULL UNIQUE,
    kind text NOT NULL,
    client_id integer REFERENCES clients,
    instrument_id integer REFERENCES instruments,
    facts jsonb NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON financial_migration_exceptions
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

ALTER TABLE client_cash_accounts
    ADD COLUMN available_for_trading boolean NOT NULL DEFAULT true;

ALTER TABLE client_positions
    ADD COLUMN available_for_trading boolean NOT NULL DEFAULT true;

ALTER TABLE instrument_trading_policies
    ADD COLUMN provider_symbol text;

ALTER TABLE instrument_trading_policies
    ADD COLUMN buy_allowed boolean NOT NULL DEFAULT true;

ALTER TABLE instrument_trading_policies
    ADD COLUMN sell_allowed boolean NOT NULL DEFAULT true;

ALTER TABLE instrument_trading_policies
    ADD COLUMN validated_at timestamptz;

ALTER TABLE instrument_trading_policies
    ADD COLUMN revision bigint NOT NULL DEFAULT 1;

ALTER TABLE instrument_trading_policies
    DROP CONSTRAINT instrument_trading_policies_session_check;

-- Historical policy snapshots retain original semantics; active policies are converted in 007.
ALTER TABLE instrument_trading_policies
    ADD CONSTRAINT provider_session CHECK (session IN ('SIMULATION_24_7', 'PROVIDER_OPEN', 'CLOSED'));

CREATE TABLE instrument_policy_audit (
    audit_id uuid PRIMARY KEY,
    instrument_id integer NOT NULL REFERENCES instruments,
    actor integer NOT NULL REFERENCES users,
    reason text NOT NULL,
    before_values jsonb,
    after_values jsonb NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON instrument_policy_audit
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

INSERT INTO schema_migrations (version)
    VALUES (6);

COMMIT;
