# Analytics API and Frontend Page Implementation Guide

**Status:** implementation design and contract guide

**Date reviewed:** 2026-10-14

**Scope:** the read-only internal analytics workspace at `/analytics/*`, its Python FastAPI service, its separate reporting database, and the Angular client that consumes it.

This document is an implementation guide. It separates verified repository behavior from proposed work. The page may be demonstrated with an explicitly labelled empty state or fixture data, but it must not present fabricated values as live reporting results.

## 1. Outcome and architectural rule

The analytics credential should open a read-only business analytics page for a user with the `ANALYST` role. The page must support historical analysis by time, instrument, market, and an approved client segment while protecting the live trading system from analytical query load.

The controlling architectural rule is:

> The FastAPI analytics service reads only from a separate physical reporting database. It does not run reporting joins against the Spring transaction database, and it never becomes the authority for current orders, cash, holdings, acceptance, execution, or legal trade state.

The resulting request path is:

```mermaid
flowchart LR
    Browser[Angular analytics page] -->|JWT and filters| Api[FastAPI analytics API]
    Api -->|read-only SQLAlchemy| Report[(Separate reporting database)]
    Source[Spring transactional domain] -->|committed outbox or CDC| Load[Reporting loader]
    Load --> Report
    Api -->|status, data_as_of, definitions| Browser
    Browser -->|current account, orders, holdings| Spring[Spring API]
```

The browser route and the API route are different concerns:

- Browser routes remain `/analytics/overview`, `/analytics/trends`, and `/analytics/instruments`.
- The analytics API owns `/api/analytics/summary`, `/api/analytics/trends`, `/api/analytics/instruments`, and `/api/analytics/freshness`.
- In development the browser may call the FastAPI container directly on its host port.
- In production a reverse proxy may route `/api/analytics/*` to FastAPI on the same public origin. The existing `nginx/Dockerfile` is only a placeholder, so a proxy must be explicitly configured before relying on this mode.

## 2. Verified repository baseline

### 2.1 Frontend

The Angular application already has the analyst workspace policy and lazy routes:

| Browser route | Current route metadata | Current API path | Current component |
|---|---|---|---|
| `/analytics/overview` | Analytics overview | `GET /api/analytics/summary` | Generic `StaffPageComponent` |
| `/analytics/trends` | Activity trends | `GET /api/analytics/trends` | Generic `StaffPageComponent` |
| `/analytics/instruments` | Instrument analysis | `GET /api/analytics/instruments` | Generic `StaffPageComponent` |

The route policy in `frontend/src/app/core/auth/workspace-policy.ts` maps those paths to `ANALYST`. The parent `workspaceGuard` therefore controls navigation, but this is only a user-interface boundary. The API must enforce the role again.

`frontend/src/app/shared/workspace/staff-page.component.ts` currently renders a generic read-only unavailable page and calls `WorkspaceApi.staffAvailability()`. There is no analytics-specific Angular service, response model, filter state, chart model, or analytics component test.

`frontend/src/app/core/api/api-url.ts` currently targets the Spring backend on port `8082`. `AuthInterceptor` adds the bearer token only for that backend origin. A separate FastAPI origin therefore requires an explicit analytics URL and an interceptor trust update; otherwise a valid analyst token will not reach FastAPI.

Chart.js is already listed in `frontend/package.json`, so the page may use the existing dependency. Exact table values must remain available beside any chart.

### 2.2 Spring backend

`backend/src/main/java/com/neueda/leap/controller/StaffWorkspaceController.java` currently declares:

```text
GET /api/analytics/summary
GET /api/analytics/trends
GET /api/analytics/instruments
GET /api/analytics/freshness
```

Each method requires `ROLE_ANALYST` and always returns `503 SERVICE_UNAVAILABLE` with a message that the reporting source and approved metrics are not connected. This is an intentional fail-closed availability boundary, not an analytics implementation.

The Spring security configuration is stateless JWT security. Roles are read from the JWT `roles` claim and converted to `ROLE_<role>`. The FastAPI service must independently validate the same bearer token and require `ANALYST`; it must never trust a route guard or a client-supplied role parameter.

