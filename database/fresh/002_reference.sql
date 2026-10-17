-- Requires fresh/001_schema.sql. Repeatable reference data, not demo accounts.
-- Record the migrations already represented by the snapshot so migrate.sh skips them.

BEGIN;

INSERT INTO roles (role_name)
    VALUES ('ADMIN'),
    ('ADVISOR'),
    ('CLIENT'),
    ('TRADING_OPS'),
    ('COMPLIANCE'),
    ('ANALYST'),
    ('FINANCE')
ON CONFLICT
    DO NOTHING;

INSERT INTO schema_migrations (version)
    VALUES (3),
    (4),
    (5),
    (6),
    (7),
    (8),
    (9),
    (10)
ON CONFLICT
    DO NOTHING;

COMMIT;
