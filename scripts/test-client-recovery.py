#!/usr/bin/env python3
"""Disposable process restart + pg_dump/restore rehearsal; never connects to Compose volumes."""
import hashlib, json, os, pathlib, runpy, subprocess, tempfile, time, urllib.request, urllib.error, uuid

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


def api(path, payload=None, token=None, key=None, context=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    if context:
        headers["X-Operations-Context"] = context
    req = urllib.request.Request(
        "http://127.0.0.1:" + port + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return res.status, res.read() if res.headers.get_content_type() == "application/zip" else json.load(res)
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
        "-e", "OPERATIONS_ACCESS_ENABLED=true",
        "-e", "OPERATIONS_ALLOWED_PURPOSES=ORDER_REVIEW",
        "-e", "OPERATIONS_POLICY_VERSION=recovery-fixture-v1",
        "-e", "OPERATIONS_CONTEXT_SECONDS=3600",
        "-e", "OPERATIONS_EXPORT_SECONDS=3600",
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
    # Explicit isolated grants exercise recovery of scope, purpose and immutable artifacts.
    sql("""INSERT INTO operations_access_grants(grant_id,staff_user_id,permission,client_id,
        account_reference,valid_from,valid_until,approved_by,approval_reference)
        SELECT gen_random_uuid(),staff.user_id,p.permission,c.client_id,'RECOVERY-ACCOUNT',
          now()-interval '1 minute',now()+interval '1 hour',approver.user_id,'RECOVERY-TEST-APPROVAL'
        FROM users staff CROSS JOIN users approver CROSS JOIN clients c
        CROSS JOIN (VALUES ('ORDER_READ'),('EVIDENCE_READ'),('EVIDENCE_EXPORT'),('RECONCILIATION_READ'),('CASE_READ'),('CASE_UPDATE')) p(permission)
        JOIN users owner ON owner.user_id=c.user_id
        WHERE staff.username='demo.operations' AND approver.username='demo.finance' AND owner.username='demo.client'""")
    code, operations = api('/auth/login', {'username':'demo.operations','password':'RoleDemo2026!'})
    assert code == 200
    operations_token = operations['token']
    code, purpose = api('/api/operations/access-contexts', {'purposeCode':'ORDER_REVIEW','referenceType':'EXTERNAL','caseReference':'RECOVERY-CASE-001'}, operations_token)
    assert code == 200, purpose
    external_context = purpose['contextId']
    case_key = str(uuid.uuid4())
    case_command = {'category':'ORDER_QUESTION','subject':'Retained receipt question',
                    'description':'Please explain the retained execution price.', 'orderId':filled['orderId']}
    code, case = api('/api/support/cases',case_command,token,case_key)
    assert code == 200, case
    case_reference = case['reference']
    client_case = '/api/support/cases/' + case_reference
    staff_case = '/api/operations/cases/' + case_reference
    code, updates = api('/api/support/updates',token=token)
    assert code == 200 and updates['unread'] == 1
    code, read = api('/api/support/updates/read',{'cursor':updates['cursor']},token)
    assert code == 200 and read['unread'] == 0
    reply_key = str(uuid.uuid4())
    reply_command = {'expectedVersion':1,'body':'The retained execution quote was used for internal booking.'}
    code, reply = api(staff_case+'/replies',reply_command,operations_token,reply_key)
    assert code == 200 and reply['version'] == 2, reply
    code, public_before = api(client_case,token=token)
    code, note = api(staff_case+'/notes',{'expectedVersion':2,'body':'Private investigation retained for staff review.'},operations_token,str(uuid.uuid4()))
    assert code == 200, note
    code, public_after = api(client_case,token=token)
    assert code == 200 and public_before == public_after
    code, resolved = api(staff_case+'/transitions',{'expectedVersion':2,'status':'RESOLVED','body':'The retained receipt explains the internal booking outcome.'},operations_token,str(uuid.uuid4()))
    assert code == 200 and resolved['version'] == 3, resolved
    code, reopened = api(client_case+'/reopen',{'expectedVersion':3,'body':'Please explain the reservation consumption as well.'},token,str(uuid.uuid4()))
    assert code == 200 and reopened['version'] == 4, reopened
    code, purpose = api('/api/operations/access-contexts',{'purposeCode':'ORDER_REVIEW','referenceType':'MANAGED','caseReference':case_reference},operations_token)
    assert code == 200, purpose
    context = purpose['contextId']
    code, exported = api('/api/operations/evidence-exports', {'orderId':filled['orderId']}, operations_token, str(uuid.uuid4()), context)
    assert code == 200, exported
    code, association = api(staff_case+'/exports',{'exportId':exported['exportId']},operations_token,context=context)
    assert code == 200 and association['digest'] == exported['digest'], association
    download_path = '/api/operations/evidence-exports/' + exported['exportId'] + '/download'
    code, archive_before = api(download_path, token=operations_token, context=context)
    assert code == 200 and hashlib.sha256(archive_before).hexdigest() == exported['digest']
    verifier = runpy.run_path(str(root / 'scripts/verify-operations-export.py'))['verify']
    with tempfile.TemporaryDirectory(prefix='operations-verifier-') as directory:
        archive_path = pathlib.Path(directory) / 'evidence.zip'
        archive_path.write_bytes(archive_before)
        assert verifier(archive_path, exported['digest'])['verifiedFiles'] == 4
        try:
            verifier(archive_path, '0' * 64)
        except ValueError:
            pass
        else:
            raise AssertionError('Verifier accepted an incorrect archive digest')

    restored_tables = ['operations_access_grants','operations_grant_events','operations_access_contexts',
        'operations_access_events','operations_evidence_exports','order_decision_contexts',
        'support_cases','support_case_events','support_case_notes','support_case_requests','support_read_state','support_case_exports']
    before_restore = {table:sql('SELECT count(*) FROM '+table) for table in restored_tables}
    restore_started = time.monotonic()
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
    restore_seconds = time.monotonic() - restore_started
    for table, count in before_restore.items():
        assert sql('SELECT count(*) FROM '+table, 'tadpoles_restored_test') == count
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
    code, recovered_case = api('/api/support/cases/by-request/'+case_key,token=token)
    assert code == 200 and recovered_case['reference'] == case_reference and recovered_case['status'] == 'IN_REVIEW', recovered_case
    code, recovered_reply = api(staff_case+'/requests/'+reply_key,token=operations_token)
    assert code == 200 and recovered_reply == reply, recovered_reply
    code, restored_updates = api('/api/support/updates',token=token)
    assert code == 200 and restored_updates['unread'] == 3, restored_updates
    code, external_detail = api('/api/operations/orders/'+str(filled['orderId']),token=operations_token,context=external_context)
    assert code == 200, external_detail
    wait_fill(accepted["orderId"], token)
    code, archive_after = api(download_path, token=operations_token, context=context)
    assert code == 200 and archive_after == archive_before
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
    for table in ['operations_grant_events','operations_access_contexts','operations_access_events',
                  'operations_evidence_exports','order_decision_contexts','reconciliation_findings',
                  'support_case_events','support_case_notes','support_case_requests','support_case_exports']:
        assert sql(
            "SELECT NOT has_table_privilege('recovery_runtime','" + table + "','UPDATE')",
            'tadpoles_restored_test',
        ) == 't'
        for action in ['DELETE FROM ' + table, 'TRUNCATE ' + table + ' CASCADE']:
            try:
                sql('SET ROLE recovery_runtime;'+action, 'tadpoles_restored_test')
            except subprocess.CalledProcessError:
                pass
            else:
                raise AssertionError('Runtime could mutate '+table)
    assert sql("SELECT NOT has_table_privilege('recovery_runtime','operations_access_grants','INSERT')", 'tadpoles_restored_test') == 't'
    stop_api()
    token = start("tadpoles_restored_test", True, True, restricted=True)
    code, detail = api('/api/operations/orders/'+str(filled['orderId']), token=operations_token, context=context)
    assert code == 200 and detail['evidenceStatus'] == 'COMPLETE_FOR_INTERNAL_BOOKING', detail
    code, runtime_export = api('/api/operations/evidence-exports', {'orderId':filled['orderId']}, operations_token, str(uuid.uuid4()), context)
    assert code == 200, runtime_export
    code, ready = api('/api/operations/readiness', token=operations_token)
    assert code == 200 and ready['runtimePermissions']['evidenceProtected'] and ready['supportSchemaReady'], ready
    code, runtime_case = api('/api/support/cases',{'category':'ACCOUNT_REVIEW','subject':'Restricted runtime case','description':'Confirm support persists under the restricted runtime.'},token,str(uuid.uuid4()))
    assert code == 200, runtime_case
    code, runtime_reply = api('/api/support/cases/'+runtime_case['reference']+'/replies',{'expectedVersion':1,'body':'Additional public information after restore.'},token,str(uuid.uuid4()))
    assert code == 200 and runtime_reply['version'] == 2, runtime_reply
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
    print(f"Operations dump/restore: {restore_seconds:.3f}s; zero loss relative to the captured snapshot; export bytes identical after restore.")
    print(
        "PASS: durable cases/public-private history, recovered write receipts/unread watermarks, managed export associations; Operations scoped reads/exports, audit/grant restoration, restricted evidence permissions; HTTP-provider buy, backup/restore, deposit replay after restore, admission halt/drain, restart, non-owner booking, sell-all, withdrawal/replay, immutable evidence grants, repeated migrations, and persisted zero-difference reconciliation."
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
