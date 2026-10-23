# Analytics Visualization Crash Course

This guide explains the analytics visualization refactor, the current endpoint ownership, the UI behavior, and the path from the provisional contracts to live reporting.

## 1. What changed

The existing analytics workspace already had three Angular routes:

- `/analytics/overview`
- `/analytics/trends`
- `/analytics/instruments`

They continue to use the same `ANALYST` route policy and the same read-only FastAPI boundary. The overview now includes two visualization panels:

- **Portfolio vs market benchmarks:** a Chart.js line chart for the portfolio, S&P 500, Dow Jones Industrial Average, Nasdaq-100, and Russell 2000.
- **Largest holdings:** a Chart.js doughnut chart for the five largest holdings plus an `Other` category for all remaining holdings.

Each chart has a matching exact-value table. The performance panel offers `1M`, `3M`, and `12M` controls. The selection is sent to the API as `period=1m`, `period=3m`, or `period=12m`.

The API contracts are typed in both TypeScript and Pydantic:

- Angular: `PortfolioPerformanceResponse`, `PortfolioHoldingsResponse`
- FastAPI: `PortfolioPerformanceResponse`, `PortfolioHoldingsResponse`

The routes are:

```text
GET /api/analytics/portfolio-performance?from=...&to=...&timezone=UTC&granularity=day&period=1m
GET /api/analytics/holdings?from=...&to=...&timezone=UTC&granularity=day
```

## 2. Why authoritative mode currently returns 503

The repository does not yet contain an authoritative reporting feed for portfolio valuations, benchmark snapshots, fills, segment history, or reporting watermarks. Existing Spring endpoints expose client-owned transactional data, but the design documents explicitly prohibit using those endpoints as an internal reporting source.

The analytics service remains fail-closed in authoritative mode. The default local Compose configuration now seeds fixed pseudonymous rows into the separate reporting database and labels every response `DEMONSTRATION`. A production-like configuration without an approved reporting loader still returns `503`. The page does not display notebook values, random values, or unlabeled zeros.

## 3. Switching analyst data modes

The mode is selected by `ANALYTICS_DATA_MODE` in the Compose environment file. The checked-in local default is `demo`.

### Demo mode (available now)

1. Set `ANALYTICS_DATA_MODE=demo` in `.env`.
2. Run `docker-compose up -d --force-recreate analytics-api` (or the normal `docker-compose up -d`).
3. Sign in as `demo.analyst` and open Analytics. The response metadata contains `data_mode=DEMONSTRATION`, and the UI displays the “Demonstration data” warning.

The Compose seed job runs with the reporting-database owner credential. The API continues to connect as the read-only `analytics_reader`; demo mode queries only rows marked `is_demo=true` and the `demo-fixture` watermark. The seed is repeatable and does not require deleting the reporting volume.

### Authoritative/live mode (not connected yet)

1. Set `ANALYTICS_DATA_MODE=authoritative` in `.env`.
2. Recreate the API with `docker-compose up -d --force-recreate analytics-api`.
3. The API then selects only `is_demo=false` rows and the authoritative watermark. Demo fixtures remain stored but are not selected.

**Important:** this repository does not yet include the committed outbox/CDC loader or approved live portfolio/benchmark feed. Therefore authoritative mode currently returns `503`/unavailable; changing the setting does not turn the fixed demo fixtures into live data. Do not advertise authoritative mode as operational until the loader, definitions, reconciliation, and freshness gates described below are complete.

To switch back, restore `ANALYTICS_DATA_MODE=demo` and recreate `analytics-api`. Do not use `docker-compose down -v` for a mode switch; it deletes the persistent reporting database and unrelated local data.

### What the demo charts represent

The fixture is deterministic, not captured market history. It uses weekday-only dates through the latest closed fixture date, staggered instrument activity, variable fill counts and quantities, and five independently varying cumulative return paths. The API re-indexes the selected performance window to 100 at its first point. These choices make the example shapes less repetitive while keeping rebuilds and tests reproducible. Do not use the demo curves to infer real returns, benchmark performance, market events, or execution behavior.

This behavior is required by the analytics design and supports:

- BR-16: analytics must not contend with live transactional workloads.
- BR-17: displayed metrics must have approved definitions.
- BR-09 and BR-14: transactional and legal state remain owned by the Spring domain.

## 4. How the performance visualization works

