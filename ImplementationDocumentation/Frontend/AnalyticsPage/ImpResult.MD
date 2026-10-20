# Analytics Page Usability Result

## Assessment

The previous analytics experience was not usable for regular or casual use: each route opened the same generic unavailable page, there was no shared report scope, no way to move between views, and no explanation of freshness or metric meaning.

The new page is a usable **provisional workspace shell** even while the reporting source is unavailable. It gives an analyst one consistent place to set a date range, choose daily/weekly/monthly granularity, narrow market, asset class, or instrument, move between Overview, Trends, and Instruments, retry the service, and see why values are not displayed. Filters are URL-backed so a refreshed or shared view retains its scope. The page deliberately shows no zeros or notebook values.

It is not yet useful for casual analysis of real activity because the reporting loader and approved definitions do not exist. That is an intentional product limitation, not a UI gap: presenting plausible numbers now would make the page easier to use while making it less trustworthy.

## Better design direction for the first data-connected release

Use a calm analyst command center with a persistent scope bar, a compact freshness rail, and one primary question per view:

- **Overview:** “What happened in this period?” Show four approved measures with unit, event date, currency, and definition version beside each value. Keep the instrument preview below the summary, not in a separate navigation step for the common scan.
- **Trends:** “When did it happen?” Pair a readable chart with an always-visible exact-value table. Add a measure selector that makes `fills_booked`, `orders_filled`, and `executed_quantity` visibly different choices.
- **Instruments:** “Where did it happen?” Use a sortable, paginated ranking. Keep symbol, market, asset class, selected measure, unit, and currency in the same row.
- **Freshness rail:** Keep `CURRENT`, `DATA_DELAYED`, `PARTIAL`, and `UNAVAILABLE` near the report title. Show `data_as_of`, last successful load, timezone, watermark, and warnings without forcing the user to open a separate page.
- **Scope memory:** Preserve filters in the URL and offer a small set of approved saved views only after segment entitlements and export/audit policy are settled.

## Reasons for this direction

1. Casual users need a clear starting question and a small number of decisions. A shared scope bar prevents each screen from teaching a different filter workflow.
2. Analysts need confidence before interpretation. Freshness, event date, definition version, unit, and currency belong next to the figure rather than in documentation away from it.
3. Charts are good for shape, not exact reconciliation. The exact table stays beside the chart so a user can inspect a point without estimating from pixels.
4. A separate freshness rail makes delayed data visible without hiding the last valid result or implying that live trading is unavailable.
5. Segment and currency rules are policy-sensitive. Keeping them explicit in the row and filter model avoids ambiguous totals and makes later approval changes traceable.

## Remaining usability gates

- Connect the committed outbox/CDC loader and prove idempotent fills before rendering live values.
- Approve metric definitions, segment entitlement, suppression, timezone, quantity units, and currency conversion rules.
- Add current, delayed, partial, no-activity, and populated fixture tests to the Angular component.
- Validate the layout with a real browser at mobile and desktop widths once the local frontend server and Playwright tooling are available.
