#!/usr/bin/env python3
"""Read-only hardened Compose contract check; never starts services or prints resolved secrets."""
import json, os, pathlib, subprocess, tempfile

root = pathlib.Path(__file__).resolve().parent.parent
policy_prefixes = ("OPERATIONS_", "COMPLIANCE_", "SUPPORT_WORKFLOW_")
env = {
    **{key: value for key, value in os.environ.items() if not key.startswith(policy_prefixes)},
    "TRADING_RUNTIME_DB_USER": "test_runtime",
    "TRADING_RUNTIME_DB_PASSWORD": "test-only",
    "BANKING_ENCRYPTION_KEY": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
}
approved_policy = {
    "COMPLIANCE_ACCESS_ENABLED": "true",
    "COMPLIANCE_ALLOWED_PURPOSES": "CASE_REVIEW",
    "COMPLIANCE_POLICY_VERSION": "approved-fixture-v1",
    "COMPLIANCE_CONTEXT_SECONDS": "600",
    "COMPLIANCE_EXPORT_SECONDS": "600",
}
with tempfile.TemporaryDirectory(prefix="tadpoles-hardened-config-") as directory:
    settings = pathlib.Path(directory) / "test.env"
    settings.write_text("\n".join(
        line for line in (root / ".env.template").read_text().splitlines()
        if not line.strip().startswith(policy_prefixes)
    ) + "\n")
    command = [
        "docker",
        "compose",
        "--env-file",
        str(settings),
        "-f",
        str(root / "docker-compose.yml"),
        "-f",
        str(root / "docker-compose.hardened.yml"),
        "config",
        # This contract check needs declarations, not a developer's private .env.
        "--no-env-resolution",
        "--format",
        "json",
    ]
    services = json.loads(subprocess.check_output(command, env=env))["services"]
    approved = json.loads(subprocess.check_output(command, env={**env, **approved_policy}))["services"]
backend = services["backend"]
assert not backend.get("env_file"), "Hardened backend must not inherit owner credentials from .env"
assert (
    "database-demo-seed" not in backend["depends_on"]
), "Hardened startup must not depend on local demo seeding"
assert "database-demo-seed" not in services or services["database-demo-seed"].get(
    "profiles"
), "Demo accounts must require an explicit local profile"
assert "auth-stub" not in services or services["auth-stub"].get(
    "profiles"
), "Development auth stub must require an explicit local profile"
assert backend["environment"]["TRADING_LOCAL_DEMO_SETUP"] == "false"
assert backend["environment"]["DB_USERNAME"] == "test_runtime"
assert backend["environment"]["OPERATIONS_ACCESS_ENABLED"] == "false"
assert backend["environment"]["SUPPORT_WORKFLOW_ENABLED"] == "false"
assert backend["environment"]["OPERATIONS_ALLOWED_PURPOSES"] == ""
assert backend["environment"]["OPERATIONS_POLICY_VERSION"] == ""
assert backend["environment"]["OPERATIONS_CONTEXT_SECONDS"] == "0"
assert backend["environment"]["OPERATIONS_EXPORT_SECONDS"] == "0"
assert backend["environment"]["COMPLIANCE_ACCESS_ENABLED"] == "false"
assert backend["environment"]["COMPLIANCE_ALLOWED_PURPOSES"] == ""
assert backend["environment"]["COMPLIANCE_POLICY_VERSION"] == ""
assert backend["environment"]["COMPLIANCE_CONTEXT_SECONDS"] == "0"
assert backend["environment"]["COMPLIANCE_EXPORT_SECONDS"] == "0"
for key, value in approved_policy.items():
    assert approved["backend"]["environment"][key] == value
print(
    "PASS: hardened startup excludes demo seeding/auth stub, uses the runtime identity, defaults both role policies/workflow closed, and preserves explicit approved Compliance policy."
)
