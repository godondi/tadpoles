#!/usr/bin/env python3
"""Rehearse supported Compliance upgrades with the actual runner in disposable PostgreSQL."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
NAME = f"tadpoles-compliance-upgrade-{os.getpid()}"


def command(*args, input=None):
    result = subprocess.run(args, input=input, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"{' '.join(args)}\n{result.stdout}\n{result.stderr}")
    return result.stdout.strip()


def sql(query):
    return command("docker", "exec", "-i", NAME, "psql", "-XqAt",
                   "-v", "ON_ERROR_STOP=1", "-U", "test", "-d", "tadpoles_isolated_test",
                   input=query)


def source(path):
    sql(path.read_text())


def shape():
    return sql("""
        SELECT table_name,column_name,data_type,is_nullable,
               coalesce(character_maximum_length,0),coalesce(column_default,'')
        FROM information_schema.columns WHERE table_schema='public'
        ORDER BY table_name,column_name;
        SELECT c.relname,x.conname,pg_get_constraintdef(x.oid)
        FROM pg_constraint x JOIN pg_class c ON c.oid=x.conrelid
        WHERE c.relnamespace='public'::regnamespace ORDER BY c.relname,x.conname;
        SELECT c.relname,t.tgname,pg_get_triggerdef(t.oid)
        FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid
        WHERE NOT t.tgisinternal AND c.relnamespace='public'::regnamespace
        ORDER BY c.relname,t.tgname;
        SELECT tablename,indexname,indexdef FROM pg_indexes
        WHERE schemaname='public' ORDER BY tablename,indexname;
        SELECT p.proname,pg_get_function_identity_arguments(p.oid),
               regexp_replace(p.prosrc,'[[:space:]]','','g')
        FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
        WHERE n.nspname='public' ORDER BY p.proname;
        SELECT version FROM schema_migrations ORDER BY version;
    """)


def runner():
    command("docker", "exec", "-e", "PGUSER=test", "-e",
            "PGDATABASE=tadpoles_isolated_test", NAME, "sh", "/runner.sh")

def runtime_permissions():
    sql("\\set runtime_role workflow_runtime\n" + (ROOT / "database/runtime-grants.sql").read_text())
    immutable = (
        "support_case_intake", "support_case_workflow_events",
        "support_case_workflow_requests", "support_case_resolutions", "support_case_referrals",
        "compliance_investigation_events", "compliance_investigation_requests",
        "compliance_investigation_exports",
    )
    for table in immutable:
        privileges = sql(f"""
            SELECT has_table_privilege('workflow_runtime','{table}','SELECT'),
                   has_table_privilege('workflow_runtime','{table}','INSERT'),
                   has_table_privilege('workflow_runtime','{table}','UPDATE'),
                   has_table_privilege('workflow_runtime','{table}','DELETE'),
                   has_table_privilege('workflow_runtime','{table}','TRUNCATE');
        """)
        assert privileges == "t|t|f|f|f", (table, privileges)
    mutable = {
        "support_case_workflow": {
            "effective_category", "assignee", "priority", "next_action", "due_at",
            "version", "checklist_revision", "updated_at",
        },
        "support_case_tasks": {
            "state", "reason", "evidence_kind", "evidence_reference", "actor", "recorded_at",
        },
        "compliance_investigations": {
            "reviewer", "status", "version", "disposition", "findings",
            "client_safe_explanation", "updated_at",
        },
    }
    for table, allowed in mutable.items():
        actual = sql(f"""
            SELECT column_name FROM information_schema.columns
            WHERE table_schema='public' AND table_name='{table}'
              AND has_column_privilege('workflow_runtime','{table}',column_name,'UPDATE')
            ORDER BY column_name;
        """)
        assert set(actual.splitlines()) == allowed, (table, actual)
        assert sql(f"SELECT has_table_privilege('workflow_runtime','{table}','DELETE,TRUNCATE')") == "f"
    for table in ("operations_access_grants", "compliance_access_grants"):
        assert sql(f"SELECT has_table_privilege('workflow_runtime','{table}','INSERT,UPDATE,DELETE,TRUNCATE')") == "f"
    assert sql("SELECT has_schema_privilege('workflow_runtime','public','CREATE')") == "f"
    sql("""
        SET ROLE workflow_runtime;
        SELECT count(*) FROM support_case_workflow;
        UPDATE support_case_workflow SET next_action=next_action WHERE false;
        UPDATE support_case_tasks SET reason=reason WHERE false;
        UPDATE compliance_investigations SET status=status WHERE false;
        DO $$
        BEGIN
            BEGIN
                DELETE FROM support_case_workflow_events WHERE false;
                RAISE EXCEPTION 'Runtime unexpectedly deleted immutable workflow history';
            EXCEPTION WHEN insufficient_privilege THEN NULL;
            END;
            BEGIN
                UPDATE compliance_investigations SET client_id=client_id WHERE false;
                RAISE EXCEPTION 'Runtime unexpectedly changed investigation source identity';
            EXCEPTION WHEN insufficient_privilege THEN NULL;
            END;
        END $$;
        RESET ROLE;
    """)


def main():
    with tempfile.TemporaryDirectory(prefix="tadpoles-compliance-upgrade-") as temporary:
        migrations = Path(temporary) / "migrations"
        shutil.copytree(ROOT / "database/migrations", migrations)
        files = sorted(migrations.glob("*.sql"))
        try:
            command("docker", "run", "-d", "--name", NAME,
                    "-e", "POSTGRES_USER=test", "-e", "POSTGRES_PASSWORD=test",
                    "-e", "POSTGRES_DB=tadpoles_isolated_test",
                    "--mount", f"type=bind,src={migrations},dst=/migrations,readonly",
                    "--mount", f"type=bind,src={ROOT / 'database/migrate.sh'},dst=/runner.sh,readonly",
                    "postgres:15-alpine")
            import time
            for _ in range(30):
                try:
                    sql("SELECT 1")
                    break
                except RuntimeError:
                    time.sleep(1)
            else:
                raise RuntimeError("Disposable PostgreSQL did not become responsive")

            sql("CREATE ROLE workflow_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;")
            expected = None
            for variant in ("fresh", "retained012", "old013", "current013", "applied014"):
                sql("DROP SCHEMA public CASCADE; CREATE SCHEMA public;")
                if variant == "fresh":
                    source(ROOT / "database/fresh/001_schema.sql")
                    source(ROOT / "database/fresh/002_reference.sql")
                else:
                    source(ROOT / "database/schema/001_tadpoles_schema.sql")
                    sql("CREATE TABLE IF NOT EXISTS schema_migrations(version integer PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now());")
                    for file in files:
                        version = int(file.name.split("_")[0])
                        if version > 12:
                            break
                        source(file)
                        sql(f"INSERT INTO schema_migrations(version) VALUES({version}) ON CONFLICT DO NOTHING")
                    if variant != "retained012":
                        ddl = (migrations / "013_compliance_workspace.sql").read_text()
                        if variant in ("old013", "applied014"):
                            ddl = ddl.replace(
                                "    scope_version varchar(64) NOT NULL CHECK (scope_version ~ '^[0-9a-f]{64}$'),\n", "")
                        sql(ddl)
                        if variant in ("old013", "applied014"):
                            sql("""
                                INSERT INTO users(user_id,username,password_hash,display_name)
                                VALUES(987654,'legacy.context.actor','fixture','Fixture');
                                INSERT INTO compliance_access_contexts(context_id,actor,purpose_code,
                                    case_reference,created_at,expires_at,policy_version)
                                VALUES('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',987654,'ORDER_REVIEW',
                                    'LEGACY-CONTEXT',now(),now()+interval '1 hour','fixture-v1');
                            """)
                        if variant == "applied014":
                            sql("""
                                ALTER TABLE compliance_access_contexts ADD COLUMN scope_version varchar(64);
                                ALTER TABLE compliance_access_contexts ADD CONSTRAINT compliance_context_scope_version_check
                                    CHECK(scope_version IS NULL OR scope_version ~ '^[0-9a-f]{64}$');
                                INSERT INTO schema_migrations(version) VALUES(14);
                            """)
                runner()
                current = shape()
                if expected is None:
                    expected = current
                if expected != current:
                    raise AssertionError(f"Fresh/upgrade structural mismatch: {variant}")
                runner()
                assert current == shape(), f"Repeated runner changed schema: {variant}"
                if variant in ("old013", "applied014"):
                    assert sql("SELECT scope_version IS NULL FROM compliance_access_contexts WHERE actor=987654") == "t"
                runtime_permissions()
                print(f"PASS {variant}: actual runner, parity, repeatability, preserved context scope, restricted runtime")
        finally:
            command("docker", "rm", "-f", NAME)


if __name__ == "__main__":
    main()