### Response shape

A connected service returns a list of date points:

```json
{
  "filters": {
    "from": "2026-01-01",
    "to": "2026-12-31",
    "timezone": "UTC",
    "granularity": "day"
  },
  "metadata": {
    "reporting_status": "CURRENT",
    "data_as_of": "2026-10-19T17:00:00Z",
    "last_successful_load_at": "2026-10-19T17:05:00Z",
    "period_start": "2026-01-01",
    "period_end": "2026-12-31",
    "reporting_timezone": "UTC",
    "definition_version": "2026-10-01",
    "source_watermark": "event-000100",
    "warnings": []
  },
  "period": "12m",
  "points": [
    {
      "date": "2026-01-01",
      "values": {
        "portfolio": 100,
        "sp500": 100,
        "dow_jones": 100,
        "nasdaq_100": 100,
        "russell_2000": 100
      }
    }
  ]
}
```

### Normalization rule

The values are indexed to 100 at the beginning of the selected period. The local demo service applies this normalization to its deterministic fixture path. Authoritative values still require an approved loader and must document:

- Whether portfolio values include cash, fees, dividends, and corporate actions.
- Which price and benchmark providers are used.
- Whether the values are time-weighted or money-weighted.
- How missing market days and late values are handled.
- The valuation currency and any approved FX conversion.
- The reporting timezone and business date.

The frontend only plots the supplied points. It does not calculate investment returns or invent missing benchmark values.

### Period behavior

The three buttons are intentionally explicit:

| Button | API value | Intended range |
|---|---|---|
| `1M` | `1m` | Past month |
| `3M` | `3m` | Past three months |
| `12M` | `12m` | Past twelve months |

The page's date filters remain visible because the reporting period and the selected visualization window are separate policy decisions. A future implementation may constrain the date range server-side or make the period selector update the date range, but it must keep the contract unambiguous.

## 5. How the holdings visualization works

### Response shape

A connected service returns holdings with explicit market value, percentage, and currency:

```json
{
  "holdings": [
    {
      "symbol": "AAA",
      "display_name": "Example Holding",
      "market_value": "12000.00",
      "percentage": "18.25",
      "currency": "USD"
    }
  ],
  "total_market_value": "65750.00",
  "currency": "USD"
}
```

### Top five rule

The component sorts by numeric `market_value` in descending order:

1. Keep rows 1 through 5.
2. If there are more than five rows, sum the remaining market values into `Other`.
3. Sum the remaining percentages into `Other`.
4. Keep remainder holdings separated by currency. The default demo portfolio uses one valuation currency; a multi-currency response must not be reduced to one combined doughnut without an approved FX basis.

The chart slice size uses market value. The exact table shows both market value and percentage, so a user can inspect the aggregation instead of relying on a visual angle.

## 6. How to use the page today

1. In the default local Compose deployment, sign in with `demo.analyst` and password `RoleDemo2026!`; the verified JWT contains `ANALYST`.
2. Open `/analytics/overview`.
3. Set the date range and optional market, asset class, or instrument filters.
4. Select `1M`, `3M`, or `12M` in the performance panel.
5. Use the exact-value tables when comparing individual dates or holdings.
6. Read the status chip and metadata strip before interpreting any figure.
7. Read the demonstration warning before interpreting the fixed local fixtures. In authoritative mode, if the reporting source is unavailable, use **Try again** after the service is restored. Do not treat an unavailable panel as a zero result.

The `trends` and `instruments` tabs continue to use their existing aggregate-report contracts. The new portfolio visualizations are shown on the overview only because they are portfolio-level context rather than instrument-activity rankings.

## 7. How to connect real data

### Step 1: Approve definitions

Record the definition version and approval for:

- Portfolio valuation and performance methodology.
- Benchmark identifiers and providers.
- Currency and FX rules.
- Corporate actions and dividend treatment.
- Portfolio snapshot frequency.
- Freshness thresholds and delayed-result behavior.
- Retention, privacy, and analyst entitlement policy.

### Step 2: Extend reporting schema

Add reporting-only structures for:

- Portfolio valuation snapshots keyed by pseudonymous portfolio/reporting scope and business time.
- Benchmark index snapshots keyed by stable benchmark key and business time.
- Currency and valuation metadata.
- Source event IDs, received timestamps, corrections, and definition versions.
- Reporting watermarks and reconciliation results.

