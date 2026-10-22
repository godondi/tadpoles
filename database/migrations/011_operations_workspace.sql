BEGIN;

-- Explicit client scopes only. Provision through an authorized database deployment, never staff UI.
CREATE TABLE operations_access_grants (
    grant_id uuid PRIMARY KEY,
    staff_user_id integer NOT NULL REFERENCES users(user_id),
    permission text NOT NULL CHECK (permission IN ('ORDER_READ', 'EVIDENCE_READ', 'RECONCILIATION_READ', 'EVIDENCE_EXPORT')),
    scope_type text NOT NULL DEFAULT 'CLIENT' CHECK (scope_type = 'CLIENT'),
    client_id integer NOT NULL REFERENCES clients(client_id),
    account_reference varchar(80) NOT NULL CHECK (account_reference ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$'),
    valid_from timestamptz NOT NULL,
    valid_until timestamptz NOT NULL CHECK (valid_until > valid_from),
    approved_by integer NOT NULL REFERENCES users(user_id),
    approval_reference varchar(200) NOT NULL CHECK (length(trim(approval_reference)) >= 8),
    revoked_at timestamptz,
    CHECK (approved_by <> staff_user_id)
);
CREATE INDEX operations_grants_lookup ON operations_access_grants (staff_user_id, permission, client_id, valid_until) WHERE revoked_at IS NULL;
CREATE TABLE operations_grant_events (
    event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grant_id uuid NOT NULL REFERENCES operations_access_grants(grant_id),
    database_actor text NOT NULL,
    action text NOT NULL,
    before_values jsonb,
    after_values jsonb NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION record_operations_grant_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (to_jsonb(NEW) - 'revoked_at') IS DISTINCT FROM (to_jsonb(OLD) - 'revoked_at') THEN
        RAISE EXCEPTION 'Revoke and issue a new grant to change scope';
    END IF;
    IF TG_OP = 'UPDATE' AND (OLD.revoked_at IS NOT NULL OR NEW.revoked_at IS NULL) THEN
        RAISE EXCEPTION 'Grant revocation cannot be reversed';
    END IF;
    INSERT INTO operations_grant_events(grant_id, database_actor, action, before_values, after_values)
    VALUES (NEW.grant_id, current_user, TG_OP, CASE WHEN TG_OP = 'UPDATE' THEN to_jsonb(OLD) END, to_jsonb(NEW));
    RETURN NEW;
END;
$$;
CREATE TRIGGER operations_grant_changes AFTER INSERT OR UPDATE ON operations_access_grants FOR EACH ROW EXECUTE FUNCTION record_operations_grant_change();
CREATE TRIGGER operations_grant_no_delete BEFORE DELETE ON operations_access_grants FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TABLE operations_access_contexts (
    context_id uuid PRIMARY KEY,
    actor integer NOT NULL REFERENCES users(user_id),
    purpose_code varchar(40) NOT NULL,
    case_reference varchar(200) NOT NULL CHECK (length(trim(case_reference)) BETWEEN 8 AND 200),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    policy_version varchar(80) NOT NULL
);
CREATE TABLE operations_access_events (
    event_id uuid PRIMARY KEY,
    actor integer NOT NULL REFERENCES users(user_id),
    context_id uuid,
    action varchar(80) NOT NULL,
    target_type varchar(40) NOT NULL,
    target_id varchar(80),
    filter_digest varchar(64) NOT NULL,
    scope_version varchar(64) NOT NULL,
    outcome varchar(40) NOT NULL,
    returned_count integer NOT NULL CHECK (returned_count >= 0),
    recorded_at timestamptz NOT NULL,
    correlation_id uuid NOT NULL,
    export_id uuid
);
CREATE INDEX operations_access_events_actor_time ON operations_access_events(actor, recorded_at);
CREATE TABLE operations_evidence_exports (
    export_id uuid PRIMARY KEY,
    requester integer NOT NULL REFERENCES users(user_id),
    context_id uuid NOT NULL REFERENCES operations_access_contexts(context_id),
    order_id integer NOT NULL REFERENCES client_trades(trade_id),
    idempotency_key varchar(80) NOT NULL,
    request_hash varchar(64) NOT NULL,
    scope_version varchar(64) NOT NULL,
    snapshot_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    order_version bigint NOT NULL,
    digest varchar(64) NOT NULL,
    archive bytea NOT NULL,
    UNIQUE(requester, idempotency_key)
);
CREATE TABLE order_decision_contexts (
    decision_id uuid PRIMARY KEY REFERENCES order_rule_decisions(decision_id),
    schema_version integer NOT NULL CHECK (schema_version = 1),
    facts jsonb NOT NULL,
    recorded_at timestamptz NOT NULL
);
CREATE INDEX operations_order_scope_time ON client_trades(client_id, submitted_at DESC, trade_id DESC) WHERE record_kind = 'SELF_DIRECTED';
CREATE INDEX operations_order_time ON client_trades(submitted_at DESC, trade_id DESC) WHERE record_kind = 'SELF_DIRECTED';
CREATE INDEX operations_order_status_time ON client_trades(status, submitted_at DESC, trade_id DESC) WHERE record_kind = 'SELF_DIRECTED';
CREATE TABLE reconciliation_findings (
    finding_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES reconciliation_runs(run_id),
    kind text NOT NULL,
    client_id integer REFERENCES clients(client_id),
    order_id integer REFERENCES client_trades(trade_id),
    reference text NOT NULL,
    detected_at timestamptz NOT NULL
);
CREATE INDEX operations_reconciliation_scope ON reconciliation_findings(run_id, client_id, finding_id);
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON operations_grant_events FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON operations_access_contexts FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON operations_access_events FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON operations_evidence_exports FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON order_decision_contexts FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON reconciliation_findings FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

-- Project retained legacy findings without changing the source reports. Unknown owners remain redacted.

        INSERT INTO reconciliation_findings(run_id,kind,client_id,order_id,reference,detected_at)
        SELECT r.run_id, finding->>'kind',
          CASE split_part(finding->>'reference',':',1)
            WHEN 'cash' THEN (SELECT client_id FROM clients WHERE client_id::text=split_part(finding->>'reference',':',2))
            WHEN 'position' THEN (SELECT client_id FROM clients WHERE client_id::text=split_part(finding->>'reference',':',2))
            WHEN 'order' THEN (SELECT client_id FROM client_trades WHERE trade_id::text=split_part(finding->>'reference',':',2))
            WHEN 'fill' THEN (SELECT t.client_id FROM order_fills f JOIN client_trades t ON t.trade_id=f.order_id WHERE f.fill_id::text=split_part(finding->>'reference',':',2))
            WHEN 'movement' THEN (SELECT client_id FROM cash_movements WHERE movement_id::text=split_part(finding->>'reference',':',2))
            WHEN 'event' THEN (SELECT client_id FROM order_events WHERE event_id::text=split_part(finding->>'reference',':',2))
          END,
          CASE split_part(finding->>'reference',':',1)
            WHEN 'order' THEN (SELECT trade_id FROM client_trades WHERE trade_id::text=split_part(finding->>'reference',':',2))
            WHEN 'fill' THEN (SELECT order_id FROM order_fills WHERE fill_id::text=split_part(finding->>'reference',':',2))
            WHEN 'event' THEN (SELECT order_id FROM order_events WHERE event_id::text=split_part(finding->>'reference',':',2))
          END,
          finding->>'reference',r.checked_at
        FROM reconciliation_runs r CROSS JOIN LATERAL jsonb_array_elements(r.report->'exceptions') finding
        
        ;

INSERT INTO schema_migrations(version) VALUES (11);
COMMIT;
