# Client Ownership Enforcement

## What changed

Added ownership checks so authenticated `CLIENT` users can only view or act on records tied to their own `clientId`.

The enforced surfaces are:

- `GET /api/clients/{clientId}`
- `PATCH /api/clients/{clientId}`
- `GET /api/clients/{clientId}/balance`
- all holdings endpoints under `/api/clients/{clientId}/holdings`
- all trade endpoints under `/api/clients/{clientId}/trades`
- `GET /api/clients/{clientId}/subscriptions`
- client-facing trade suggestion endpoints:
  - `GET /api/clients/{clientId}/trade-suggestions`
  - `GET /api/trade-suggestions/{suggestionId}`
  - `PATCH /api/trade-suggestions/{suggestionId}`

## How it was implemented

I kept the current controller + shared-helper pattern and extended `SecurityRoleSupport` instead of introducing a separate authorization layer.

`SecurityRoleSupport` now:

- resolves the authenticated `AppUser` from the JWT
- detects whether the caller has the `CLIENT` role
- rejects access with `403 Forbidden` when a client tries to access another client’s records

Each client-scoped controller now calls the shared helper before invoking its service method. Non-client roles keep their existing access patterns; the new restriction is specifically applied to `CLIENT` users.

## Refactor

`AppUserController` now reuses the shared JWT-to-user resolution logic from `SecurityRoleSupport` instead of maintaining its own duplicate implementation.

## Test coverage

Added controller tests first for:

- client access to their own holdings, trades, subscriptions, and trade suggestions
- `403` denial when a client requests another client’s records
- existing admin/advisor/analyst behavior staying intact where applicable

## API contract updates

Updated `docs/api.yaml` to document the new `403 Forbidden` responses on the affected client-owned routes.
