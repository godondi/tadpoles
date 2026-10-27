#!/usr/bin/env python3
"""Exercise standard Operations startup with an explicit test market provider in an isolated Compose project."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent.parent
PROJECT = 'tadpoles-operations-demo-check-' + str(os.getpid())
PORT = 18085


def api(path, token=None, body=None, context=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if context:
        headers['X-Operations-Context'] = context
    if key:
        headers['Idempotency-Key'] = key
    request = urllib.request.Request(
        f'http://127.0.0.1:{PORT}' + path,
        data=json.dumps(body).encode() if body is not None else None,
        headers=headers,
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


with tempfile.TemporaryDirectory(prefix=PROJECT) as directory:
    directory = Path(directory)
    settings = directory / 'test.env'
    # All values are explicit test fixtures; never load the developer's .env.
    settings.write_text('''BACKEND_HOST_PORT=18085
BACKEND_CONTAINER_PORT=8080
SERVER_PORT=8080
DB_URL=jdbc:postgresql://database:5432/tadpoles_isolated_test
DB_USERNAME=test
DB_PASSWORD=test
POSTGRES_USER=test
POSTGRES_PASSWORD=test
POSTGRES_DB=tadpoles_isolated_test
POSTGRES_HOST_PORT=18084
JWT_SECRET=operations-demo-fixture-secret-at-least-32-bytes
AUTH_STUB_URL=http://auth-stub:4000
AUTH_STUB_PORT=4000
AUTH_STUB_HOST_PORT=18081
AUTH_STUB_CONTAINER_PORT=4000
FRONTEND_HOST_PORT=18086
FRONTEND_CONTAINER_PORT=4000
ANALYTICS_POSTGRES_HOST_PORT=18088
ANALYTICS_HOST_PORT=18089
ANALYTICS_CONTAINER_PORT=8000
''')
    override = directory / 'isolation.yml'
    lines = ['services:']
    for service in ['backend', 'database', 'database-migrate', 'database-demo-seed',
                    'market-fixture', 'frontend']:
        lines += [f'  {service}:', f'    container_name: {PROJECT}-{service}',
                  '    env_file: !reset []']
        if service in ['database', 'frontend']:
            lines += ['    ports: !reset []']
        if service == 'backend':
            lines += [f'    image: {PROJECT}-backend:test',
                      '    environment:', '      SERVER_PORT: "8080"']
    for service in ['analytics-database', 'analytics-api', 'analytics-demo-seed', 'nginx']:
        lines += [f'  {service}:', '    profiles: ["not-under-test"]',
                  '    env_file: !reset []', '    ports: !reset []']
    override.write_text('\n'.join(lines) + '\n')
    command = ['docker', 'compose', '--env-file', str(settings), '-p', PROJECT]
    for file in [ROOT / 'docker-compose.yml', ROOT / 'docker-compose.market-fixture.yml', override]:
        command += ['-f', str(file)]

    def compose(*args):
        # Detailed build output stays in a diagnostic log, away from application secrets.
        log = directory / 'compose-build.log'
        try:
            with open(log, 'ab') as output:
                subprocess.run(command + list(args), stdout=output, stderr=subprocess.STDOUT, check=True)
        except subprocess.CalledProcessError as failure:
            raise RuntimeError(log.read_text()[-4000:]) from failure

    def sql(statement):
        return subprocess.check_output(
            ['docker', 'exec', '-i', PROJECT + '-database', 'psql', '-U', 'test',
             '-d', 'tadpoles_isolated_test', '-v', 'ON_ERROR_STOP=1', '-At'],
            input=statement.encode(), stderr=subprocess.STDOUT,
        ).decode().strip()

    def login(username):
        status, result = api('/auth/login', body={'username': username, 'password': 'RoleDemo2026!'})
        assert status == 200, result
        return result['token']

    try:
        compose('config', '--quiet')
        compose('build', 'backend')
        compose('up', '-d', 'backend')
        for attempt in range(90):
            try:
                operations = login('demo.operations')
                break
            except (OSError, ValueError, AssertionError):
                time.sleep(1)
        else:
            raise AssertionError('Demo backend did not become ready')
        for attempt in range(60):
            if sql('SELECT count(*) FROM instrument_trading_policies') == '14':
                break
            time.sleep(1)
        assert sql('SELECT count(*) FROM instrument_trading_policies') == '14'
        assert sql('SELECT count(*) FROM operations_access_grants') == '9'
        sql((ROOT / 'database/demo/operations_workspace.sql').read_text())
        assert sql('SELECT count(*) FROM operations_access_grants') == '9', 'Seed must be repeatable'
        assert sql("SELECT count(*) FROM operations_access_grants g JOIN clients c USING(client_id) "
                   "JOIN users u ON u.user_id=c.user_id WHERE u.username<>'demo.client' AND g.permission NOT IN ('CASE_READ','CASE_UPDATE')") == '0'
        references = []
        for owner in ('demo.client', 'demo.client2'):
            owner_token = login(owner)
            for category in ('ORDER_QUESTION', 'ACCOUNT_REVIEW', 'HISTORICAL_RECORD', 'TRADING_AVAILABILITY', 'OTHER'):
                status, case = api('/api/support/cases', owner_token, {
                    'category': category, 'subject': 'Default queue ' + category,
                    'description': 'This report must appear without searching by category.'
                }, key=str(uuid.uuid4()))
                assert status == 200, case
                references.append(case['reference'])
        status, queue = api('/api/operations/cases', operations)
        assert status == 200 and queue['total'] == 10, queue
        assert {case['reference'] for case in queue['items']} == set(references), queue
        assert {case['category'] for case in queue['items']} == {
            'ORDER_QUESTION', 'ACCOUNT_REVIEW', 'HISTORICAL_RECORD', 'TRADING_AVAILABILITY', 'OTHER'
        }, queue
        status, reply = api('/api/operations/cases/' + references[-1] + '/replies', operations,
                            {'expectedVersion': 1, 'body': 'This case is available through standard local startup.'},
                            key=str(uuid.uuid4()))
        assert status == 200, reply
        status, policy = api('/api/operations/access-policy', operations)
        assert status == 200 and policy['configured'] and policy['purposes'] == ['ORDER_REVIEW'], policy
        status, check = api('/api/workspace/operations/instruments/test?symbol=SPY', operations)
        assert status == 200 and check['recognized'] and check['chartAvailable'], check
        assert 'fixture' in check['source'].lower(), check
        status, context = api('/api/operations/access-contexts', operations,
                              {'purposeCode': 'ORDER_REVIEW', 'referenceType': 'EXTERNAL', 'caseReference': 'DEMO-SMOKE-001'})
        assert status == 200, context
        context = context['contextId']
        client = login('demo.client')
        instrument = int(sql("SELECT instrument_id FROM instruments WHERE ticker='AAPL'"))
        status, order = api('/api/orders', client,
                            {'instrumentId': instrument, 'side': 'BUY', 'quantity': '1', 'orderType': 'MARKET'},
                            key=str(uuid.uuid4()))
        assert status == 202, order
        for attempt in range(30):
            status, detail = api('/api/operations/orders/' + str(order['orderId']), operations, context=context)
            if status == 200 and detail['order']['status'] == 'FILLED':
                break
            time.sleep(1)
        assert status == 200 and detail['evidenceStatus'] == 'COMPLETE_FOR_INTERNAL_BOOKING', detail
        assert detail['order']['accountReference'] == 'DEMO-CLIENT'
        status, receipt = api('/api/operations/evidence-exports', operations,
                              {'orderId': order['orderId']}, context, str(uuid.uuid4()))
        assert status == 200 and receipt['state'] == 'READY', receipt
        sql("UPDATE operations_access_grants SET revoked_at=now() WHERE permission='ORDER_READ'")
        sql((ROOT / 'database/demo/operations_workspace.sql').read_text())
        assert sql("SELECT count(*) FROM operations_access_grants WHERE permission='ORDER_READ'") == '1', 'Seed must honor revocation'
        sql("UPDATE operations_access_grants SET revoked_at=now() WHERE permission='CASE_READ' AND client_id IN "
            "(SELECT client_id FROM clients JOIN users USING(user_id) WHERE username='demo.client2')")
        sql((ROOT / 'database/demo/operations_workspace.sql').read_text())
        assert sql('SELECT count(*) FROM operations_access_grants') == '9', 'Seed must honor case revocation'
        status, denied = api('/api/operations/cases/' + references[-1], operations)
        assert status == 404, denied
        print('PASS: standard local Operations startup, 14 validated fixture tickers, SPY quote/chart test, '
              'nine repeatable scoped grants, all-category cases from both local clients without search, public reply, purpose context, scoped filled order/export, '
              'and retained grant revocation.')
    finally:
        compose('down', '--volumes', '--remove-orphans')
