#!/usr/bin/env python3
"""Read-only hardened Compose contract check; never starts services or prints resolved secrets."""
import json, os, pathlib, subprocess

root = pathlib.Path(__file__).resolve().parent.parent
env = {
    **os.environ,
    "TRADING_RUNTIME_DB_USER": "test_runtime",
    "TRADING_RUNTIME_DB_PASSWORD": "test-only",
    "BANKING_ENCRYPTION_KEY": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
}
result = subprocess.check_output(
    [
        "docker",
        "compose",
        "--env-file",
        str(root / ".env.template"),
        "-f",
        str(root / "docker-compose.yml"),
        "-f",
        str(root / "docker-compose.hardened.yml"),
        "config",
        # This contract check needs declarations, not a developer's private .env.
        "--no-env-resolution",
        "--format",
        "json",
    ],
    env=env,
)
services = json.loads(result)["services"]
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
print(
    "PASS: hardened startup excludes demo seeding/auth stub and uses the separately provisioned runtime identity."
)
