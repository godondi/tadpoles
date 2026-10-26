BEGIN;

-- Advisory projections never write financial state. NONE retains the guidance identity/version.
CREATE TABLE advisor_guidance (
    guidance_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id integer NOT NULL REFERENCES clients(client_id),
    instrument_id integer NOT NULL REFERENCES instruments(instrument_id),
    status text NOT NULL CHECK (status IN ('NONE','HOLD','SELL')),
    reasoning text CHECK (reasoning IS NULL OR (length(reasoning) BETWEEN 1 AND 2000 AND reasoning !~ '^[[:space:]]|[[:space:]]$')),
    author_advisor_id integer NOT NULL REFERENCES advisors(advisor_id),
    author_user_id integer NOT NULL REFERENCES users(user_id),
    version bigint NOT NULL DEFAULT 1 CHECK (version BETWEEN 1 AND 9007199254740991),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (client_id,instrument_id),
    CHECK (status <> 'NONE' OR reasoning IS NULL)
);
CREATE TABLE advisor_recommendations (
    recommendation_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id integer NOT NULL REFERENCES clients(client_id),
    instrument_id integer NOT NULL REFERENCES instruments(instrument_id),
    reasoning text NOT NULL CHECK (length(reasoning) BETWEEN 1 AND 2000 AND reasoning !~ '^[[:space:]]|[[:space:]]$'),
    author_advisor_id integer NOT NULL REFERENCES advisors(advisor_id),
    author_user_id integer NOT NULL REFERENCES users(user_id),
    version bigint NOT NULL DEFAULT 1 CHECK (version BETWEEN 1 AND 9007199254740991),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    archived_at timestamptz
);
CREATE UNIQUE INDEX advisor_recommendations_active ON advisor_recommendations(client_id,instrument_id)
    WHERE archived_at IS NULL;

CREATE TABLE advisor_guidance_history (
    history_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    guidance_id integer NOT NULL REFERENCES advisor_guidance(guidance_id),
    client_id integer NOT NULL REFERENCES clients(client_id),
    instrument_id integer NOT NULL REFERENCES instruments(instrument_id),
    status text NOT NULL CHECK (status IN ('NONE','HOLD','SELL')),
    reasoning text CHECK (reasoning IS NULL OR (length(reasoning) BETWEEN 1 AND 2000 AND reasoning !~ '^[[:space:]]|[[:space:]]$')),
    author_advisor_id integer NOT NULL REFERENCES advisors(advisor_id),
    author_user_id integer NOT NULL REFERENCES users(user_id),
    version bigint NOT NULL CHECK (version BETWEEN 1 AND 9007199254740991),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    action text NOT NULL CHECK (action IN ('INSERT','UPDATE')),
    UNIQUE (guidance_id,version),
    CHECK (status <> 'NONE' OR reasoning IS NULL)
);
CREATE TABLE advisor_recommendation_history (
    history_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recommendation_id integer NOT NULL REFERENCES advisor_recommendations(recommendation_id),
    client_id integer NOT NULL REFERENCES clients(client_id),
    instrument_id integer NOT NULL REFERENCES instruments(instrument_id),
    reasoning text NOT NULL CHECK (length(reasoning) BETWEEN 1 AND 2000 AND reasoning !~ '^[[:space:]]|[[:space:]]$'),
    author_advisor_id integer NOT NULL REFERENCES advisors(advisor_id),
    author_user_id integer NOT NULL REFERENCES users(user_id),
    version bigint NOT NULL CHECK (version BETWEEN 1 AND 9007199254740991),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    archived_at timestamptz,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    action text NOT NULL CHECK (action IN ('INSERT','UPDATE','ARCHIVE')),
    UNIQUE (recommendation_id,version)
);

