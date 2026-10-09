# Internal Analytics Insights

## What changed

Added an internal analytics summary endpoint for stakeholders who need quick business insight from trading activity.

Implemented:

- `GET /api/analytics/summary`
- optional `fromDate` / `toDate` filtering
- role protection for `ADMIN`, `AUDITOR`, and `ANALYST`

The response now surfaces:

- overall trade counts
- executed trade volume and notional
- active client and instrument counts
- most active instruments
- client activity trends over time

## How it was implemented

I followed the existing controller/service/mapper split:

- `AnalyticsController` exposes the endpoint
- `AnalyticsServiceImpl` validates the request and assembles the response
- `AnalyticsMapper` runs the aggregation queries against `client_trades` and `instruments`

The analytics are read-only and built from existing trade records, so they fit the current backend structure without changing order-processing behavior.

## Why it was done this way

The requirement was to surface a **small set of business insights** for internal stakeholders, not to build a full warehouse or BI platform.

This approach gives the platform a usable first analytics layer while keeping the implementation consistent with the current codebase.

## Tests added first

Added tests for:

- controller role protection and response shape
- service date-range validation
- mapper aggregation results and period filtering

## Documentation

Updated `docs/api.yaml` so the analytics summary endpoint now documents:

- optional date filters
- error responses
- the concrete response structure for metrics, active instruments, and activity trends
