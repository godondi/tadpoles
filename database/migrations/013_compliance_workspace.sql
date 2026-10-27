BEGIN;

CREATE TABLE compliance_access_grants (
    grant_id uuid PRIMARY KEY,
    staff_user_id integer NOT NULL REFERENCES users(user_id),
    CHECK (staff_user_id > 0),
    permission text NOT NULL CHECK (permission IN ('EVIDENCE_READ','EXPORT_CREATE','EXPORT_DOWNLOAD')),
    scope_type text NOT NULL DEFAULT 'CLIENT' CHECK (scope_type = 'CLIENT'),
    client_id integer NOT NULL REFERENCES clients(client_id) CHECK (client_id > 0),
    account_reference varchar(80) NOT NULL
        CHECK (account_reference ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$'),
    valid_from timestamptz NOT NULL,
    valid_until timestamptz NOT NULL CHECK (valid_until > valid_from),
    approved_by integer NOT NULL REFERENCES users(user_id),
    approval_reference varchar(200) NOT NULL CHECK (length(trim(approval_reference)) >= 8),
    revoked_at timestamptz,
    CHECK (approved_by <> staff_user_id)
);
CREATE INDEX compliance_grants_lookup
    ON compliance_access_grants(staff_user_id,permission,client_id,valid_until)
    WHERE revoked_at IS NULL;

CREATE TABLE compliance_grant_events (
    event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grant_id uuid NOT NULL REFERENCES compliance_access_grants(grant_id),
    database_actor text NOT NULL,
    action text NOT NULL,
    before_values jsonb,
    after_values jsonb NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION record_compliance_grant_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND (to_jsonb(NEW) - 'revoked_at') IS DISTINCT FROM (to_jsonb(OLD) - 'revoked_at') THEN
        RAISE EXCEPTION 'Revoke and issue a new Compliance grant to change scope';
    END IF;
    IF TG_OP = 'UPDATE' AND (OLD.revoked_at IS NOT NULL OR NEW.revoked_at IS NULL) THEN
        RAISE EXCEPTION 'Compliance grant revocation cannot be reversed';
    END IF;
    INSERT INTO compliance_grant_events(
        grant_id,database_actor,action,before_values,after_values)
    VALUES(
        NEW.grant_id,current_user,TG_OP,
        CASE WHEN TG_OP='UPDATE' THEN to_jsonb(OLD) END,to_jsonb(NEW));
    RETURN NEW;
END;
$$;
CREATE TRIGGER compliance_grant_changes
    AFTER INSERT OR UPDATE ON compliance_access_grants
    FOR EACH ROW EXECUTE FUNCTION record_compliance_grant_change();
CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON compliance_grant_events
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence
    BEFORE DELETE ON compliance_access_grants
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

CREATE TABLE compliance_access_contexts (
    context_id uuid PRIMARY KEY,
    actor integer NOT NULL REFERENCES users(user_id) CHECK (actor > 0),
    purpose_code varchar(40) NOT NULL CHECK (purpose_code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    case_reference varchar(200) NOT NULL
        CHECK (case_reference ~ '^[A-Za-z0-9][A-Za-z0-9._:/-]{7,199}$'),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    policy_version varchar(80) NOT NULL,
    scope_version varchar(64) NOT NULL CHECK (scope_version ~ '^[0-9a-f]{64}$'),
    revoked_at timestamptz
);
CREATE INDEX compliance_context_actor_time
    ON compliance_access_contexts(actor,expires_at,context_id);

CREATE FUNCTION guard_compliance_context() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Compliance contexts cannot be deleted';
    END IF;
    IF (to_jsonb(NEW) - 'revoked_at') IS DISTINCT FROM
       (to_jsonb(OLD) - 'revoked_at') THEN
        RAISE EXCEPTION 'Compliance context identity is immutable';
    END IF;
    IF OLD.revoked_at IS NOT NULL OR NEW.revoked_at IS NULL THEN
        RAISE EXCEPTION 'Compliance context revocation cannot be reversed';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER compliance_context_guard
    BEFORE UPDATE OR DELETE ON compliance_access_contexts
    FOR EACH ROW EXECUTE FUNCTION guard_compliance_context();

CREATE TABLE compliance_access_events (
    event_id uuid PRIMARY KEY,
    actor integer NOT NULL REFERENCES users(user_id),
    context_id uuid REFERENCES compliance_access_contexts(context_id),
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
CREATE INDEX compliance_access_events_actor_time
    ON compliance_access_events(actor,recorded_at);
CREATE INDEX compliance_access_events_context_time
    ON compliance_access_events(context_id,recorded_at);
CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON compliance_access_events
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

CREATE TABLE compliance_evidence_exports (
    export_id uuid PRIMARY KEY,
    requester integer NOT NULL REFERENCES users(user_id) CHECK (requester > 0),
    context_id uuid NOT NULL REFERENCES compliance_access_contexts(context_id),
    order_id integer NOT NULL REFERENCES client_trades(trade_id) CHECK (order_id > 0),
    idempotency_key varchar(80) NOT NULL
        CHECK (idempotency_key ~ '^[A-Za-z0-9._-]{8,80}$'),
    request_hash varchar(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    scope_version varchar(64) NOT NULL,
    snapshot_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    state varchar(16) NOT NULL CHECK (state IN ('REQUESTED','READY','FAILED','EXPIRED','DENIED')),
    digest varchar(64) NOT NULL CHECK (digest ~ '^[0-9a-f]{64}$'),
    manifest jsonb NOT NULL,
    archive bytea NOT NULL,
    UNIQUE(requester,idempotency_key)
);
CREATE INDEX compliance_exports_requester_time
    ON compliance_evidence_exports(requester,created_at DESC,export_id DESC);
CREATE INDEX compliance_exports_order_time
    ON compliance_evidence_exports(order_id,created_at DESC,export_id DESC);
CREATE TRIGGER immutable_evidence
    BEFORE UPDATE OR DELETE ON compliance_evidence_exports
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

INSERT INTO schema_migrations(version) VALUES (13);

COMMIT;
