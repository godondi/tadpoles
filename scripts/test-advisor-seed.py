#!/usr/bin/env python3
"""Test demo provisioning in disposable PostgreSQL; never touches Compose volumes."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
schema = (ROOT / "database/fresh/001_schema.sql").read_text()
seed = (ROOT / "database/demo/role_workspaces.sql").read_text()


def run(sql: str, succeeds: bool = True) -> None:
    result = subprocess.run(
        ["docker", "run", "--rm", "-i", "--user", "postgres", "--tmpfs", "/tmp",
         "postgres:15", "sh", "-eu", "-c",
         "initdb -D /tmp/advisor-test -A trust >/dev/null; "
         "pg_ctl -D /tmp/advisor-test -o '-k /tmp' -w start >/dev/null; "
         "psql -h /tmp -U postgres -v ON_ERROR_STOP=1"],
        input=sql, text=True, capture_output=True,
    )
    if succeeds and result.returncode:
        raise AssertionError(result.stderr)
    if not succeeds:
        assert result.returncode != 0, "Conflicting assignment was silently accepted"
        assert "demo.client already has a different advisor" in result.stderr, result.stderr


assertions = """
DO $$ BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM users u JOIN user_roles ur USING(user_id)
    JOIN roles r USING(role_id) JOIN advisors a USING(user_id)
    JOIN clients c ON c.advisor_id=a.advisor_id
    JOIN users owner ON owner.user_id=c.user_id
    WHERE u.username='demo.advisor' AND u.display_name='Demo Advisor'
      AND a.advisor_name='Demo Advisor' AND r.role_name='ADVISOR'
      AND owner.username='demo.client'
      AND u.password_hash=crypt('RoleDemo2026!',u.password_hash)
  ) THEN RAISE EXCEPTION 'Advisor demo identity or assignment missing'; END IF;
  IF EXISTS (SELECT 1 FROM clients c JOIN users u USING(user_id)
             WHERE u.username='demo.client2' AND c.advisor_id IS NOT NULL)
  THEN RAISE EXCEPTION 'Negative fixture was assigned'; END IF;
END $$;
"""

run(schema + seed + assertions + """
UPDATE users SET password_hash=crypt('ExistingPassword!',gen_salt('bf'))
WHERE username='demo.advisor';
UPDATE client_cash_accounts SET posted=123.45
WHERE client_id=(SELECT c.client_id FROM clients c JOIN users u USING(user_id)
                 WHERE u.username='demo.client') AND currency='USD';
""" + seed + """
DO $$ BEGIN
  IF (SELECT count(*) FROM advisors a JOIN users u USING(user_id)
      WHERE u.username='demo.advisor') != 1 THEN
    RAISE EXCEPTION 'Duplicate advisor'; END IF;
  IF NOT EXISTS (SELECT 1 FROM users WHERE username='demo.advisor'
    AND password_hash=crypt('ExistingPassword!',password_hash)) THEN
    RAISE EXCEPTION 'Existing password overwritten'; END IF;
  IF NOT EXISTS (SELECT 1 FROM client_cash_accounts ca JOIN clients c USING(client_id)
    JOIN users u USING(user_id) WHERE u.username='demo.client'
    AND ca.currency='USD' AND ca.posted=123.45) THEN
    RAISE EXCEPTION 'Existing balance overwritten'; END IF;
END $$;
""")

run(schema + seed + """
INSERT INTO advisors(advisor_name) VALUES ('Other Advisor');
UPDATE clients SET advisor_id=(SELECT advisor_id FROM advisors WHERE advisor_name='Other Advisor')
WHERE user_id=(SELECT user_id FROM users WHERE username='demo.client');
""" + seed, succeeds=False)
print("Advisor seed tests passed: identity, role, assignment, repeatability, preserved password/cash, conflict rejection.")