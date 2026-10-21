BEGIN;

ALTER TABLE client_trades ADD CONSTRAINT support_order_owner UNIQUE (trade_id, client_id);
CREATE TABLE support_cases (
    case_id uuid PRIMARY KEY,
    reference varchar(41) NOT NULL UNIQUE CHECK (reference ~ '^CASE-[0-9a-f-]{36}$'),
    client_id integer NOT NULL REFERENCES clients(client_id),
    order_id integer,
    category varchar(40) NOT NULL CHECK (category IN ('ORDER_QUESTION','ACCOUNT_REVIEW','HISTORICAL_RECORD','TRADING_AVAILABILITY','OTHER')),
    subject varchar(120) NOT NULL CHECK (length(trim(subject)) BETWEEN 5 AND 120),
    request_key varchar(128) NOT NULL,
    request_hash varchar(64) NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN ('OPEN','IN_REVIEW','AWAITING_CLIENT','RESOLVED')),
    version bigint NOT NULL CHECK (version >= 1),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (client_id,request_key),
    UNIQUE (case_id,client_id),
    FOREIGN KEY (order_id,client_id) REFERENCES client_trades(trade_id,client_id)
);
CREATE INDEX support_owner_time ON support_cases(client_id,created_at DESC,case_id DESC);
CREATE INDEX support_owner_status ON support_cases(client_id,status,created_at DESC,case_id DESC);
CREATE TABLE support_case_events (
    event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id uuid NOT NULL,
    client_id integer NOT NULL,
    version bigint NOT NULL,
    kind varchar(24) NOT NULL,
    actor integer NOT NULL REFERENCES users(user_id),
    author_role varchar(16) NOT NULL CHECK (author_role IN ('CLIENT','OPERATIONS')),
    body varchar(4000) NOT NULL CHECK (length(trim(body)) BETWEEN 10 AND 4000),
    previous_status varchar(24),
    status varchar(24) NOT NULL,
    recorded_at timestamptz NOT NULL,
    UNIQUE(case_id,version),
    FOREIGN KEY(case_id,client_id) REFERENCES support_cases(case_id,client_id)
);
CREATE INDEX support_public_updates ON support_case_events(client_id,event_id);
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_events FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE FUNCTION guard_support_case() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Support history cannot be deleted'; END IF;
    IF (to_jsonb(NEW) - ARRAY['status','version','updated_at']) IS DISTINCT FROM
       (to_jsonb(OLD) - ARRAY['status','version','updated_at']) THEN
        RAISE EXCEPTION 'Case identity and request are immutable';
    END IF;
    IF NEW.version <> OLD.version + 1 THEN RAISE EXCEPTION 'Case version must advance once'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER support_case_guard BEFORE UPDATE OR DELETE ON support_cases FOR EACH ROW EXECUTE FUNCTION guard_support_case();
ALTER TABLE operations_access_grants DROP CONSTRAINT operations_access_grants_permission_check;
ALTER TABLE operations_access_grants ADD CONSTRAINT operations_access_grants_permission_check
    CHECK (permission IN ('ORDER_READ','EVIDENCE_READ','RECONCILIATION_READ','EVIDENCE_EXPORT','CASE_READ','CASE_UPDATE'));
CREATE TABLE support_case_notes (
    note_id uuid PRIMARY KEY,
    case_id uuid NOT NULL REFERENCES support_cases(case_id),
    actor integer NOT NULL REFERENCES users(user_id),
    body varchar(4000) NOT NULL CHECK (length(trim(body)) BETWEEN 10 AND 4000),
    recorded_at timestamptz NOT NULL
);
CREATE INDEX support_notes_page ON support_case_notes(case_id,recorded_at,note_id);
CREATE TABLE support_case_requests (
    case_id uuid NOT NULL REFERENCES support_cases(case_id),
    actor integer NOT NULL REFERENCES users(user_id),
    request_key varchar(128) NOT NULL,
    request_hash varchar(64) NOT NULL,
    receipt jsonb NOT NULL,
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY(case_id,actor,request_key)
);
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_notes FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_requests FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
ALTER TABLE support_cases ADD CONSTRAINT support_case_reference_identity UNIQUE(case_id,reference);
ALTER TABLE operations_access_contexts ADD COLUMN reference_type varchar(16) NOT NULL DEFAULT 'EXTERNAL'
    CHECK (reference_type IN ('EXTERNAL','MANAGED'));
ALTER TABLE operations_access_contexts ADD COLUMN managed_case_id uuid;
ALTER TABLE operations_access_contexts ADD CONSTRAINT managed_context_reference
    FOREIGN KEY(managed_case_id,case_reference) REFERENCES support_cases(case_id,reference);
ALTER TABLE operations_access_contexts ADD CONSTRAINT managed_context_kind
    CHECK ((reference_type='MANAGED') = (managed_case_id IS NOT NULL));
CREATE TABLE support_read_state (
    client_id integer NOT NULL REFERENCES clients(client_id),
    recipient integer NOT NULL REFERENCES users(user_id),
    last_event bigint NOT NULL CHECK(last_event >= 0),
    PRIMARY KEY(client_id,recipient)
);
CREATE TABLE support_case_exports (
    case_id uuid NOT NULL REFERENCES support_cases(case_id),
    export_id uuid NOT NULL REFERENCES operations_evidence_exports(export_id),
    digest varchar(64) NOT NULL,
    actor integer NOT NULL REFERENCES users(user_id),
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY(case_id,export_id)
);
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_exports FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE FUNCTION check_support_projection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE aggregate support_cases%ROWTYPE;
BEGIN
    SELECT * INTO aggregate FROM support_cases WHERE case_id=NEW.case_id;
    IF NOT FOUND OR (SELECT count(*) FROM support_case_events WHERE case_id=NEW.case_id) <> aggregate.version
       OR NOT EXISTS(SELECT 1 FROM support_case_events WHERE case_id=NEW.case_id
            AND version=aggregate.version AND status=aggregate.status AND recorded_at=aggregate.updated_at) THEN
        RAISE EXCEPTION 'Case projection requires matching retained history';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER support_projection_consistent AFTER INSERT OR UPDATE ON support_cases
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_support_projection();
CREATE CONSTRAINT TRIGGER support_history_consistent AFTER INSERT ON support_case_events
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_support_projection();
CREATE FUNCTION guard_support_read_state() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM clients WHERE client_id=NEW.client_id AND user_id=NEW.recipient)
       OR (NEW.last_event<>0 AND NOT EXISTS(SELECT 1 FROM support_case_events WHERE event_id=NEW.last_event AND client_id=NEW.client_id)) THEN
        RAISE EXCEPTION 'Read watermark must belong to the recipient';
    END IF;
    IF TG_OP='UPDATE' AND (NEW.client_id<>OLD.client_id OR NEW.recipient<>OLD.recipient OR NEW.last_event<OLD.last_event) THEN
        RAISE EXCEPTION 'Read watermark cannot change recipient or move backwards';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER support_read_state_guard BEFORE INSERT OR UPDATE ON support_read_state
    FOR EACH ROW EXECUTE FUNCTION guard_support_read_state();
INSERT INTO schema_migrations(version) VALUES(12);
COMMIT;
