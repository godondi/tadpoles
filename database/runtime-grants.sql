-- Run as the migration owner with: psql -v runtime_role=YOUR_EXISTING_APP_ROLE -f database/runtime-grants.sql
-- Requires the current schema and a separately provisioned, non-owner runtime role.
-- This script neither creates login credentials nor changes passwords.
-- Use psql: the backslash commands and quoted runtime_role variables are client syntax.
\set ON_ERROR_STOP on
SELECT
    EXISTS (
        SELECT
            1
        FROM
            pg_roles
        WHERE
            rolname = :'runtime_role'
            AND NOT rolsuper
            AND NOT rolcreaterole
            AND NOT rolcreatedb
            AND NOT rolbypassrls)
    AND NOT EXISTS (
        SELECT
            1
        FROM
            pg_class c
            JOIN pg_roles r ON r.oid = c.relowner
        WHERE
            r.rolname = :'runtime_role'
            AND c.relnamespace = 'public'::regnamespace)
    AND NOT EXISTS (
        SELECT
            1
        FROM
            pg_namespace n
            JOIN pg_roles r ON r.oid = n.nspowner
        WHERE
            r.rolname = :'runtime_role'
            AND n.nspname = 'public')
    AND NOT EXISTS (
        SELECT
            1
        FROM
            pg_database d
            JOIN pg_roles r ON r.oid = d.datdba
        WHERE
            d.datname = current_database()
            AND r.rolname = :'runtime_role')
    AND NOT EXISTS (
        SELECT
            1
        FROM
            pg_auth_members m
            JOIN pg_roles r ON r.oid = m.member
        WHERE
            r.rolname = :'runtime_role') AS safe_role \gset

\if :safe_role
BEGIN;

GRANT USAGE ON SCHEMA public TO :"runtime_role";

-- Existing administrative operations retain their table permissions; database guards protect migrated accounts.
GRANT SELECT, INSERT, UPDATE, DELETE ON users, roles, user_roles, advisors, model_portfolios, clients,
    client_profiles, instruments, model_portfolio_holdings, client_subscriptions, trade_suggestions,
    client_trades, client_holdings, refresh_tokens, audit_logs TO :"runtime_role";

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"runtime_role";

GRANT SELECT ON schema_migrations, financial_migration_exceptions TO :"runtime_role";

GRANT SELECT, INSERT, UPDATE ON client_cash_accounts, client_positions, order_reservations,
    order_execution_jobs, auth_sessions TO :"runtime_role";

GRANT SELECT, INSERT ON order_quote_observations, order_rule_decisions, order_fills, cash_journal,
    position_journal, order_events, order_execution_attempts, outbox_events TO :"runtime_role";

GRANT SELECT, INSERT, UPDATE ON bank_references, instrument_trading_policies TO :"runtime_role";

GRANT SELECT, INSERT ON cash_movements, cash_movement_outbox, instrument_policy_audit, provider_setup_checks,
    reconciliation_runs TO :"runtime_role";

REVOKE UPDATE, DELETE, TRUNCATE
    ON cash_movements,
    cash_movement_outbox,
    instrument_policy_audit,
    provider_setup_checks,
    reconciliation_runs,
    financial_migration_exceptions FROM :"runtime_role";

-- Source facts remain append-only, including for a role previously given broader grants.
REVOKE UPDATE, DELETE, TRUNCATE
    ON order_quote_observations,
    order_rule_decisions,
    order_fills,
    cash_journal,
    position_journal,
    order_events,
    order_execution_attempts,
    outbox_events FROM :"runtime_role";

-- Staff cannot provision grants through the application runtime identity.
GRANT SELECT ON operations_access_grants, operations_grant_events TO :"runtime_role";
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON operations_access_grants, operations_grant_events FROM :"runtime_role";
GRANT SELECT, INSERT ON operations_access_contexts, operations_access_events,
    operations_evidence_exports, order_decision_contexts, reconciliation_findings TO :"runtime_role";
REVOKE UPDATE, DELETE, TRUNCATE ON operations_access_contexts, operations_access_events,
    operations_evidence_exports, order_decision_contexts, reconciliation_findings FROM :"runtime_role";

GRANT SELECT, INSERT ON support_cases, support_case_events, support_case_notes, support_case_requests, support_case_exports TO :"runtime_role";
REVOKE UPDATE ON support_cases FROM :"runtime_role";
GRANT UPDATE(status,version,updated_at) ON support_cases TO :"runtime_role";
REVOKE DELETE, TRUNCATE ON support_cases FROM :"runtime_role";
REVOKE UPDATE, DELETE, TRUNCATE ON support_case_events, support_case_notes, support_case_requests, support_case_exports FROM :"runtime_role";

REVOKE UPDATE ON support_read_state FROM :"runtime_role";
GRANT UPDATE(last_event) ON support_read_state TO :"runtime_role";
GRANT SELECT, INSERT ON support_read_state TO :"runtime_role";
REVOKE DELETE, TRUNCATE ON support_read_state FROM :"runtime_role";

REVOKE TRUNCATE, TRIGGER ON ALL TABLES IN SCHEMA public FROM :"runtime_role";

REVOKE CREATE ON SCHEMA public FROM :"runtime_role";

COMMIT;

\else
\echo 'Runtime role must already exist, be unprivileged, and own no public schema/tables.'
\quit 1
\endif