CREATE FUNCTION guard_advisor_projection() RETURNS trigger LANGUAGE plpgsql
    SET search_path = pg_catalog, public AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Advisory projections cannot be deleted' USING ERRCODE = '23514';
    END IF;
    -- Same lock order as trading: client first, before author/assignment and eligibility checks.
    PERFORM 1 FROM public.clients WHERE client_id=NEW.client_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Advisory client is unavailable' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.clients c
        JOIN public.advisors a ON a.advisor_id=c.advisor_id
        JOIN public.users u ON u.user_id=a.user_id
        WHERE c.client_id=NEW.client_id AND a.advisor_id=NEW.author_advisor_id
          AND u.user_id=NEW.author_user_id AND u.enabled
          AND EXISTS (SELECT 1 FROM public.user_roles ur JOIN public.roles r USING(role_id)
                      WHERE ur.user_id=u.user_id AND r.role_name='ADVISOR')
    ) THEN
        RAISE EXCEPTION 'Author must be the assigned enabled advisor with linked user and role' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.version IS DISTINCT FROM 1::bigint THEN
            RAISE EXCEPTION 'Initial advisory version must be one' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF NEW.client_id IS DISTINCT FROM OLD.client_id
           OR NEW.instrument_id IS DISTINCT FROM OLD.instrument_id
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'Advisory ownership and creation time are immutable' USING ERRCODE = '23514';
        END IF;
        IF NEW.version IS DISTINCT FROM OLD.version + 1 THEN
            RAISE EXCEPTION 'Advisory version must advance once' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_TABLE_NAME = 'advisor_guidance' THEN
        IF TG_OP = 'UPDATE' AND NEW.guidance_id IS DISTINCT FROM OLD.guidance_id THEN
            RAISE EXCEPTION 'Guidance identity is immutable' USING ERRCODE = '23514';
        END IF;
    ELSE
        IF TG_OP = 'INSERT' THEN
            IF NEW.archived_at IS NOT NULL THEN
                RAISE EXCEPTION 'A recommendation must start active' USING ERRCODE = '23514';
            END IF;
        ELSE
            IF NEW.recommendation_id IS DISTINCT FROM OLD.recommendation_id OR OLD.archived_at IS NOT NULL THEN
                RAISE EXCEPTION 'Recommendation identity and archived records are immutable' USING ERRCODE = '23514';
            END IF;
            IF NEW.archived_at IS NOT NULL AND NEW.reasoning IS DISTINCT FROM OLD.reasoning THEN
                RAISE EXCEPTION 'Archiving cannot also change reasoning' USING ERRCODE = '23514';
            END IF;
        END IF;
        -- Archiving remains available after acquisition or catalog deactivation.
        IF NEW.archived_at IS NULL THEN
            IF EXISTS (SELECT 1 FROM public.client_positions p
                       WHERE p.client_id=NEW.client_id AND p.instrument_id=NEW.instrument_id AND p.posted>0)
               OR NOT EXISTS (SELECT 1 FROM public.instruments i WHERE i.instrument_id=NEW.instrument_id
                   AND i.is_active AND i.currency='USD' AND i.ticker NOT LIKE 'SIM-%'
                   AND i.ticker NOT IN ('CASH','USD','GBP','INR','EUR')) THEN
                RAISE EXCEPTION 'Recommendation instrument is held or outside the approved catalog' USING ERRCODE = '23514';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER advisor_guidance_guard BEFORE INSERT OR UPDATE OR DELETE ON advisor_guidance
    FOR EACH ROW EXECUTE FUNCTION guard_advisor_projection();
CREATE TRIGGER advisor_recommendations_guard BEFORE INSERT OR UPDATE OR DELETE ON advisor_recommendations
    FOR EACH ROW EXECUTE FUNCTION guard_advisor_projection();

-- Only this owner-executed trigger appends snapshots; runtime has no history INSERT or helper EXECUTE.
-- Static, schema-qualified inserts and a fixed search_path prevent caller-controlled object resolution.
CREATE FUNCTION append_advisor_history() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
    SET search_path = pg_catalog, public AS $$
BEGIN
    IF TG_RELID = 'public.advisor_guidance'::regclass THEN
        INSERT INTO public.advisor_guidance_history
            (guidance_id,client_id,instrument_id,status,reasoning,author_advisor_id,author_user_id,version,created_at,updated_at,action)
        VALUES (NEW.guidance_id,NEW.client_id,NEW.instrument_id,NEW.status,NEW.reasoning,
                NEW.author_advisor_id,NEW.author_user_id,NEW.version,NEW.created_at,NEW.updated_at,TG_OP);
    ELSIF TG_RELID = 'public.advisor_recommendations'::regclass THEN
        INSERT INTO public.advisor_recommendation_history
            (recommendation_id,client_id,instrument_id,reasoning,author_advisor_id,author_user_id,version,created_at,updated_at,archived_at,action)
        VALUES (NEW.recommendation_id,NEW.client_id,NEW.instrument_id,NEW.reasoning,
                NEW.author_advisor_id,NEW.author_user_id,NEW.version,NEW.created_at,NEW.updated_at,NEW.archived_at,
                CASE WHEN NEW.archived_at IS NOT NULL THEN 'ARCHIVE' ELSE TG_OP END);
    ELSE
        RAISE EXCEPTION 'Unsupported advisory projection' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER advisor_guidance_snapshot AFTER INSERT OR UPDATE ON advisor_guidance
    FOR EACH ROW EXECUTE FUNCTION append_advisor_history();
CREATE TRIGGER advisor_recommendations_snapshot AFTER INSERT OR UPDATE ON advisor_recommendations
    FOR EACH ROW EXECUTE FUNCTION append_advisor_history();

CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON advisor_guidance_history
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence BEFORE UPDATE OR DELETE ON advisor_recommendation_history
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence_truncate BEFORE TRUNCATE ON advisor_guidance_history
    FOR EACH STATEMENT EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_evidence_truncate BEFORE TRUNCATE ON advisor_recommendation_history
    FOR EACH STATEMENT EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_projection_truncate BEFORE TRUNCATE ON advisor_guidance
    FOR EACH STATEMENT EXECUTE FUNCTION reject_evidence_change();
CREATE TRIGGER immutable_projection_truncate BEFORE TRUNCATE ON advisor_recommendations
    FOR EACH STATEMENT EXECUTE FUNCTION reject_evidence_change();

REVOKE ALL ON FUNCTION append_advisor_history() FROM PUBLIC;
REVOKE ALL ON FUNCTION guard_advisor_projection() FROM PUBLIC;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON advisor_guidance_history, advisor_recommendation_history FROM PUBLIC;

INSERT INTO schema_migrations(version) VALUES(18);
COMMIT;