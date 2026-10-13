# Separate Pages Feature Guide

## Overview

The application now provides role-specific workspaces. After authentication, the backend identity is loaded from `/api/users/me`, and the frontend sends each user to the workspace permitted by their assigned role.

## Getting started

1. Start the backend with its required environment variables, including the database connection and JWT secret.
2. Start the Angular frontend from the repository root:

   ```powershell
   npm --prefix frontend start
   ```

3. Open the local frontend URL shown by Angular, normally `http://localhost:4200`.
4. Sign in at `/login` using an account seeded or created for one of the supported roles.

## Workspace entry points

| Role | Default page | Available area |
| --- | --- | --- |
| `CLIENT` | `/app/overview` | Portfolio, markets, instruments, orders, and trading |
| `TRADING_OPS` | `/operations/orders` | Operations order monitoring and order details |
| `OPERATIONS` | `/operations/orders` | Legacy alias for `TRADING_OPS` |
| `COMPLIANCE` | `/compliance/investigations` | Investigation and order-evidence views |
| `ANALYST` | `/analytics/overview` | Analytics workspace |
| `REPORTING` | `/analytics/overview` | Legacy alias for `ANALYST` |
| `FINANCE` | `/finance/overview` | Finance and reconciliation workspace |

Users without a permitted workspace are sent to `/unauthorized`. Unknown URLs are handled by `/not-found`.

## Client workflow

After signing in as a `CLIENT`:

- Use the top navigation to open Overview, Markets, Portfolio, or Orders.
- Search for stocks and ETFs from the client navigation.
- Open an instrument to view details and use the trading page to place an order.
- Review submitted orders at `/app/orders` and open an order for its details.
- Use the profile menu for account details, onboarding, and logout.

Client account, holding, and trade requests are ownership-checked by the backend. A client cannot use a URL or request for another client’s records.

## Staff workflows

Operations users can monitor orders through `/operations/orders` and open individual order details. Compliance users can review investigations and supporting order evidence through `/compliance/investigations`.

Analytics and Finance routes are available and role-protected. Their current pages explicitly indicate when reporting or reconciliation data is unavailable because those dedicated backend data sources have not yet been added.

## Troubleshooting

- If the page redirects to `/login`, the access token is missing or expired; sign in again.
- If the page redirects to `/unauthorized`, the authenticated account does not have the role required by that route.
- If a client sees an ownership error, verify that the signed-in user is linked to the requested client account.
- If the backend does not start in tests, provide defaults or test values for `SERVER_PORT`, `JWT_SECRET`, and the database settings. The checked-in backend test configuration supplies defaults for the server and Fauxnance settings.