Do not add raw client names, emails, or unrestricted account records to the response source.

### Step 3: Publish committed facts

The Spring transaction service remains authoritative for live order, fill, cash, and holding state. Publish committed events through the approved outbox or CDC path. The reporting loader must be idempotent and must not treat `EXECUTED` status alone as a booked fill.

Benchmark data should enter through a separately approved market-data ingestion path. The loader should preserve provider timestamps and distinguish business event time from received time.

### Step 4: Extend the existing repository queries for the approved source

The local demonstration repository queries already exist. Replace or extend the demo-only source path with bounded authoritative reporting queries only after the source and definitions are approved. Requirements:

- Read only from the separate reporting database.
- Validate `period`, date range, timezone, and filters.
- Use approved benchmark keys only.
- Return `data_as_of`, `last_successful_load_at`, `reporting_status`, and `definition_version`.
- Keep currencies separate unless conversion is approved.
- Return `503` when no valid result exists.
- Return delayed/partial metadata when only some data is affected.
- Enforce query timeout, row bounds, and analyst entitlement.

### Step 5: Add authoritative integration fixtures

Create deterministic PostgreSQL fixtures for:

- One portfolio and all four benchmarks across 1, 3, and 12 months.
- Six or more holdings to prove the `Other` aggregation.
- A multi-currency case that must not be silently merged.
- A delayed watermark.
- A correction or late-arriving valuation.
- Empty activity and unavailable database states.

### Step 6: Update API documentation

Add the two new paths to `docs/api.yaml` with successful response examples and `401`, `403`, `422`, `429`, and `503` responses. Keep the examples clearly marked as contract examples, not production data.

## 8. Test strategy

Run focused tests first:

```text
cd frontend && npm test -- --watch=false
cd analytics-service && pytest -q
```

Use the commands above after changing either mode or fixture data. The demo fixtures are checked into the reporting seed; authoritative-mode tests should continue proving fail-closed behavior until a real loader is connected.

Before enabling live data, retain or add these assertions:

- Angular HTTP tests verify exact URLs, auth headers, filters, and period values.
- Component tests verify the three periods, loading/error states, exact tables, and `Other` aggregation.
- Chart lifecycle tests verify instances are destroyed on reload and component teardown.
- FastAPI tests verify missing token `401`, non-analyst `403`, invalid filters `422`, unavailable source `503`, and successful fixture responses.
- PostgreSQL tests verify benchmark date alignment, currencies, corrections, and freshness metadata.
- Compose tests verify analytics has reporting credentials only and cannot write to the reporting schema.

Then run the broader project gates from `AnalyticsPageImplementation.MD`.

## 9. Troubleshooting

### The charts show unavailable

In authoritative mode this is expected until the reporting source is connected. In demo mode, check `ANALYTICS_DATA_MODE=demo`, the seed job exit status, FastAPI readiness, and `data_mode=DEMONSTRATION` metadata. Check service logs without exposing tokens or protected values.

### A chart is blank after a successful response

Check that:

- `points` is non-empty for performance.
- Each point has a valid ISO date.
- Values are numeric or numeric strings.
- `holdings` contains market values and percentages.
- The component is running in a browser, not SSR-only rendering.
- The browser console does not report a Chart.js canvas reuse error.

### The holdings pie chart has the wrong slices

Confirm the response is sorted by market value in the component and that the source did not already send an `Other` row that should be excluded or explicitly handled. Confirm currencies are compatible before aggregation.

### A benchmark appears as zero

The frontend does not intentionally replace missing values with zero. Inspect the source payload and definition version. A missing value should remain missing and the report should carry a warning or partial status.

## 10. Files changed by this refactor

- `frontend/src/app/core/api/analytics-api.service.ts`
- `frontend/src/app/core/api/analytics-api.service.spec.ts`
- `frontend/src/app/features/analytics/analytics-page.component.ts`
- `frontend/src/app/features/analytics/analytics-page.component.spec.ts`
- `analytics-service/app/schemas.py`
- `analytics-service/app/api.py`
- `analytics-service/tests/test_api.py`
- `AnalyticsVisualizationCommentary.MD`
- `AnalyticsVisualizationCrashCourse.MD`

The refactor is deliberately limited to the analytics contract and presentation layer. It does not change Spring ownership of client workflows or claim that reporting data is already authoritative.
