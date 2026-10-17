-- Requires migration 004 (cash journal and append-only/outbox guard functions).
-- Internal USD deposits/withdrawals and encrypted bank references; no real payments.

BEGIN;

CREATE TABLE bank_references (
    bank_id uuid PRIMARY KEY,
    client_id integer NOT NULL REFERENCES clients,
    nickname text NOT NULL CHECK (length(nickname) BETWEEN 2 AND 50),
    encrypted_details text NOT NULL,
    last_four text NOT NULL CHECK (last_four ~ '^[0-9]{4}$'),
    is_default boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (bank_id, client_id),
    CHECK (active OR NOT is_default)
);

CREATE UNIQUE INDEX bank_reference_nickname ON bank_references (client_id, lower(nickname))
WHERE
    active;

CREATE UNIQUE INDEX bank_reference_default ON bank_references (client_id)
WHERE
    active AND is_default;

-- Request keys prevent duplicate deposits/withdrawals; every movement has one owner.
CREATE TABLE cash_movements (
    movement_id uuid PRIMARY KEY,
    client_id integer NOT NULL REFERENCES clients,
    bank_id uuid NOT NULL,
    request_key text NOT NULL,
    request_hash text NOT NULL,
    direction text NOT NULL CHECK (direction IN ('DEPOSIT', 'WITHDRAW')),
    currency text NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    amount numeric NOT NULL CHECK (amount > 0 AND amount <= 10000 AND scale (amount) <= 2),
    status text NOT NULL CHECK (status IN ('SETTLED', 'REJECTED')),
    reason text,
    bank_snapshot text NOT NULL,
    posting_group uuid NOT NULL UNIQUE,
    actor text NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (client_id, request_key),
    FOREIGN KEY (bank_id, client_id) REFERENCES bank_references (bank_id, client_id)
);

CREATE INDEX cash_movement_history ON cash_movements (client_id, recorded_at DESC, movement_id);

CREATE TABLE cash_movement_outbox (
    event_id uuid PRIMARY KEY,
    movement_id uuid NOT NULL UNIQUE REFERENCES cash_movements,
    event_type text NOT NULL,
    schema_version integer NOT NULL DEFAULT 1,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    delivered_at timestamptz,
    attempts integer NOT NULL DEFAULT 0
);

-- Each journal posting must identify exactly one source: fill, setup, or cash movement.
ALTER TABLE cash_journal
    ADD COLUMN movement_id uuid UNIQUE REFERENCES cash_movements;

ALTER TABLE cash_journal
    DROP CONSTRAINT cash_journal_check;

ALTER TABLE cash_journal
    ADD CONSTRAINT cash_journal_source CHECK (num_nonnulls (fill_id, setup_reference, movement_id) = 1);

CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON cash_movements
    FOR EACH ROW
    EXECUTE FUNCTION reject_evidence_change ();

CREATE TRIGGER immutable_movement_outbox
    BEFORE UPDATE OR DELETE ON cash_movement_outbox
    FOR EACH ROW
    EXECUTE FUNCTION guard_outbox_fact ();

INSERT INTO schema_migrations (version)
    VALUES (5);

COMMIT;
