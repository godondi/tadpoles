# Analytics Visualization Commentary

**Date:** 2026-10-20  
**Scope:** review and implementation of the analytics workspace visualizations.

## Executive Summary

The repository already contains a provisional analytics boundary:

- Angular owns `/analytics/overview`, `/analytics/trends`, and `/analytics/instruments`.
- The Angular routes require the `ANALYST` role.
- FastAPI owns the read-only analytics API namespace and independently validates the analyst JWT.
- Spring owns client portfolio, holdings, order, and live transaction workflows.
- The default local Compose mode serves deterministic, explicitly labelled demonstration data. Authoritative mode still returns `503 SERVICE_UNAVAILABLE` because no approved reporting source is connected.

The new visualization slice adds the UI and API contracts without weakening that boundary. The page now has:

1. A portfolio performance line-chart contract comparing the portfolio with S&P 500, Dow Jones Industrial Average, Nasdaq-100, and Russell 2000.
2. Explicit `1m`, `3m`, and `12m` period controls.
3. A holdings doughnut-chart contract that displays the five largest holdings and rolls the remainder into `Other`.
4. Exact-value tables beside each chart for accessibility and auditability.
5. Explicit demonstration labeling for local fixtures and unavailable behavior until approved reporting data exists.

No notebook values, hard-coded portfolio values, Spring transactional reads, or client-owned holdings were reused as analytics truth.

## Review Findings

### What was already implemented

- `frontend/src/app/features/analytics/analytics-page.component.ts` provided the analytics shell, URL-backed filters, route tabs, freshness messaging, and explicit unavailable/no-activity states.
- `frontend/src/app/core/api/analytics-api.service.ts` provided typed clients for summary, trends, instruments, and freshness.
- `analytics-service/app/auth.py` independently checked bearer tokens and required `ANALYST`.
- `analytics-service/app/api.py` exposes bounded read-only reporting routes; local demo mode serves reporting fixtures while authoritative mode fails closed.
- `analytics-service/app/schemas.py` described the original aggregate-report response shapes.
- `backend/src/main/java/com/neueda/leap/controller/WorkspaceClientController.java` exposed `/api/workspace/client/portfolio` and `/api/orders` for client workflows. These remain Spring-owned and are not valid sources for internal aggregate analytics.
- The existing dashboard charts in `frontend/src/app/dashboard.ts` use placeholder values. They are client-facing prototype content and were not copied into the analytics page.

### What was missing

- No analytics response type represented portfolio-vs-benchmark performance.
- No analytics response type represented a holdings allocation snapshot.
- No approved source modeled benchmark history or current portfolio market value.
- The analytics page had no Chart.js lifecycle management.
- There was no test asserting the new visualization URLs and period parameter.
- There was no test asserting the new FastAPI routes preserve the `503` boundary.

## Implementation Decisions

### 1. Keep reporting ownership separate

The new API paths are:

- `GET /api/analytics/portfolio-performance`
- `GET /api/analytics/holdings`

They are protected by the same FastAPI `ANALYST` dependency as the existing analytics routes. Local demo mode returns fixed pseudonymous fixtures with `data_mode=DEMONSTRATION`; authoritative mode returns the explicit unavailable response until the reporting source exists. Returning zeros, notebook data, or live client data would falsely imply that the report is authoritative.

### 2. Normalize performance for comparison

The performance contract uses points shaped as:

```json
{
  "date": "2026-10-01",
  "values": {
    "portfolio": 100.0,
    "sp500": 100.0,
    "dow_jones": 100.0,
    "nasdaq_100": 100.0,
    "russell_2000": 100.0
  }
}
```

The values are expected to be indexed to 100 at the start of the selected period. This makes percentage performance comparable without pretending that the portfolio and indexes share the same absolute currency value. The source loader must calculate the index and document the pricing, valuation, timezone, and dividend/corporate-action policy.

### 3. Keep all four requested benchmarks explicit

The API and UI use stable keys:

| Key | Display label |
|---|---|
| `sp500` | S&P 500 |
| `dow_jones` | Dow Jones Industrial Average |
| `nasdaq_100` | Nasdaq-100 |
| `russell_2000` | Russell 2000 |