The OpenAPI file documents the four analytics paths as `availability-boundary-only`, with `401`, `403`, and `503` responses but no successful response contract. Its generic `AnalyticsSummaryResponse` is not referenced by those paths and must not be treated as an implemented schema.

### 2.3 Current endpoint inventory

These are the existing application endpoint groups relevant to ownership and integration. They remain Spring-owned unless explicitly migrated. The analytics service must not duplicate them.

| Area | Existing endpoints |
|---|---|
| Authentication | `POST /auth/login`, `POST /auth/signup`, `POST /auth/register`, `POST /auth/register/client` |
| Session | `POST /api/auth/refresh`, `POST /api/auth/logout` |
| Identity and roles | `GET /api/users/me`, `GET /api/users/me/profile`, `PUT /api/users/me/onboarding`, `GET /api/users`, `GET /api/users/{userId}`, `PATCH /api/users/{userId}`, `PUT /api/users/{userId}/roles`, `GET /api/roles`, `GET /api/roles/{roleId}` |
| Advisors and clients | `/api/advisors`, `/api/advisors/{advisorId}`, `/api/advisors/{advisorId}/clients`, `/api/clients`, `/api/clients/{clientId}`, `/api/clients/{clientId}/balance` |
| Client holdings and subscriptions | `/api/clients/{clientId}/holdings`, `/api/clients/{clientId}/holdings/{holdingId}`, `/api/clients/{clientId}/subscriptions` |
| Trades and suggestions | `/api/clients/{clientId}/trades`, `/api/clients/{clientId}/trades/{tradeId}`, `POST /api/clients/{clientId}/trades/{tradeId}/fill`, `/api/advisors/{advisorId}/clients/{clientId}/trade-suggestions`, `/api/clients/{clientId}/trade-suggestions`, `/api/trade-suggestions/{suggestionId}` |
| Instruments and portfolios | `/api/instruments`, `/api/instruments/{instrumentId}`, `/api/model-portfolios`, `/api/model-portfolios/{modelPortfolioId}`, `/api/model-portfolios/{modelPortfolioId}/holdings`, `/api/model-portfolios/{modelPortfolioId}/holdings/{instrumentId}` |
| Client workspace | `GET /api/workspace/client/portfolio`, `GET /api/orders`, `GET /api/orders/{id}`, `GET /api/workspace/client/instruments`, `GET /api/instruments/{id}`, `GET /api/workspace/client/trading-availability`, `POST /api/orders` |
| Market data | `GET /candles/{symbol}`, `GET /api/candles/{symbol}`, `GET /quotes/{symbol}`, `GET /api/quotes/{symbol}` |
| Other staff boundaries | `/api/operations/orders*`, `/api/compliance/investigations*`, `/api/compliance/evidence`, `/api/finance/summary`, `/api/finance/reconciliations` |
| Current analytics boundary | The four `GET /api/analytics/*` paths listed above, returning `503` from Spring |

The exact authorization on the non-analytics endpoints remains governed by their controllers and services. The analytics page must not use client holdings, client order search, audit evidence, or finance endpoints as an indirect reporting source.

### 2.4 Database and notebook reality

The transaction schema currently includes `client_trades`, `instruments`, `clients`, `client_holdings`, `audit_logs`, and related portfolio tables. It does not contain a reporting fact model, fill-event fact table, market dimension, approved segment history, reporting watermark, reconciliation run, or outbox table.

The existing `client_trades` table has `PENDING`, `APPROVED`, `REJECTED`, and `EXECUTED` statuses, a trade date, quantity, recorded price, and optional execution time. An `EXECUTED` trade row is not automatically a booked fill event. Do not report it as `fills_booked` without an approved semantic mapping and durable fill evidence.

`analytics/analytics.ipynb` is successfully executed exploratory work. It installs pandas, NumPy, Faker, seaborn, and matplotlib; generates fake clients, instruments, holdings, trades, and benchmark returns; and leaves database queries commented out. It contains no FastAPI application, SQLAlchemy model, authentication, reporting database, or deployment entry point. Its charts can inform a UI discussion only. Its random values, fake names, and benchmark results must never be loaded into an authoritative report.

## 3. Proposed service boundary

Create a new top-level service directory, for example:

```text
analytics-service/
  pyproject.toml
  Dockerfile
  app/
    __init__.py
    main.py
    config.py
    db.py
    auth.py
    schemas.py
    api/
      __init__.py
      analytics.py
    repositories/
      __init__.py
      analytics.py
    services/
      __init__.py
      reporting.py
  migrations/
  tests/
    conftest.py
    test_auth.py
    test_analytics_api.py
    test_analytics_queries.py
    test_reconciliation.py
```

Use FastAPI, Pydantic v2, SQLAlchemy 2.x, and a PostgreSQL driver such as `asyncpg` for the running service. Use PostgreSQL integration tests for production-shaped query behavior; an in-memory SQLite test must not be the only query test because numeric behavior, date grouping, PostgreSQL functions, and indexes can differ.

The API process is read-only against the reporting database. If ingestion is implemented in Python, run it as a separate worker or command with separate credentials. Do not give the serving process write credentials merely because the loader uses the same code package.

### 3.1 Authentication and authorization

For the current repository, FastAPI must:

1. Require `Authorization: Bearer <JWT>` on every analytics data endpoint.
2. Verify the HS256 signature with the shared `JWT_SECRET`, issuer and audience rules agreed with the Spring service, token expiry, and `sub`.
3. Read `roles` from the verified claims and require `ANALYST`.
4. Resolve segment entitlements from trusted configuration or an authorization service. Never accept `segment=all` as proof of entitlement.
5. Return `401` for a missing or invalid token and `403` for a valid token without the required role or segment scope.
6. Avoid logging raw tokens, client names, email addresses, or SQL containing protected values.

The current auth stub issues an HS256 token containing `sub` and `roles`, and the Spring service uses the same secret. That is suitable only for local demonstration. Production should move to an issuer/JWKS model or a managed identity provider so unrelated services do not share a static signing secret.

The browser route guard remains useful for navigation, but API authorization is the real security boundary. A user who calls FastAPI directly must receive the same denial as a user who bypasses Angular.

### 3.2 Reporting schema

Use stable, pseudonymous, versioned reporting data. A minimum first release needs the following logical entities:

| Entity | Required fields and purpose |
|---|---|
| `dim_instrument` | Instrument key, symbol, name or display label, market/MIC, asset class, base and quote currency, quantity precision, active dates |
| `dim_client_segment` | Stable pseudonymous segment key, approved label, effective dates, privacy policy version; no client name or contact fields |
| `fact_order_event` | Source order ID, event type, event time, received time, instrument key, segment key, status, source event ID, correction/reversal metadata |
| `fact_fill` | Stable fill ID, source order ID, fill event time, quantity, unit, price, notional, trade currency, instrument key, segment key, source event ID, correction/reversal metadata |
| `agg_activity_period` | Pre-aggregated period, market, asset class, instrument, segment where permitted, order counts, fill counts, quantities by unit, notionals by currency |
| `reporting_watermark` | Source stream, latest included event time, latest source event ID, last successful load, reconciliation status, definition version |
| `metric_definition` | Metric key, event date used, formula, unit/currency rule, version, effective dates, approval reference |

Do not use `users`, `client_profiles`, unrestricted `clients`, or raw `audit_logs` as an analytics response source. If a segment is too small, suppress it or roll it up according to an approved privacy rule. Suppression must be distinguishable from zero.

The loader must be idempotent. Use a unique source event ID and unique fill ID, upsert corrections deliberately, and preserve the source event and definition version used for every aggregate. A retry must not double count a fill.

### 3.3 Source publication and isolation

For authoritative BR-16 evidence, publish committed domain facts from the Spring transaction boundary using an outbox or CDC stream. The source transaction should write the accepted business event and its outbox record together. A loader then validates, masks, maps dimensions, and writes the reporting database.

A temporary read-only ETL from `client_trades` can demonstrate plumbing, but it cannot claim full BR-16/BR-17 compliance until accepted events, booked fills, corrections, market data, and segment policy are represented. If this temporary mode is used, label the page `Reporting data not connected` or `Demonstration data`, and label unsupported metrics `Definition pending` rather than returning zero.

The serving API must have:

- A reporting database URL only.
- A database role with `SELECT` access to approved reporting schemas and views.
- Statement timeouts and bounded result sizes.
- No Spring transaction database credentials.
- No write route, ad hoc SQL route, raw export route, or client-detail route.

