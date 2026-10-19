# Analytics credential dashboard: proposed specification

## Purpose and status

The analytics credential should open a **read-only business analytics page** for an authorized internal analyst. The page should help its holder understand historical trading activity by instrument, time, market, and approved client segment. Every figure should show what it measures and how current the underlying reporting data is.

This specification distinguishes documented needs from proposed page behavior. A proposed feature is **not** an approved business mandate.

| Label | Basis |
|---|---|
| **[SOURCE — Must, BR-16]** | Historical trading analysis must be possible by instrument, time, and client segment without analytical workloads slowing live trading. |
| **[SOURCE — Should, BR-17]** | Internal reporting should provide a small set of useful business measures, including volumes, most-active instruments, and activity trends. |
| **[DOCUMENT PROPOSAL — analytics role and Angular page]** | An authorized analyst could use an analytics dashboard with date, market, instrument-type, and approved-segment filters; chart and table views; permitted aggregate export; and a data-freshness indicator. |
| **[DOCUMENT PROPOSAL — data boundary]** | The page would query a separate analytical store through a reporting API or approved reporting tool. Aggregate or pseudonymized data would be the default; client-level analysis would require a separate policy. |

## Proposed page structure

### 1. Header and reporting status **[PROPOSAL]**

Display **Business Analytics** with a short description such as “Historical trading activity and trends.” The header should also show:

- **Data as of:** the latest business event successfully included in reporting, with an explicit timezone.
- **Reporting state:** *Current*, *Data delayed*, *Loading*, or *Unavailable*, according to thresholds agreed later.
- **Period covered:** the first and last dates represented in the current results.
- A brief reminder that reporting data is derived and may lag a recently booked trade.

On a stub page, use a prominent **“Sample data”** or **“Reporting data not connected”** label. Fixture values must not appear to be live trading results, and the page must not invent a successful load time.

### 2. Filter bar **[PROPOSAL]**

Offer a date range and reporting granularity, such as day, week, or month. Additional filters could cover market or region, asset or instrument type, and an instrument search. Show a client-segment filter only after the segment definition and the holder’s entitlement are approved.

Keep selected filters visible above the results and include **Reset filters**. State which event date each measure uses: submission, acceptance, or fill. If a filter combination returns no rows, say **“No activity for these filters.”** Distinguish that state from a failed load or unavailable reporting service.

### 3. Summary measures **[PROPOSAL; definitions require approval]**

Each card should show its unit, period, and a short definition through adjacent help text or an information control.

| Proposed measure | Meaning and display rule |
|---|---|
| **Orders submitted** | Distinct client submission attempts in the period, if pre-acceptance attempts are included in the approved model. |
| **Orders accepted** | Distinct orders accepted in the period. Acceptance is separate from execution. |
| **Orders filled** | Distinct orders reaching a filled outcome during the period. |
| **Fills booked** | Distinct booked fill events. Keep this separate from filled orders because an order may produce multiple fills if that behavior is supported. |
| **Traded volume** | Executed quantity with its instrument/unit context, or traded notional with its currency. Do not combine unlike quantities or currencies into an unexplained total. |
| **Active clients** | Distinct pseudonymous clients meeting an approved activity definition, such as an accepted order or booked fill. |
| **Rejection rate** | Optional until the included rejection types and denominator are agreed. |

Omit any measure whose meaning cannot yet be explained, or mark it **“Definition pending.”** A blank value must not silently stand for zero.

### 4. Activity trends **[PROPOSAL]**

The primary chart should plot activity across the selected period. A measure selector could switch between accepted orders, booked fills, and traded notional. The axes should name the measure and unit; notional should show its currency or approved conversion basis. A comparison with the prior equivalent period could be added if business users find it useful.

Provide a **chart/table toggle** so the exact values remain available without reading a graph. Tooltip values should include the period, measure definition, and any applicable currency. Time grouping should use an agreed calendar and timezone rather than the browser’s local date by accident.

### 5. Most-active instruments **[PROPOSAL]**

Show a ranked table or horizontal chart with instrument name or symbol, market, asset class, and the selected activity measure. Allow ranking by fill count or executed quantity; these answer different questions. If notional is offered, label its trade currency and avoid cross-currency ranking unless a conversion method has been approved.