A missing benchmark point stays missing. The chart does not silently substitute another index or fill a value with zero.

### 4. Make the holdings rule deterministic

The frontend sorts holdings by numeric market value, retains the first five, and sums every remaining holding into a single `Other` row. The `Other` row also sums the component percentages. The reporting source should ideally perform this aggregation server-side for large portfolios, but the frontend rule ensures consistent display for a bounded response and keeps the chart semantics visible in tests.

The chart uses market value for slice size and the adjacent table shows holding name, market value, currency, and percentage. Holdings with unlike currencies must not be combined by the server unless an approved conversion basis exists.

### 5. Preserve exact values beside visualizations

Canvas charts are visual aids, not the only representation. Each visualization includes a table with exact values. This supports keyboard users, screen readers, narrow screens, and investigation of individual points.

### 6. Use Chart.js consistently

Chart.js is already a frontend dependency and is used by the existing dashboard. The analytics component registers only the controllers it needs, destroys chart instances before recreation, and destroys them on component teardown. Browser checks prevent canvas work during server-side rendering.

## Endpoint Inventory Reviewed

### Analytics API

| Endpoint | Owner | Current behavior |
|---|---|---|
| `GET /api/analytics/summary` | FastAPI | Analyst-protected; unavailable until reporting source exists |
| `GET /api/analytics/trends` | FastAPI | Analyst-protected; unavailable until reporting source exists |
| `GET /api/analytics/instruments` | FastAPI | Analyst-protected; unavailable until reporting source exists |
| `GET /api/analytics/freshness` | FastAPI | Analyst-protected; unavailable until reporting source exists |
| `GET /api/analytics/portfolio-performance` | FastAPI | New analyst-protected visualization contract; currently unavailable |
| `GET /api/analytics/holdings` | FastAPI | New analyst-protected visualization contract; currently unavailable |

### Spring client/workspace API

| Endpoint | Purpose | Analytics use |
|---|---|---|
| `GET /api/workspace/client/portfolio` | Current authenticated client's portfolio | Not used by internal analytics |
| `GET /api/clients/{clientId}/holdings` | Client-scoped holdings management/read | Not used by internal analytics |
| `GET /api/orders` | Current client's order history | Not used as a reporting source |
| `GET /api/instruments` | Instrument/workflow data | Not a benchmark history source |

The separation is required by BR-16. The analytics API must read a separate reporting database populated by committed events or CDC, not query Spring's transaction database.

## TDD Evidence Added

### Angular API test

`frontend/src/app/core/api/analytics-api.service.spec.ts` now checks:

- The performance request uses the analytics origin.
- The selected period is sent as `period=12m`.
- Shared filters such as `timezone` and `from` are preserved.
- The holdings request uses the analytics origin and shared date filters.

### Angular component test

`frontend/src/app/features/analytics/analytics-page.component.spec.ts` now checks:

- The unavailable state keeps filters visible and shows no invented figures.
- All three approved period buttons are present.
- Selecting `12M` requests the `12m` contract.

### FastAPI test

`analytics-service/tests/test_api.py` now checks that both visualization routes require the existing authenticated analyst boundary and return `503` while the reporting source is absent.

## Remaining Authoritative Work

The UI and contracts are ready for source integration, but the feature must not be called live or authoritative yet. The following remain required:

1. Approve portfolio valuation, benchmark provider, index methodology, market calendar, timezone, currency, and corporate-action definitions.
2. Add committed source publication for booked portfolio facts and benchmark snapshots.
3. Add idempotent reporting-loader support for portfolio values and benchmark values.
4. Populate reporting fixtures and PostgreSQL integration tests.
5. Implement repository queries with bounded date ranges, freshness metadata, and correction handling.
6. Replace the FastAPI `unavailable()` calls with approved reporting queries.
7. Add reconciliation and concurrent-load evidence for BR-16.
8. Update `docs/api.yaml` with the two new successful response contracts and their `401`, `403`, `422`, `429`, and `503` responses.

Until those steps are complete, the correct visible behavior is the labelled unavailable state.
