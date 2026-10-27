BEGIN;

ALTER TABLE operations_access_grants DROP CONSTRAINT operations_access_grants_permission_check;
ALTER TABLE operations_access_grants ADD CONSTRAINT operations_access_grants_permission_check
    CHECK (permission IN ('ORDER_READ','EVIDENCE_READ','RECONCILIATION_READ','EVIDENCE_EXPORT',
        'CASE_READ','CASE_UPDATE','ACCOUNT_DIAGNOSTICS'));

CREATE TABLE support_case_intake (
    case_id uuid PRIMARY KEY REFERENCES support_cases(case_id),
    schema_version integer NOT NULL CHECK (schema_version=1),
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload)='object' AND octet_length(payload::text)<=12000),
    recorded_at timestamptz NOT NULL
);
CREATE TABLE support_case_workflow (
    case_id uuid PRIMARY KEY REFERENCES support_cases(case_id),
    effective_category varchar(40) NOT NULL CHECK (effective_category IN
        ('ORDER_QUESTION','ACCOUNT_REVIEW','HISTORICAL_RECORD','TRADING_AVAILABILITY','OTHER')),
    assignee integer REFERENCES users(user_id),
    priority varchar(16) NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW','NORMAL','HIGH')),
    next_action varchar(500),
    due_at timestamptz,
    version bigint NOT NULL DEFAULT 1 CHECK (version>=1),
    checklist_revision bigint NOT NULL DEFAULT 1 CHECK (checklist_revision>=1),
    updated_at timestamptz NOT NULL
);
CREATE INDEX support_workflow_queue ON support_case_workflow(priority,due_at,assignee,case_id);
CREATE TABLE support_case_workflow_events (
    event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id uuid NOT NULL REFERENCES support_case_workflow(case_id),
    version bigint NOT NULL,
    actor integer REFERENCES users(user_id),
    kind varchar(40) NOT NULL,
    facts jsonb NOT NULL,
    recorded_at timestamptz NOT NULL,
    UNIQUE(case_id,version)
);
CREATE TABLE support_case_workflow_requests (
    case_id uuid NOT NULL REFERENCES support_case_workflow(case_id),
    actor integer NOT NULL REFERENCES users(user_id),
    request_key varchar(128) NOT NULL,
    request_hash varchar(64) NOT NULL,
    receipt jsonb NOT NULL,
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY(case_id,actor,request_key)
);
CREATE TABLE support_case_tasks (
    case_id uuid NOT NULL REFERENCES support_case_workflow(case_id),
    checklist_revision bigint NOT NULL CHECK (checklist_revision>=1),
    code varchar(60) NOT NULL,
    label varchar(300) NOT NULL,
    state varchar(24) NOT NULL CHECK (state IN ('PENDING','COMPLETED','UNAVAILABLE','NOT_APPLICABLE')),
    reason varchar(2000) NOT NULL CHECK (length(trim(reason)) BETWEEN 10 AND 2000),
    evidence_kind varchar(40),
    evidence_reference varchar(200),
    actor integer NOT NULL REFERENCES users(user_id),
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY(case_id,checklist_revision,code)
);
CREATE TABLE support_case_resolutions (
    resolution_id uuid PRIMARY KEY,
    case_id uuid NOT NULL REFERENCES support_case_workflow(case_id),
    public_version bigint NOT NULL,
    workflow_version bigint NOT NULL,
    category varchar(40) NOT NULL,
    code varchar(60) NOT NULL,
    checklist jsonb NOT NULL,
    actor integer NOT NULL REFERENCES users(user_id),
    recorded_at timestamptz NOT NULL,
    UNIQUE(case_id,public_version),
    FOREIGN KEY(case_id,public_version) REFERENCES support_case_events(case_id,version)
);
CREATE FUNCTION guard_support_workflow() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Private workflow cannot be deleted'; END IF;
    IF NEW.case_id<>OLD.case_id OR NEW.version<>OLD.version+1
       OR NEW.checklist_revision<OLD.checklist_revision THEN
        RAISE EXCEPTION 'Private workflow identity/version is protected';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER support_workflow_guard BEFORE UPDATE OR DELETE ON support_case_workflow
    FOR EACH ROW EXECUTE FUNCTION guard_support_workflow();
CREATE FUNCTION check_support_workflow_projection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE projection support_case_workflow%ROWTYPE;
BEGIN
    SELECT * INTO projection FROM support_case_workflow WHERE case_id=NEW.case_id;
    IF NOT FOUND OR (SELECT count(*) FROM support_case_workflow_events WHERE case_id=NEW.case_id)<>projection.version
       OR NOT EXISTS (SELECT 1 FROM support_case_workflow_events WHERE case_id=NEW.case_id
                      AND version=projection.version AND recorded_at=projection.updated_at) THEN
        RAISE EXCEPTION 'Private workflow requires matching retained history';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER support_workflow_projection AFTER INSERT OR UPDATE ON support_case_workflow
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_support_workflow_projection();
CREATE CONSTRAINT TRIGGER support_workflow_history AFTER INSERT ON support_case_workflow_events
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_support_workflow_projection();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_intake
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_workflow_events
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_workflow_requests
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_resolutions
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

INSERT INTO support_case_workflow(case_id,effective_category,updated_at)
SELECT case_id,category,now() FROM support_cases;
INSERT INTO support_case_workflow_events(case_id,version,kind,facts,recorded_at)
SELECT case_id,version,'INITIALIZED','{"source":"MIGRATED","templateVersion":1}'::jsonb,updated_at
FROM support_case_workflow;

INSERT INTO schema_migrations(version) VALUES (16);
COMMIT;
