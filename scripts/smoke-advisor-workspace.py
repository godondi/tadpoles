#!/usr/bin/env python3
"""Verify the optional demo workspace through nginx; tokens remain in process memory.

Read-only by default. --verify-demo-mutations creates/edits/archives one demo
recommendation through the actual API, leaving attributable archived test history.
Never modifies cash, positions, orders or another account's existing advice.
"""
import argparse
from decimal import Decimal
import json
import urllib.error
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8087")
    parser.add_argument("--verify-demo-mutations", action="store_true")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")

    def call(method, path, token=None, body=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(base + path, method=method, headers=headers,
                                         data=None if body is None else json.dumps(body).encode())
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                code, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            code, raw = error.code, error.read()
        assert code == expected, f"{method} {path}: expected HTTP {expected}, got {code}"
        return json.loads(raw) if raw else None

    def login(username):
        result = call("POST", "/auth/login", body={"username": username, "password": "RoleDemo2026!"})
        token = result["token"]
        identity = call("GET", "/api/users/me", token)
        assert identity["username"] == username
        return token, identity

    advisor, identity = login("demo.advisor")
    client, owner = login("demo.client")
    other, negative = login("demo.client2")
    assert "ADVISOR" in identity["roles"]
    client_id, negative_id = owner["clientId"], negative["clientId"]
    clients = call("GET", "/api/workspace/advisor/clients", advisor)
    assert {item["clientId"] for item in clients["items"]} == {client_id}, "Unexpected demo assignment set"
    root = f"/api/workspace/advisor/clients/{client_id}"
    portfolio = call("GET", root + "/portfolio", advisor)
    assert portfolio["clientId"] == client_id
    for item in portfolio["currentPositions"]:
        assert all(isinstance(item[key], str) for key in ("owned", "reserved", "available"))
        assert Decimal(item["owned"]) - Decimal(item["reserved"]) == Decimal(item["available"])
    call("GET", root + "/guidance", advisor)
    recommendations = call("GET", root + "/recommendations", advisor)
    catalog = call("GET", "/api/workspace/advisor/instruments?page=0&pageSize=100", advisor)
    assignment = call("GET", "/api/workspace/client/advisor", client)
    assert assignment["assignmentStatus"] == "ASSIGNED" and assignment["advisor"]["name"] == "Demo Advisor"
    call("GET", "/api/workspace/client/advisory", client)
    call("GET", "/api/workspace/client/advisory", other)
    call("GET", "/api/workspace/advisor/clients", expected=401)
    call("GET", "/api/workspace/advisor/clients", client, expected=403)
    for resource in ("portfolio", "guidance", "recommendations"):
        call("GET", f"/api/workspace/advisor/clients/{negative_id}/{resource}", advisor, expected=404)
    call("POST", f"/api/workspace/advisor/clients/{negative_id}/recommendations", advisor,
         {"instrumentId": 1, "reasoning": "Negative scope check"}, expected=404)
    call("POST", root + "/recommendations", client,
         {"instrumentId": 1, "reasoning": "Wrong-role check"}, expected=403)
    call("POST", root + "/recommendations", advisor,
         {"instrumentId": 1.5, "reasoning": "Invalid integer check"}, expected=400)
    print("PASS: real login, assigned clients, exact holdings, guidance, recommendations, catalog, client assignment/advisory, role/ownership negatives.")

    if args.verify_demo_mutations:
        held = {p["instrumentId"] for p in portfolio["currentPositions"] if Decimal(p["owned"]) > 0}
        existing = {r["instrumentId"] for r in recommendations["items"]}
        choices = [i for i in catalog["items"] if i["isActive"] and i["instrumentId"] not in held | existing]
        assert choices, "No unused eligible instrument on the bounded catalog page for a safe demo mutation"
        item = call("POST", root + "/recommendations", advisor,
                    {"instrumentId": choices[0]["instrumentId"], "reasoning": "Demo API verification; not a trade instruction."}, expected=201)
        archived = False
        try:
            item = call("PATCH", root + f"/recommendations/{item['recommendationId']}", advisor,
                        {"reasoning": "Demo API verification updated; not a trade instruction.", "expectedVersion": item["version"]})
            call("PATCH", root + f"/recommendations/{item['recommendationId']}", advisor,
                 {"reasoning": "Stale-version check", "expectedVersion": item["version"] - 1}, expected=409)
            result = call("DELETE", root + f"/recommendations/{item['recommendationId']}", advisor,
                          {"expectedVersion": item["version"]})
            assert result["version"] == item["version"] + 1
            archived = True
            assert item["recommendationId"] not in {r["recommendationId"] for r in call("GET", root + "/recommendations", advisor)["items"]}
            print("PASS: create/edit/version-conflict/archive with JSON DELETE through nginx; archived test history retained.")
        finally:
            if not archived:
                call("DELETE", root + f"/recommendations/{item['recommendationId']}", advisor,
                     {"expectedVersion": item["version"]})


if __name__ == "__main__":
    main()