## 4. Analytics HTTP contract

The existing `/api/analytics/*` paths are the least disruptive public contract. FastAPI should own those paths on the analytics service host. Keep the path names stable while the first real contract replaces the Spring `503` placeholder.

### 4.1 Common query parameters

All report endpoints should accept and validate:

| Parameter | Rule |
|---|---|
| `from` | Inclusive ISO date, required unless a documented default is approved |
| `to` | Inclusive ISO date, required and not before `from` |
| `timezone` | Explicit IANA timezone; do not use the browser's local timezone by accident |
| `granularity` | `day`, `week`, or `month`; use the approved reporting calendar |
| `market` | Optional allowlisted market or MIC |
| `asset_class` | Optional allowlisted asset class |
| `instrument` | Optional exact symbol or bounded search value |
| `segment` | Optional approved segment key, checked against the analyst entitlement |
| `measure` | Endpoint-specific allowlisted measure, never arbitrary SQL or a free-form column name |
| `page` and `page_size` | Required for detail/ranking endpoints or bounded with documented defaults |

The service should reject invalid dates, unknown granularity, excessive ranges, unapproved segments, and unsupported measures with `422`. A valid filter returning no rows is a successful `200` response with an explicit no-activity state, not an error and not a fabricated zero-filled series.

### 4.2 Common response metadata

Every successful report response should include metadata equivalent to:

```json
{
  "reporting_status": "CURRENT",
  "data_as_of": "2026-10-12T17:00:00Z",
  "last_successful_load_at": "2026-10-12T17:05:10Z",
  "period_start": "2026-10-01",
  "period_end": "2026-10-12",
  "reporting_timezone": "UTC",
  "definition_version": "2026-10-01",
  "source_watermark": "event-00001234",
  "warnings": []
}
```

The timestamps above are illustrative schema examples only. The service must obtain real values from reporting metadata and must not invent a load time. `data_as_of` means the latest source business event included in the result, not the time the HTTP request was made. When the pipeline is delayed, keep the last valid result and set `reporting_status` to `DATA_DELAYED` with a warning.

### 4.3 Summary

`GET /api/analytics/summary`

Return the selected filters, common metadata, and only metrics whose definitions are approved and supported by the reporting facts. The response may contain:

- `orders_submitted`, only if submission attempts and their event date are modelled.
- `orders_accepted`, only if acceptance is a separate recorded event.
- `orders_filled`, defined as distinct orders with a valid booked fill, not as a synonym for fill count.
- `fills_booked`, defined as distinct durable fill events.
- `active_clients`, as a count of pseudonymous clients under an approved activity rule.
- `executed_quantity`, grouped by instrument/unit rather than added across unlike units.
- `traded_notional`, grouped by currency unless a conversion basis is approved.

Use nullable or omitted metrics with a `definition_pending` explanation when the source cannot support them. A blank value must not silently mean zero.

### 4.4 Trends

`GET /api/analytics/trends`

Return a series of exact points:

```json
{
  "measure": "fills_booked",
  "unit": "count",
  "currency": null,
  "points": [
    {"period_start": "2026-10-01", "period_end": "2026-10-01", "value": 12}
  ]
}
```

The selected measure, unit, currency, event date, timezone, and definition version must be visible to the frontend. Notional series must not combine USD, GBP, INR, and other currencies without an approved conversion basis.

### 4.5 Instruments

`GET /api/analytics/instruments`

Return a bounded ranking or paginated table with symbol/display name, market, asset class, selected measure, unit, and currency where applicable. Keep fill count, filled-order count, and executed quantity as separate measures. A ranking by executed quantity must not be described as a ranking by active instruments.

### 4.6 Freshness

`GET /api/analytics/freshness`

Return the reporting state, latest source event included, last successful load, reconciliation watermark, definition version, and any data-quality exception summary. It must not expose internal credentials, SQL, backlog internals, or unrestricted client data.

Recommended states are `CURRENT`, `DATA_DELAYED`, `LOADING`, `PARTIAL`, and `UNAVAILABLE`.

### 4.7 Export

Export is a policy decision, not a default feature. Until approved, do not expose an export endpoint. If enabled later, use an endpoint such as `GET /api/analytics/export` that:

- Reuses the exact filter and authorization code path used by the screen.
- Exports only aggregates and pseudonymous segment keys.
- Includes generation time, `data_as_of`, reporting timezone, and definition version.
- Uses bounded row counts and an audit record.
- Never accepts a client ID, email, or arbitrary SQL expression.

### 4.8 Error contract

| HTTP status | Meaning | Frontend behavior |
|---|---|---|
| `401` | Missing, expired, malformed, or invalid JWT | Reauthenticate; do not display report values |
| `403` | No `ANALYST` role or unapproved segment scope | Show access denied without query details |
| `422` | Invalid or unsupported filters | Keep filters and show the validation message |
| `429` | Query rate or concurrency limit | Show retryable service message |
| `503` | Reporting source unavailable or not configured | Show unavailable/delayed state, never imply live trading is unavailable |
| `200` with empty rows | Valid query with no activity | Show "No activity for these filters." |

## 5. FastAPI implementation design

### 5.1 Application layers

Keep the same separation used by the rest of the repository: route handling should not contain SQL or business definitions.

1. `config.py` loads the reporting URL, JWT verification settings, allowed origins, query limits, and feature flags from environment variables.
2. `db.py` creates the SQLAlchemy engine/session and applies read-only transaction settings and statement timeouts.
3. `auth.py` verifies the bearer token and exposes a typed `CurrentAnalyst` dependency.
4. `schemas.py` contains Pydantic request/response models and rejects arbitrary fields.
5. `repositories/analytics.py` contains allowlisted SQLAlchemy queries against reporting views/tables.
6. `services/reporting.py` applies metric definitions, entitlement checks, suppression, metadata, and error mapping.
7. `api/analytics.py` maps the four routes to the service layer.
8. `main.py` registers routes, health checks, CORS, structured error handling, and request correlation.

Never build SQL by concatenating query parameters. Map each accepted `measure`, `sort`, `granularity`, and filter to a known expression. Use `Decimal` for quantities and notional values; do not convert financial values through binary floating-point arithmetic.

### 5.2 Query performance

The separate database is necessary but not sufficient. Use pre-aggregated period facts, indexes matching the filters, bounded date ranges, pagination, statement timeouts, and a maximum concurrent report query limit. Record query duration and result size as service telemetry without recording protected row contents.

The API should prefer a small number of predictable aggregate queries over one unrestricted join. Cache only responses whose authorization and definition version are part of the cache key. A cache must not leak a segment result to another entitlement.

### 5.3 Freshness behavior

The reporting service should read the watermark in the same logical snapshot as the aggregate result. If the last successful load is older than the approved threshold:

- Return the last valid aggregate if available.
- Mark the response `DATA_DELAYED`.
- Set `data_as_of` to the actual source watermark.
- Include the last successful load time and a plain-language warning.

If there is no valid result, return `503` with a safe error body. Do not turn an unavailable database into an empty report.

## 6. Docker and configuration changes

No application or database code is changed by this documentation-only update. During implementation, add only the configuration needed for the new ownership boundary.

Add an `analytics-api` container and a separate `analytics-database` container or separately managed reporting database. Example environment names are:

```text
ANALYTICS_HOST_PORT=8090
ANALYTICS_CONTAINER_PORT=8000
ANALYTICS_DATABASE_URL=postgresql+asyncpg://analytics_reader:...@analytics-database:5432/analytics_db
ANALYTICS_JWT_SECRET=...
ANALYTICS_ALLOWED_ORIGINS=http://localhost:8086,http://localhost:4200
ANALYTICS_QUERY_TIMEOUT_MS=5000
```

The real secret must come from the deployment secret mechanism. Do not commit credentials. The analytics API should depend on the reporting database health check, not on the transactional database being available for serving requests.

For local direct routing, configure Angular with an analytics base URL such as `http://localhost:8090`. For a same-origin production deployment, configure a reverse proxy route from `/api/analytics/` to the analytics container and use a relative base URL. CORS must allow only the actual frontend origins in direct mode.

Do not pass the Spring `DB_URL`, `DB_USERNAME`, or `DB_PASSWORD` into `analytics-api`. A read-only reporting role is a separate credential and should be denied `INSERT`, `UPDATE`, `DELETE`, and DDL.

## 7. Angular integration