### 6. Aggregate detail table **[PROPOSAL]**

A table should let the analyst inspect the values behind the summary and charts. A practical default row is **period × market × asset class × instrument**, with approved client segment added only when permitted. Candidate columns are accepted orders, filled orders, booked fills, executed quantity, traded notional, and trade currency.

Show applied filters, support sorting and pagination, and display totals only when summing the values is meaningful. The analyst’s default view should contain aggregates, not names, contact information, unrestricted account records, or individual order evidence.

### 7. Freshness and data-quality panel **[PROPOSAL]**

Explain the scope and condition of the loaded data in plain language:

- Latest source event included and last successful load.
- Whether processing is delayed or a data-quality exception is under investigation.
- The source watermark or reporting period through which figures have been reconciled, if available.
- A concise explanation of what **Data as of** means.

If the reporting pipeline stops, keep the last valid figures visible with a prominent **“Data delayed”** notice and timestamp. Do not quietly present stale figures as current. Detailed replay, backlog, and reconciliation controls can remain in authorized operational or finance views; this page needs a reliable status summary.

### 8. Export and definitions **[PROPOSAL]**

If policy permits export for this credential, offer **Export current aggregate view**. The exported file should reflect the same filters, definitions, and access rules as the screen and include its generation time and data-as-of time. It should exclude client-identifying fields. If export policy is undecided, the stub can show the control as unavailable with a short explanation.

Provide a **Definitions** panel explaining order counts versus fill counts, quantity versus notional, trading-event time versus load time, currency handling, approved segments, and the treatment of corrections or reversals. These definitions should be versioned or otherwise traceable so repeated monthly reports remain comparable.

## Stub-page behavior **[PROPOSAL]**

Before the reporting API and separate analytical store are connected, the stub can still make the intended experience reviewable:

1. Render the analytics navigation, page sections, filter layout, and explanatory text.
2. Show clearly labelled sample charts and tables, or a useful empty state: **“Historical reporting is not connected yet.”**
3. Mark filters and export as demonstrative or disabled when they cannot return real results.
4. Reserve a visible place for freshness and reporting status without inventing timestamps or reconciliation results.
5. State that the page is a derived historical view and does not establish current cash, holdings, order acceptance, or legal trade state.

The completed screen alone would not demonstrate BR-16. That requirement also calls for reporting workload isolation and evidence that heavy analytical queries do not materially degrade live order processing.

## Access and navigation **[DOCUMENT PROPOSAL, refined]**

The analytics credential would navigate to `/analytics` and see only entitled internal reporting features. It should not expose buy/sell controls, client holdings, unrestricted order search, full audit evidence, cash-adjustment actions, or access administration. Other internal roles could receive selected aggregate reports only when specifically entitled.

Angular route guards can shape the navigation, but the reporting API must enforce authorization on each request. Reporting queries should run against the separate analytical store, not the production transactional database. The analytics role should have no general transactional database access.

## Empty, loading, and failure states **[PROPOSAL]**

| State | Page response |
|---|---|
| **No activity** | Keep the selected filters visible and say that no activity matches them. |
| **Loading** | Show a loading indicator without replacing known values with misleading zeroes. |
| **Data delayed** | Display the last successful load time and identify the figures as potentially incomplete. |
| **Reporting unavailable** | Explain that historical reports cannot currently be refreshed; avoid implying that client trading is unavailable. |
| **Access denied** | Show a clear permission message without revealing protected data or internal query details. |
| **Partial data-quality exception** | Label affected periods or measures and direct the user to an authorized support path. |

## Decisions required before the page becomes authoritative **[DECISION REQUIRED]**

The business analyst, Finance, Compliance, and data owner should agree on:

- Exact measure definitions and which event date each one uses.
- Approved client-segment taxonomy, history, and small-cohort privacy rules.
- Currency conversion policy for any firmwide notional figure.
- Reporting timezone, market-calendar treatment, and late-arriving data behavior.
- Export permissions and any additional client-level access policy.
- Acceptable reporting lag, status thresholds, and reconciliation display.
- Treatment and presentation of corrections, reversals, and fees.

Until these choices are approved, the layout can be demonstrated, but sample values and unsettled labels should remain explicitly provisional.
