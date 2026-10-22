BEGIN;

ALTER TABLE compliance_access_contexts
    ADD COLUMN scope_version varchar(64);
ALTER TABLE compliance_access_contexts
    ADD CONSTRAINT compliance_context_scope_version_check
    CHECK (scope_version IS NULL OR scope_version ~ '^[0-9a-f]{64}$');

-- Existing contexts are intentionally left without a scope snapshot. The application
-- rejects them and requires a newly issued context before any protected operation.
INSERT INTO schema_migrations(version) VALUES (14);

COMMIT;