### 7.1 API client

Add a dedicated `AnalyticsApiService` and typed models rather than extending the generic `staffAvailability()` method. The service should expose methods for summary, trends, instruments, freshness, and any future approved export.

Build query parameters from typed filter state. Encode dates and symbols, preserve the explicit timezone, and omit optional filters rather than sending empty or ambiguous values. Keep filters in the browser URL query string so a report can be reproduced and refreshed.

The API URL helper should support two trusted origins:

- The existing Spring backend origin for existing application endpoints.
- The configured analytics origin or same-origin analytics path for analytics endpoints.

Update `AuthInterceptor` so it adds the bearer token only to those configured trusted API origins. Never attach the token to arbitrary third-party URLs. Keep the current logout/session-change cancellation behavior for analytics requests.

### 7.2 Components and routes

Replace the three analytics uses of `StaffPageComponent` with an analytics page component or a small feature set:

- Overview renders status, filters, summary measures, and a ranked instrument preview.
- Trends renders the chart/table toggle and exact points.
- Instruments renders the ranked aggregate table with pagination and measure selection.

The route guard and `routeRole()` policy should remain `ANALYST`. Add a feature-specific component only after the API contract and error states are tested. Keep the browser paths unchanged so existing workspace links and landing behavior continue to work.

### 7.3 Required UI states

The page must distinguish:

- Loading: show a loading indicator without clearing known data into zeroes.
- No activity: show the selected filters and "No activity for these filters."
- Current: show `data_as_of` and the reporting timezone.
- Data delayed: keep last valid values visible with a prominent delayed label.
- Partial: identify affected metric or period.
- Unavailable: say historical reporting cannot be refreshed; do not imply trading is down.
- Access denied: do not reveal protected data or internal query details.
- Definition pending: show the metric as unavailable rather than guessing.

Every metric needs a unit, period, event date, definition text or definition version, and currency where relevant. Do not sum quantities from unlike instruments or notionals across currencies.

## 8. Spring and database changes that are actually justified

The first implementation should not make the Spring backend query the reporting database. That would create a second reporting path, reintroduce live-service coupling, and undermine the reason for FastAPI.

The justified Spring-side change is source publication if the current transaction model lacks a committed event stream:

1. Add an append-only outbox record in the same transaction as the accepted order or booked fill.
2. Include a stable event ID, event type, event time, source transaction reference, instrument, quantity, notional/currency, and correction metadata.
3. Publish or replicate the outbox to the reporting loader after commit.
4. Keep the transactional database authoritative for order, cash, holding, and fill state.

After FastAPI is serving the frontend and its security/contract tests pass, remove or deprecate the old Spring analytics placeholder routes. Update `StaffWorkspaceControllerTest` and `docs/api.yaml` at the same time. Do not silently leave two services owning the same public path with different data.

The current transaction schema also needs an approved fill-event model before `fills_booked`, `orders_filled`, or traded notional can be called authoritative. A status-only row is not enough for BR-09, BR-14, BR-15, or BR-17 evidence.

## 9. TDD and regression procedure

Implement in red-green-refactor slices. Each slice must have a failing test that expresses the contract before the smallest code change that makes it pass.

### 9.1 Test-first slices

1. **Contract red:** define Pydantic models and API examples; test required filters, invalid dates, unsupported measures, metadata, and no-activity responses.
2. **Auth red:** test no token, invalid signature, expired token, valid non-analyst token, valid analyst token, and segment entitlement.
3. **Query red:** load deterministic reporting fixtures and test counts, distinct order versus fill semantics, currency separation, timezone grouping, corrections, and suppression.
4. **Freshness red:** test current, delayed, partial, unavailable, and no-watermark states. Verify no fabricated timestamp or zero result.
5. **HTTP red:** test the FastAPI routes and status codes with dependency overrides and a real PostgreSQL integration path.
6. **Frontend red:** test route access, query parameter construction, bearer token routing, response rendering, chart/table toggle, and each failure state with `HttpTestingController`.
7. **Integration red:** run the API, reporting database, frontend, and auth fixture through Compose; verify an analyst can load the page and another role receives `403`.
8. **Isolation red:** run heavy reporting queries concurrently with the trading workload and measure the agreed live-order latency/error criteria.

