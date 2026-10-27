BEGIN;

ALTER TABLE compliance_access_grants DROP CONSTRAINT compliance_access_grants_permission_check;
ALTER TABLE compliance_access_grants ADD CONSTRAINT compliance_access_grants_permission_check
    CHECK (permission IN ('EVIDENCE_READ','EXPORT_CREATE','EXPORT_DOWNLOAD',
        'INVESTIGATION_READ','INVESTIGATION_UPDATE'));
CREATE TABLE compliance_investigations (
    investigation_id uuid PRIMARY KEY,
    reference varchar(43) NOT NULL UNIQUE,
    case_id uuid NOT NULL,
    client_id integer NOT NULL,
    order_id integer,
    reviewer integer NOT NULL REFERENCES users(user_id),
    reason_code varchar(24) NOT NULL CHECK (reason_code IN ('POLICY','INTEGRITY','ELIGIBILITY','PROVENANCE')),
    summary varchar(2000) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED','IN_REVIEW','NEEDS_INFORMATION','DECIDED','CLOSED')),
    version bigint NOT NULL DEFAULT 1 CHECK (version>=1),
    disposition varchar(100),
    findings varchar(4000),
    client_safe_explanation varchar(4000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    FOREIGN KEY(case_id,client_id) REFERENCES support_cases(case_id,client_id),
    FOREIGN KEY(order_id,client_id) REFERENCES client_trades(trade_id,client_id),
    CHECK (status NOT IN ('DECIDED','CLOSED') OR (disposition IS NOT NULL AND findings IS NOT NULL))
);
CREATE INDEX compliance_case_queue ON compliance_investigations(client_id,status,updated_at,investigation_id);
CREATE TABLE support_case_referrals (
    referral_id uuid PRIMARY KEY,
    case_id uuid NOT NULL REFERENCES support_cases(case_id),
    investigation_id uuid NOT NULL UNIQUE REFERENCES compliance_investigations(investigation_id),
    mandatory boolean NOT NULL DEFAULT true CHECK (mandatory),
    actor integer NOT NULL REFERENCES users(user_id),
    recorded_at timestamptz NOT NULL
);
CREATE INDEX support_referrals_case ON support_case_referrals(case_id,recorded_at,referral_id);
CREATE TABLE compliance_investigation_events (
    event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    investigation_id uuid NOT NULL REFERENCES compliance_investigations(investigation_id),
    version bigint NOT NULL,
    actor integer NOT NULL REFERENCES users(user_id),
    kind varchar(40) NOT NULL,
    facts jsonb NOT NULL,
    recorded_at timestamptz NOT NULL,
    UNIQUE(investigation_id,version)
);
CREATE TABLE compliance_investigation_requests (
    investigation_id uuid NOT NULL REFERENCES compliance_investigations(investigation_id),
    actor integer NOT NULL REFERENCES users(user_id),
    request_key varchar(128) NOT NULL,
    request_hash varchar(64) NOT NULL,
    receipt jsonb NOT NULL,
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY(investigation_id,actor,request_key)
);
CREATE TABLE compliance_investigation_exports (
    investigation_id uuid NOT NULL REFERENCES compliance_investigations(investigation_id),
    export_id uuid NOT NULL REFERENCES compliance_evidence_exports(export_id),
    actor integer NOT NULL REFERENCES users(user_id),
    recorded_at timestamptz NOT NULL,
    PRIMARY KEY(investigation_id,export_id)
);
ALTER TABLE compliance_access_contexts
    ADD COLUMN reference_type varchar(16) NOT NULL DEFAULT 'EXTERNAL'
        CHECK (reference_type IN ('EXTERNAL','MANAGED')),
    ADD COLUMN investigation_id uuid REFERENCES compliance_investigations(investigation_id),
    ADD COLUMN managed_reviewer integer REFERENCES users(user_id),
    ADD COLUMN managed_version bigint,
    ADD CONSTRAINT compliance_managed_context
        CHECK ((reference_type='MANAGED')=(investigation_id IS NOT NULL)
            AND (reference_type='MANAGED')=(managed_reviewer IS NOT NULL)
            AND (reference_type='MANAGED')=(managed_version IS NOT NULL)
            AND (managed_version IS NULL OR managed_version>=1));
CREATE FUNCTION guard_compliance_investigation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Compliance investigation history cannot be deleted'; END IF;
    IF (to_jsonb(NEW)-ARRAY['reviewer','status','version','disposition','findings','client_safe_explanation','updated_at'])
       IS DISTINCT FROM
       (to_jsonb(OLD)-ARRAY['reviewer','status','version','disposition','findings','client_safe_explanation','updated_at'])
       OR NEW.version<>OLD.version+1 THEN
        RAISE EXCEPTION 'Compliance source and version are protected';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER compliance_investigation_guard BEFORE UPDATE OR DELETE ON compliance_investigations
    FOR EACH ROW EXECUTE FUNCTION guard_compliance_investigation();
CREATE FUNCTION check_compliance_investigation_projection() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE projection compliance_investigations%ROWTYPE;
BEGIN
    SELECT * INTO projection FROM compliance_investigations WHERE investigation_id=NEW.investigation_id;
    IF NOT FOUND OR (SELECT count(*) FROM compliance_investigation_events WHERE investigation_id=NEW.investigation_id)<>projection.version
       OR NOT EXISTS (SELECT 1 FROM compliance_investigation_events WHERE investigation_id=NEW.investigation_id
                      AND version=projection.version AND recorded_at=projection.updated_at) THEN
        RAISE EXCEPTION 'Compliance investigation requires matching retained history';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER compliance_review_projection AFTER INSERT OR UPDATE ON compliance_investigations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_compliance_investigation_projection();
CREATE CONSTRAINT TRIGGER compliance_review_history AFTER INSERT ON compliance_investigation_events
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_compliance_investigation_projection();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON support_case_referrals
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON compliance_investigation_events
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON compliance_investigation_requests
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON compliance_investigation_exports
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();

INSERT INTO schema_migrations(version) VALUES (17);
COMMIT;
