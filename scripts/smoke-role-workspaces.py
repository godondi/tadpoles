#!/usr/bin/env python3
"""Actual HTTP checks against the optional local role demo. Never logs access tokens."""
import json
import urllib.request
import urllib.error

BASE = 'http://127.0.0.1:8082'


def request(path, token=None, method='GET', payload=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    body = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def login(name):
    status, data = request('/auth/login', method='POST', payload={'username': name, 'password': 'RoleDemo2026!'})
    assert status == 200, (name, status)
    return data['token']


def main():
    checks = 0
    client_tokens = [login(name) for name in ['demo.client', 'demo.client2']]
    identities = [request('/api/users/me', token)[1] for token in client_tokens]
    assert identities[0]['clientId'] != identities[1]['clientId']
    for index, token in enumerate(client_tokens):
        own = identities[index]['clientId']
        other = identities[1-index]['clientId']
        assert request(f'/api/clients/{own}/balance', token)[0] == 200
        assert request(f'/api/clients/{other}/balance', token)[0] == 403
        assert request('/api/orders', token, 'POST', {})[0] == 503
        assert request('/api/orders/2147483647', token)[0] == 404
        assert request('/api/orders?page=-1', token)[0] == 400
        assert request('/api/orders?from=2026-10-13&to=2026-01-01', token)[0] == 400
        assert request('/api/orders', token)[0] == 200
        checks += 7
    for name, role, path in [
        ('demo.operations', 'TRADING_OPS', '/api/operations/orders'),
        ('demo.compliance', 'COMPLIANCE', '/api/compliance/investigations'),
        ('demo.analyst', 'ANALYST', '/api/analytics/summary'),
        ('demo.finance', 'FINANCE', '/api/finance/summary'),
    ]:
        token = login(name)
        status, identity = request('/api/users/me', token)
        assert status == 200 and identity['roles'] == [role]
        assert request(path, token)[0] == 503
        for denied in ['/api/orders', '/api/clients/1/balance', '/api/clients/1/trades', '/api/users/me/profile']:
            assert request(denied, token)[0] == 403
        assert request('/api/clients/1/trades/1/fill', token, 'POST', {})[0] == 403
        assert request('/api/clients/1', token, 'PATCH', {'cashBalance': 999})[0] == 403
        checks += 8
        print(f'{role}: correct identity, truthful unavailable response, and direct API denials passed')
    print(f'{checks} actual HTTP assertions passed; both clients isolated; trading disabled.')


if __name__ == '__main__':
    main()
