#!/usr/bin/env python3
"""Verify normal Compose wiring with disposable names/data and an explicit HTTP test fixture."""
import datetime, json, os, pathlib, subprocess, tempfile, time, urllib.request, urllib.error, uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
PROJECT = "tadpoles-compose-check-" + str(os.getpid())
PORT = 18083


def call(*args, input=None):
    return subprocess.check_output(args, input=input, stderr=subprocess.STDOUT).decode()


def api(path, payload=None, token=None, key=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(
        f"http://127.0.0.1:{PORT}" + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)


with tempfile.TemporaryDirectory(prefix=PROJECT) as temp:
    temp = pathlib.Path(temp)
    envfile = temp / "test.env"
    override = temp / "override.yml"

    def environment(database):
        envfile.write_text(
            f"""BACKEND_HOST_PORT={PORT}
BACKEND_CONTAINER_PORT=8080
DB_URL=jdbc:postgresql://database:5432/{database}
DB_USERNAME=test
DB_PASSWORD=test
SERVER_PORT=8080
AUTH_STUB_URL=http://auth-stub:4000
POSTGRES_HOST_PORT=18084
POSTGRES_USER=test
POSTGRES_PASSWORD=test
POSTGRES_DB={database}
AUTH_STUB_HOST_PORT=18081
AUTH_STUB_CONTAINER_PORT=4000
AUTH_STUB_PORT=4000
JWT_SECRET=isolated-compose-test-secret-at-least-32-bytes
FRONTEND_HOST_PORT=18086
FRONTEND_CONTAINER_PORT=4000
ANALYTICS_POSTGRES_HOST_PORT=18088
ANALYTICS_HOST_PORT=18089
ANALYTICS_CONTAINER_PORT=8000
"""
        )

    environment("tadpoles_isolated_test")
    lines = ["services:"]
    for service in [
        "backend",
        "frontend",
        "database",
        "database-migrate",
        "database-demo-seed",
    ]:
        lines += [
            f"  {service}:",
            "    env_file: !reset []",
            f"    container_name: {PROJECT}-{service}",
        ]
        if service == "database":
            lines += ["    ports: !reset []"]
        if service == "frontend":
            lines += ['    ports: !override ["127.0.0.1:18086:4000"]']
        if service in ["backend", "frontend"]:
            lines += [f"    image: {PROJECT}-{service}:test"]
    lines += [
        "  backend:",
        f"    container_name: {PROJECT}-backend",
        f"    image: {PROJECT}-backend:test",
        f"    build: {ROOT}/backend",
        "    env_file: !reset []",
        "    environment:",
        "      DB_URL: ${DB_URL}",
        "      DB_USERNAME: test",
        "      DB_PASSWORD: test",
        "      SERVER_PORT: 8080",
        "      JWT_SECRET: isolated-compose-test-secret-at-least-32-bytes",
        "      FAUX_SERVER_URL: http://provider:8080",
        "      FAUX_API_KEY: isolated-test-key",
        "      FAUX_DEFAULT_INTERVAL: 1d",
        "      FAUX_CONNECT_TIMEOUT_SECONDS: 2",
        "      FAUX_READ_TIMEOUT_SECONDS: 2",
        f'    ports: !override ["127.0.0.1:{PORT}:8080"]',
        "    depends_on:",
        "      database-demo-seed:",
        "        condition: service_completed_successfully",
        "      provider:",
        "        condition: service_started",
        "  provider:",
        "    image: node:22-bookworm-slim",
        f"    container_name: {PROJECT}-provider",
        f'    volumes: ["{ROOT}/scripts/fixtures/fauxnance-http.mjs:/fixture.mjs:ro"]',
        '    command: ["node", "/fixture.mjs"]',
    ]
    # Merge backend test configuration once; retain the real service dependency graph and builds.
    first = lines.index("  backend:")
    last = lines.index("  frontend:")
    del lines[first:last]
    for service in ["analytics-database", "analytics-api", "nginx"]:
        lines += [f"  {service}:", '    profiles: ["not-under-test"]',
                  "    env_file: !reset []", "    ports: !reset []"]
    override.write_text("\n".join(lines) + "\n")
    command = [
        "docker",
        "compose",
        "--env-file",
        str(envfile),
        "-p",
        PROJECT,
        "-f",
        str(ROOT / "docker-compose.yml"),
        "-f",
        str(override),
    ]

    def compose(*args):
        log = temp / "compose-build.log"
        try:
            with open(log, "ab") as output:
                subprocess.run(
                    command + list(args),
                    stdout=output,
                    stderr=subprocess.STDOUT,
                    check=True,
                )
        except subprocess.CalledProcessError as failure:
            raise RuntimeError(log.read_text()[-4000:]) from failure

    def sql(statement, database="tadpoles_isolated_test"):
        return call(
            "docker",
            "exec",
            "-i",
            PROJECT + "-database",
            "psql",
            "-U",
            "test",
            "-d",
            database,
            "-v",
            "ON_ERROR_STOP=1",
            "-At",
            input=statement.encode(),
        ).strip()

    def login():
        for _ in range(100):
            try:
                status, body = api(
                    "/auth/login",
                    {"username": "demo.client", "password": "RoleDemo2026!"},
                )
                if status == 200:
                    return body["token"]
            except (OSError, ValueError):
                pass
            time.sleep(1)
        raise AssertionError("Compose backend not ready")

    try:
        compose("config", "--quiet")
        compose("up", "--build", "-d")
        token = login()
        for _ in range(60):
            if sql("SELECT count(*) FROM instrument_trading_policies") == "14":
                break
            time.sleep(1)
        assert sql("SELECT count(*) FROM instrument_trading_policies") == "14"
        code, portfolio = api("/api/workspace/client/portfolio", token=token)
        assert code == 200 and portfolio["balances"][0]["available"] == "10000"
        code, banks = api("/api/workspace/client/banks", token=token)
        assert code == 200 and len(banks) == 1
        code, receipt = api(
            "/api/workspace/client/cash-movements",
            {"bankId": banks[0]["bankId"], "direction": "WITHDRAW", "amount": "123.45"},
            token,
            str(uuid.uuid4()),
        )
        assert code == 200 and receipt["status"] == "SETTLED"
        case_key = str(uuid.uuid4())
        code, support = api("/api/support/cases", {
            "category": "ACCOUNT_REVIEW", "subject": "Account receipt review",
            "description": "Please explain the retained account movement receipt."
        }, token, case_key)
        assert code == 200 and support["orderId"] is None, support
        sql(
            "UPDATE instrument_trading_policies SET enabled=false,revision=revision+1 WHERE provider_symbol='AAPL'"
        )
        compose("restart", "backend")
        token = login()
        assert (
            sql(
                "SELECT posted FROM client_cash_accounts c JOIN clients cl USING(client_id) JOIN users u USING(user_id) WHERE username='demo.client' AND currency='USD'"
            )
            == "9876.550000000000"
        )
        assert (
            sql(
                "SELECT enabled FROM instrument_trading_policies WHERE provider_symbol='AAPL'"
            )
            == "f"
        )
        assert (
            sql(
                "SELECT count(*) FROM cash_journal WHERE setup_reference LIKE 'local-demo-usd-v1:%'"
            )
            == "1"
        )
        code, recovered_support = api("/api/support/cases/by-request/" + case_key, token=token)
        assert code == 200 and recovered_support["reference"] == support["reference"], recovered_support
        assert sql("SELECT count(*) FROM schema_migrations WHERE version=12") == "1"
        # Same retained PostgreSQL volume, populated old schema, then the normal new migration/init wiring.
        compose("stop", "backend")
        sql("CREATE DATABASE tadpoles_upgrade_test")
        # Reconstruct the historical simulation schema before exercising its upgrade.
        legacy_sources = sorted((ROOT / "database/schema").glob("*.sql")) + [
            ROOT / "database/migrations/003_workspace_roles.sql",
            ROOT / "database/migrations/004_client_simulation.sql",
        ]
        for source in legacy_sources:
            sql(source.read_text(), "tadpoles_upgrade_test")
        sql(
            (ROOT / "database/demo/role_workspaces.sql").read_text(),
            "tadpoles_upgrade_test",
        )
        sql(
            (ROOT / "database/demo/simulation.sql").read_text(), "tadpoles_upgrade_test"
        )
        before = sql(
            "SELECT count(*) FROM users;SELECT count(*) FROM client_trades;SELECT count(*) FROM cash_journal",
            "tadpoles_upgrade_test",
        )
        environment("tadpoles_upgrade_test")
        compose("up", "-d", "--force-recreate")
        token = login()
        assert (
            sql(
                "SELECT count(*) FROM schema_migrations WHERE version BETWEEN 3 AND 12",
                "tadpoles_upgrade_test",
            )
            == "10"
        )
        assert (
            sql(
                "SELECT count(*) FROM users;SELECT count(*) FROM client_trades;SELECT count(*) FROM cash_journal",
                "tadpoles_upgrade_test",
            )
            == before
        )
        assert (
            int(
                sql(
                    "SELECT count(*) FROM financial_migration_exceptions",
                    "tadpoles_upgrade_test",
                )
            )
            > 0
        )
        assert (
            sql(
                "SELECT count(*) FROM client_cash_accounts WHERE posted<>0 AND available_for_trading",
                "tadpoles_upgrade_test",
            )
            == "0"
        )
        assert (
            sql(
                "SELECT count(*) FROM instruments WHERE is_active AND (ticker LIKE 'SIM-%' OR ticker='CASH')",
                "tadpoles_upgrade_test",
            )
            == "0"
        )
        print(
            "PASS: isolated Compose build/fresh startup, 14 mapped policies, USD 10000 demo opening, spend and repeat startup without refund or policy reset, populated existing-volume upgrade retaining users/orders/journals and quarantining unresolved balances."
        )
    finally:
        compose("down", "--volumes", "--remove-orphans")
