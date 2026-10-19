#!/usr/bin/env python3
"""Disposable process restart + pg_dump/restore rehearsal; never connects to Compose volumes."""
import json, os, pathlib, subprocess, time, urllib.request, urllib.error, uuid

root = pathlib.Path(__file__).resolve().parent.parent
prefix = "tadpoles-recovery-" + str(os.getpid())
port = "18082"


def run(*args, input=None):
    return subprocess.check_output(args, input=input, stderr=subprocess.STDOUT).decode()


def docker(*args, **kwargs):
    return run("docker", *args, **kwargs)


def sql(statement, database="tadpoles_isolated_test"):
    return docker(
        "exec",
        "-i",
        prefix + "-db",
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


def api(path, payload=None, token=None, key=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(
        "http://127.0.0.1:" + port + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return res.status, json.load(res)
    except urllib.error.HTTPError as err:
        return err.code, json.load(err)


def start(database, admission, worker, restricted=False):
    docker(
        "run",
        "-d",
        "--name",
        prefix + "-api",
        "--network",
        prefix,
        "-p",
        "127.0.0.1:" + port + ":8080",
        "-e",
        "DB_URL=jdbc:postgresql://"
        + prefix
        + "-db:5432/"
        + database
        + ("?options=-c%20role%3Drecovery_runtime" if restricted else ""),
        "-e",
        "DB_USERNAME=test",
        "-e",
        "DB_PASSWORD=test",
        "-e",
        "JWT_SECRET=disposable-recovery-secret-at-least-32-bytes",
        "-e",
        "SERVER_PORT=8080",
        "-e",
        "TRADING_ADMISSION_ENABLED=" + str(admission).lower(),
        "-e",
        "TRADING_WORKER_ENABLED=" + str(worker).lower(),
        "-e",
        "TRADING_LOCAL_DEMO_SETUP=true",
        "-e",
        "BANKING_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "-e",
        "FAUX_SERVER_URL=http://" + prefix + "-provider:8080",
        "-e",
        "FAUX_API_KEY=isolated-test-key",
        "-e",
        "FAUX_DEFAULT_INTERVAL=1d",
        "-e",
        "FAUX_CONNECT_TIMEOUT_SECONDS=1",
        "-e",
        "FAUX_READ_TIMEOUT_SECONDS=1",
        "-v",
        str(root / "backend/target/team-skeleton.jar") + ":/app.jar:ro",
        "maven:3.9-eclipse-temurin-21",
        "java",
        "-jar",
        "/app.jar",
    )
    for _ in range(60):
        try:
            status, body = api(
                "/auth/login", {"username": "demo.client", "password": "RoleDemo2026!"}
            )
            if status == 200:
                return body["token"]
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise AssertionError("Disposable API did not become ready")


def stop_api():
    docker("rm", "-f", prefix + "-api")


def wait_fill(order, token):
    for _ in range(30):
        status, body = api("/api/orders/" + str(order), token=token)
        if status == 200 and body["order"]["status"] == "FILLED":
            return body
        time.sleep(1)
    raise AssertionError("Restored order did not fill within the execution deadline")


try:
    docker("network", "create", prefix)
    docker(
        "run",
        "-d",
        "--name",
        prefix + "-db",
        "--network",
        prefix,
        "-e",
        "POSTGRES_DB=tadpoles_isolated_test",
        "-e",
        "POSTGRES_USER=test",
        "-e",
        "POSTGRES_PASSWORD=test",
        "postgres:15-alpine",
    )
    for _ in range(30):
        try:
            sql("SELECT 1")
            break
        except subprocess.CalledProcessError:
            time.sleep(1)
    for file in sorted((root / "database/fresh").glob("*.sql")) + [
        root / "database/demo/role_workspaces.sql"
    ]:
        sql(file.read_text())
    for _ in range(2):
        docker(
            "run",
            "--rm",
            "--network",
            prefix,
            "-e",
            "PGHOST=" + prefix + "-db",
            "-e",
            "PGUSER=test",
            "-e",
            "PGPASSWORD=test",
            "-e",
            "PGDATABASE=tadpoles_isolated_test",
            "-v",
            str(root / "database/migrations") + ":/migrations:ro",
            "-v",
            str(root / "database/migrate.sh") + ":/migrate.sh:ro",
            "postgres:15-alpine",
            "sh",
            "/migrate.sh",
        )
    docker(
        "run",
        "-d",
        "--name",
        prefix + "-provider",
        "--network",
        prefix,
        "-v",
        str(root / "scripts/fixtures/fauxnance-http.mjs") + ":/fixture.mjs:ro",
        "node:22-bookworm-slim",
        "node",
        "/fixture.mjs",
    )
    token = start("tadpoles_isolated_test", True, True)
    for _ in range(60):
        if (
            sql(
                "SELECT count(*) FROM instrument_trading_policies p JOIN instruments i USING(instrument_id) WHERE i.ticker='AAPL'"
            )
            == "1"
        ):
            break
        time.sleep(1)
    instrument = int(sql("SELECT instrument_id FROM instruments WHERE ticker='AAPL'"))
    command = {
        "instrumentId": instrument,
        "side": "BUY",
        "quantity": "1",
        "orderType": "MARKET",
    }
    code, filled = api("/api/orders", command, token, str(uuid.uuid4()))
    assert code == 202
    wait_fill(filled["orderId"], token)
    stop_api()
    token = start("tadpoles_isolated_test", True, False)
    key = str(uuid.uuid4())
    code, accepted = api("/api/orders", command, token, key)
    assert code == 202
    assert (
        sql(
            "SELECT status FROM client_trades WHERE trade_id="
            + str(accepted["orderId"])
        )
        == "ACCEPTED"
    )
    code, banks = api("/api/workspace/client/banks", token=token)
    assert code == 200 and len(banks) == 1
    movement_key = str(uuid.uuid4())
    movement = {"bankId": banks[0]["bankId"], "direction": "DEPOSIT", "amount": "10.00"}
    code, deposit = api(
        "/api/workspace/client/cash-movements", movement, token, movement_key
    )
    assert code == 200 and deposit["status"] == "SETTLED"
    dump = docker(
        "exec",
        prefix + "-db",
        "pg_dump",
        "-U",
        "test",
        "--no-owner",
        "--no-acl",
        "tadpoles_isolated_test",
    )
    sql("CREATE DATABASE tadpoles_restored_test")
    sql(dump, "tadpoles_restored_test")
    assert sql("SELECT count(*) FROM order_fills", "tadpoles_restored_test") == "1"
    assert (
        sql(
            "SELECT count(*) FROM order_reservations WHERE state='ACTIVE'",
            "tadpoles_restored_test",
        )
        == "1"
    )
    stop_api()
    # Rollback mode: no new admissions; acknowledged work drains after restart/restore.
    token = start("tadpoles_restored_test", False, True)
    wait_fill(accepted["orderId"], token)
    code, restored_deposit = api(
        "/api/workspace/client/cash-movements", movement, token, movement_key
    )
    assert code == 200 and restored_deposit["movementId"] == deposit["movementId"]
    code, _ = api(
        "/api/workspace/client/cash-movements",
        {**movement, "amount": "11"},
        token,
        movement_key,
    )
    assert code == 409
    assert sql("SELECT count(*) FROM cash_movements", "tadpoles_restored_test") == "1"
    code, replay = api("/api/orders", command, token, key)
    assert code == 200 and replay["orderId"] == accepted["orderId"]
    code, _ = api("/api/orders", command, token, str(uuid.uuid4()))
    assert code == 503
    assert sql("SELECT count(*) FROM order_fills", "tadpoles_restored_test") == "2"
    assert (
        sql(
            "SELECT count(*) FROM order_reservations WHERE state='ACTIVE'",
            "tadpoles_restored_test",
        )
        == "0"
    )
    assert (
        sql(
            "SELECT count(*) FROM order_execution_jobs WHERE NOT completed",
            "tadpoles_restored_test",
        )
        == "0"
    )
    for statement in [
        "SELECT count(*) FROM client_cash_accounts c WHERE c.posted<>(SELECT COALESCE(sum(delta),0) FROM cash_journal j WHERE j.client_id=c.client_id AND j.currency=c.currency)",
        "SELECT count(*) FROM client_positions p WHERE p.posted<>(SELECT COALESCE(sum(delta),0) FROM position_journal j WHERE j.client_id=p.client_id AND j.instrument_id=p.instrument_id)",
        "SELECT count(*) FROM order_events e LEFT JOIN outbox_events o ON o.event_id=e.event_id WHERE o.event_id IS NULL",
    ]:
        assert sql(statement, "tadpoles_restored_test") == "0"
    # The restored filled order remains single-filled through another process restart.
    stop_api()
    token = start("tadpoles_restored_test", False, True)
    wait_fill(accepted["orderId"], token)
    assert sql("SELECT count(*) FROM order_fills", "tadpoles_restored_test") == "2"
    # No-login role exists ONLY inside this disposable cluster; no persistent credentials provisioned.
    sql("CREATE ROLE recovery_runtime NOLOGIN", "tadpoles_restored_test")
    docker(
        "exec",
        "-i",
        prefix + "-db",
        "psql",
        "-U",
        "test",
        "-d",
        "tadpoles_restored_test",
        "-v",
        "runtime_role=recovery_runtime",
        input=(root / "database/runtime-grants.sql").read_bytes(),
    )
    assert (
        sql(
            "SELECT has_table_privilege('recovery_runtime','order_fills','SELECT') AND has_table_privilege('recovery_runtime','order_fills','INSERT') AND NOT has_table_privilege('recovery_runtime','order_fills','UPDATE') AND NOT has_table_privilege('recovery_runtime','order_fills','DELETE') AND NOT has_table_privilege('recovery_runtime','order_fills','TRUNCATE')",
            "tadpoles_restored_test",
        )
        == "t"
    )
    for forbidden in [
        "UPDATE order_fills SET price=1",
        "TRUNCATE order_fills CASCADE",
        "ALTER TABLE order_fills DISABLE TRIGGER ALL",
    ]:
        try:
            sql("SET ROLE recovery_runtime;" + forbidden, "tadpoles_restored_test")
        except subprocess.CalledProcessError:
            pass
        else:
            raise AssertionError("Runtime role could rewrite evidence")
    stop_api()
    token = start("tadpoles_restored_test", True, True, restricted=True)
    code, runtime_order = api("/api/orders", command, token, str(uuid.uuid4()))
    assert code == 202
    wait_fill(runtime_order["orderId"], token)
    assert sql("SELECT count(*) FROM order_fills", "tadpoles_restored_test") == "3"
    # Selling all credits internal cash; withdrawal is separately confirmed and replayable.
    sale = {**command, "side": "SELL", "quantity": "3"}
    code, sold = api("/api/orders", sale, token, str(uuid.uuid4()))
    assert code == 202
    wait_fill(sold["orderId"], token)
    assert (
        sql(
            "SELECT posted FROM client_positions WHERE instrument_id="
            + str(instrument),
            "tadpoles_restored_test",
        )
        == "0.000000000000"
    )
    withdrawal_key = str(uuid.uuid4())
    withdrawal = {**movement, "direction": "WITHDRAW", "amount": "297.00"}
    code, withdrawn = api(
        "/api/workspace/client/cash-movements", withdrawal, token, withdrawal_key
    )
    assert code == 200 and withdrawn["status"] == "SETTLED"
    code, repeated = api(
        "/api/workspace/client/cash-movements", withdrawal, token, withdrawal_key
    )
    assert code == 200 and repeated["movementId"] == withdrawn["movementId"]
    code, finance = api(
        "/auth/login", {"username": "demo.finance", "password": "RoleDemo2026!"}
    )
    assert code == 200
    code, report = api("/api/finance/reconciliations", {}, finance["token"])
    assert code == 200 and report["matched"], report
    assert (
        sql(
            "SELECT count(*) FROM order_reservations WHERE state='ACTIVE'",
            "tadpoles_restored_test",
        )
        == "0"
    )
    print(
        "PASS: HTTP-provider buy, backup/restore, deposit replay after restore, admission halt/drain, restart, non-owner booking, sell-all, withdrawal/replay, immutable evidence grants, repeated migrations, and persisted zero-difference reconciliation."
    )
finally:
    subprocess.run(
        ["docker", "rm", "-f", prefix + "-api", prefix + "-db", prefix + "-provider"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    subprocess.run(
        ["docker", "network", "rm", prefix],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
