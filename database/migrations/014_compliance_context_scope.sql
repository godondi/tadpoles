BEGIN;

ALTER TABLE compliance_access_contexts
    ADD COLUMN IF NOT EXISTS scope_version varchar(64);
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='public' AND table_name='compliance_access_contexts'
          AND column_name='scope_version' AND data_type='character varying'
          AND character_maximum_length=64
    ) THEN
        RAISE EXCEPTION 'Unexpected Compliance scope column; explicit compatibility review required';
    END IF;
END;
$$;
ALTER TABLE compliance_access_contexts ALTER COLUMN scope_version DROP NOT NULL;
ALTER TABLE compliance_access_contexts
    DROP CONSTRAINT IF EXISTS compliance_access_contexts_scope_version_check;
ALTER TABLE compliance_access_contexts
    DROP CONSTRAINT IF EXISTS compliance_context_scope_version_check;
ALTER TABLE compliance_access_contexts
    ADD CONSTRAINT compliance_context_scope_version_check
    CHECK (scope_version IS NULL OR scope_version ~ '^[0-9a-f]{64}$');

-- Existing contexts are intentionally left without a scope snapshot. The application
-- rejects them and requires a newly issued context before any protected operation.
INSERT INTO schema_migrations(version) VALUES (14);

COMMIT;
