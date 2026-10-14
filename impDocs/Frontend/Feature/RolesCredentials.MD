# Local role credentials for Compose debugging

Run `docker compose up --build`, then open the login page at `http://localhost:8086/login` with the default `.env.template` ports. The Compose `database-demo-seed` service creates these accounts in the Compose PostgreSQL database before the backend starts. Sign in with the **username** shown below. Every account uses the password **`RoleDemo2026!`**.

| Username | Role | Workspace to inspect |
| --- | --- | --- |
| `demo.client` | CLIENT | `/app/overview` |
| `demo.client2` | CLIENT | `/app/overview` (separate client for access checks) |
| `demo.operations` | TRADING_OPS | `/operations/orders` |
| `demo.compliance` | COMPLIANCE | `/compliance/investigations` |
| `demo.analyst` | ANALYST | `/analytics/overview` |
| `demo.finance` | FINANCE | `/finance/overview` |

The seed is [`database/demo/role_workspaces.sql`](../../../database/demo/role_workspaces.sql). Passwords are stored as BCrypt hashes. Re-running the seed leaves existing passwords, user records, and role assignments intact. It also gives each demo client a zero-balance client record and a completed onboarding profile, so the client workspace opens without signup or onboarding. No trades, holdings, or financial results are fabricated.

The staff pages currently show their configured unavailable states where investigation policy, reporting data, or reconciliation rules have not been supplied. These credentials let you inspect the role-specific navigation, routes, and access controls; they do not enable those unfinished business features. `ADMIN`, `AUDITOR`, `ADVISOR`, and other legacy database roles have no separate frontend workspace in the current app.

## Existing Compose database

If the database volume predates this change, `docker compose up --build` still runs the seed service against it. To apply it manually without restarting the app, run:

```bash
docker compose run --rm database-demo-seed
```

The seed uses reserved `demo.*` usernames and only inserts missing records. If a demo account already exists with a different password, the seed will not reset it. These shared credentials are for local development only; use different provisioning for deployed environments.