### 9.2 If a test or regression goes in the wrong direction

Do not weaken a failing test merely to restore green.

1. Stop at the first failure and keep the failure output, request fixture, token claims, query filters, and database watermark.
2. Classify the failure as contract, auth, data definition, query, frontend state, routing, container, or performance.
3. Compare the smallest recent diff with the last known green test. Revert only the change owned by this implementation if it is clearly the cause; do not reset unrelated user work.
4. Restore the last green slice, then add a focused regression test that captures the failure.
5. If the requirement or metric definition changed, update the API contract and approval record first. Then update the test and implementation together.
6. Rerun the same narrow test until green, then the neighboring feature tests, then the full regression suite.
7. Record the failure and resolution in `impDocs/Frontend/AnalyticsPageImplementation.MD` so the implementation history explains why the final behavior exists.

### 9.3 Suggested validation commands

Use the repository's existing commands plus the new service commands:

```text
cd analytics-service && pytest -q
cd analytics-service && ruff check .
cd frontend && npm test
cd frontend && npm run build
cd backend && mvn -B test
cd testing && mvn -B test
docker compose config
python scripts/smoke-role-workspaces.py
```

Use the exact project-specific test flags if the local Angular or Docker setup requires them. A passing unit suite is not enough for BR-16; include a reporting-versus-transaction reconciliation check and a concurrent-load result.

## 10. Requirements traceability

| Requirement | How this design reinforces it | Evidence required |
|---|---|---|
| BR-01 to BR-03 | FastAPI validates the same authenticated session independently; route access does not grant API access | Token, expiry, logout, role, and direct-call tests |
| BR-02 | No client-owned transactional data or unrestricted client records are exposed | Response schema review, PII scan, non-analyst and direct-ID negative tests |
| BR-04 to BR-11 | Analytics is downstream and read-only; it cannot accept orders or alter cash, holdings, or order state | Route inventory and database permission tests |
| BR-12 to BR-13 | Instrument and currency dimensions are explicit; analytics does not turn indicative prices into fills | Dimension and metric-definition tests |
| BR-14 to BR-15 | Source event IDs, fill IDs, correction metadata, and definition versions preserve traceability without making the dashboard the legal record | Event lineage and reconstruction tests |
| BR-16 | Separate physical database, committed event publication, bounded reporting queries, watermarks, and concurrent-load evidence isolate analytics from live trading | Load test, query-plan review, reconciliation result, freshness result |
| BR-17 | Summary, trends, instrument rankings, exact tables, units, currency handling, approved segments, and definitions provide useful internal insight | Stakeholder acceptance against agreed metric definitions |
| BR-18 | The analytics page is not a trading-impact preview; any future added capability must be separately approved and must not make this read-only service transactional | Scope review |

## 11. Definition and policy decisions required

Before the page is called authoritative, obtain written decisions for:

- Meaning and event date of submitted, accepted, filled, and booked-fill counts.
- Whether pre-acceptance attempts are retained for reporting.
- Supported markets, asset classes, instrument identifiers, and reporting calendar.
- Segment taxonomy, history, entitlement, and small-cohort suppression.
- Quantity units and cross-currency notional conversion.
- Late arrivals, corrections, reversals, fees, and duplicate source events.
- Current/delayed thresholds and the reconciliation watermark shown to analysts.
- Export permission, row limits, retention, and audit requirements.
- Production JWT issuer, audience, JWKS, and service-to-service trust model.

Until these decisions are approved, the page must remain explicitly provisional and must not imply that its figures establish current trading or legal state.

## 12. Implementation status for this change

The first provisional implementation slice is now present. `analytics-service/` provides the FastAPI boundary, independent analyst JWT validation, typed route contract, health checks, and explicit unavailable responses. The separate reporting migration and Compose services establish the intended database isolation. Angular now loads an analytics-specific workspace with URL-backed filters, route tabs, freshness metadata, and honest unavailable/no-value states. Spring no longer owns the analytics placeholder paths.

The implementation is not authoritative yet. The committed source event publication, idempotent loader, approved metric definitions, segment policy, reporting fixtures, populated query repository, and full integration/performance evidence remain future steps. The UI assessment and rationale are recorded in `impDocs/Frontend/AnalyticsPage/ImpResult.MD`.