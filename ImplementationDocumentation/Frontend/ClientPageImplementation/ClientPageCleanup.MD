# Client page cleanup

Prepared on 16 October 2026. The settlement assessment below was written before implementing the three-column dashboard. This document consolidates the accessible project chats after **Tadpoles - client page**, checked against the current source. Earlier documents describe historical stages; they do not override the current USD-only implementation.

## Settlement: what currently works

The application is an internally booked trading system. Fauxnance supplies prices and instrument information; it does not execute the orders or transfer money. There is no external bank settlement integration, clearing house, delayed settlement date, or background release of sale proceeds after a waiting period.

**Immediate internal settlement is implemented. Delayed/unsettled-cash settlement is not.** A database transaction commits the cash and position changes, fill, journals, reservation outcome, lifecycle event, and outbox evidence together. If booking fails, those writes roll back together. Accepted work survives browser reload/logout; a durable worker can finish or reject it.

| Action | Before completion | At successful commit | On rejection |
| --- | --- | --- | --- |
| Buy | Estimated consideration is reserved from posted cash; no shares are credited yet. | Execution consideration is debited, shares are credited, and the hold is consumed. | The order's cash hold is released; no shares or cash posting is invented. |
| Sell | Shares are reserved; they are still owned. Expected proceeds are not cash. | Shares are debited and sale proceeds immediately become posted, available USD. | The share hold is released; no sale proceeds are credited. |
| Deposit | The request may be in flight in the browser. | Movement receipt, cash journal, cash balance and outbox commit synchronously. | A rejected receipt has no cash posting. |
| Withdrawal | The same client lock protects cash; available funds are checked and briefly reserved within the transaction. | Cash is debited and the temporary hold is consumed in that same transaction. | No settled debit is booked. |

A lost HTTP response means the result is unknown to the browser, not that money is unsettled. Recovery uses the same request key and stored receipt. It must not add a second movement or order.

```text
posted cash = committed cash ledger balance
reserved cash = posted cash held for accepted/executing buys
available cash = posted cash - reserved cash
available shares = owned shares - shares reserved for pending sells
```

For example: USD 1,000 posted and USD 200 reserved means USD 800 can be invested or withdrawn. Buying for USD 190 consumes the USD 200 hold, leaves USD 810 posted/available, and adds the purchased shares. A filled USD 150 sale then increases posted/available cash to USD 960 immediately. There is no intermediate USD 150 unsettled-cash bucket.

The UI therefore uses **Reserved for pending buys**, rather than **Unsettled**, for the secondary amount. **Settled balance** shows the available-to-invest amount, explicitly labelled as such; the posted amount remains available as **Total settled cash**. This distinguishes free cash from cash already committed to pending instructions. Availability still depends on account, banking, instrument and quote eligibility; a cash figure is not permission to trade.

Implementation references: `TradingTransactions.admit`, `TradingTransactions.book`, `BankingServiceImpl.move`, `TradingMapper.balances`, the cash/position journals, and the transaction/reconciliation tests. There is no settlement engine to switch on. Adding delayed settlement would require new persisted obligations, due dates/calendar rules, buying-power and withdrawal rules, a durable settlement worker, reconciliation and migration tests; relabelling existing reservations would not implement it.

## Dashboard metrics and valuation design

The desktop summary contains three equal-width panels, in this order:

1. **Investing** — posted USD cash plus the estimated market value of all currently owned, eligible ledger positions.
2. **Settled balance** — USD available to invest, with reserved pending-buy cash and total posted/settled cash explained beneath it.
3. **Display currency** — the existing available-cash conversion. It remains informational and does not convert funds or value the portfolio in another currency.

```text
holdings estimate = sum(owned quantity × verified provider quote price)
investing estimate = posted USD cash + holdings estimate
```

Reserved cash is already included in posted cash and must not be added twice or deducted from net account value. Shares reserved for pending sells remain owned and stay in the holdings estimate. Pending buys add no holdings until filled. Zero positions and quarantined migration records are excluded consistently with the current portfolio.

The current portfolio endpoint provides quantities and cash, but no market-value total. A separate valuation read takes one consistent ledger snapshot, then retrieve provider prices outside the database transaction. This keeps quote outages from blocking the settled balance, positions or recent orders. Decimal calculations remain on the server; money crosses the API as decimal strings. The total uses current quote `price`, not historical execution cost, expected proceeds, chart pixels or an executable bid/ask promise. Final displayed USD amounts round HALF_UP to two decimals after summing exact position products.

The estimate must verify symbol mapping, USD currency, positive price, provider source, explicit freshness and both provider timestamps. It respects the configured age limit (up to 1,200 seconds) and rejects explicit stale flags. Market closure or a trading halt alone does not erase ownership or prevent an otherwise valid informational valuation. If any held security cannot be priced, the complete holdings and investing totals are unavailable; cash remains visible. No cash-only or partial total is presented as the full account value. The snapshot time and oldest quote time identify the estimate's age. No holdings means a valid zero holdings value without a provider call.

The summary preserves the earlier fix for scroll resets: background updates keep existing panels mounted. Valuation refresh/failure has its own state and must not reset the currency selection or collapse the entire page.

## Refactors

### Baseline — Tadpoles - client page

The initial Client page implemented a durable buy/sell simulation alongside legacy records, synthetic instrument fixtures, separate cash/position ledgers, admission/execution, request-key recovery, journals, polling, and client ownership checks. The initial delivery reported 317 backend, 16 database and 108 frontend tests. The subsequent crash-course Q&As explained why default accounts were legacy, why catalog tickers could be browse-only, and how the optional synthetic setup worked. Those explanations are historical; the following changes superseded that two-mode design.

### Tadpoles - client refactor instruction

This chat produced `ClientRefactorInstructions.MD`, rather than application changes. It specified removal of synthetic trading and dual modes; a single provider-priced internal booking model; safe schema consolidation and historical preservation; USD scope; banking references and cash movements; Trading Operations instrument controls; daily charts; and TDD, usability and documentation expectations. It identified the conflict between an initial USD-only release and HailMary's broader market requirements, rather than silently claiming the broader requirements were implemented.

### Tadpoles - client refactor imp

The user chose **USD-only initial delivery; explicitly defer broader markets**. The implementation then:

- Replaced the synthetic execution path with Fauxnance prices and one internal booking model. Orders still execute in this application's backend; no exchange/broker execution integration was added.
- Retired currency pseudo-tickers, CASH and SIM-* instruments from the trading catalog, and added provider identity/policy setup for the configured catalog. Existing full halts are preserved on startup. Bond-fund versus equity-fund classification received follow-up regression coverage.
- Added canonical fresh-install schema files separate from reference/demo inserts, and a versioned upgrade chain. Cutover refuses outstanding old commitments. Historical cash, dated holdings and other unresolved economic records are preserved/quarantined rather than silently converted into spendable USD.
- Added protected bank references with format validation, masked responses, encrypted full details, defaults, revision checks, edit/deactivation rules and immutable receipt snapshots. Banking is optional at signup but required before trading.
- Added keyed deposits/withdrawals, exact-cent arithmetic, movement/daily deposit limits, available-cash checks, recovery after unknown responses, paged history and synchronous internal settlement.
- Established Trading Operations instrument management, provider symbol testing, policy limits, side controls, full halts and audit reasons/revisions. Generic ADMIN and mixed CLIENT+TRADING_OPS identities do not bypass those role boundaries.
- Added Finance reconciliation and Operations readiness evidence for journals, holds, fills, events, worker lag and runtime permissions. Permission provisioning remains a distinct deployment operation.
- Added optional ECB reference-rate display estimates for available USD cash. This did not add multi-currency accounts, FX execution or portfolio valuation.
- Replaced the daily chart with Lightweight Charts, keeping daily-only data, accessible price tables and attribution. Added provider-data validation, cancellation and chart/quote independence.
- Corrected trading button contrast and added a journaled, idempotent local demo opening. Ordinary signup never grants money; rerunning setup does not replenish spent balances.
- Verified HTTP ownership/session behavior, reservation concurrency, write rollback, idempotency, reconciliation, fresh/upgrade Compose, backup/restore recovery and browser trading/banking flows. The final chat reported 341 backend, 19 database, 131 frontend tests plus two catalog regressions.

The chat had several interrupted/continued turns. Its final completed delivery is the outcome above; the interruptions were not separate implemented features. Real-provider execution at that checkpoint remained blocked by stale/unknown-session quotes and some unavailable daily data. Broader-market support remained deferred.

Detailed historical evidence: `ClientRefactorCommentary.MD`, `ClientRefactorCrashCourse.MD`, `Implementation.MD`, and the `evidence/` and `screenshots/` directories.

### Tadpoles - client light fixes

Three follow-up changes addressed observed provider behavior:

1. Synthetic daily bars without volume no longer fail OHLC chart validation. Quote eligibility remains a separate check. An explicitly unknown market state can use the verified scheduled U.S. core session; explicit closed states remain blocked. The schedule currently covers 2026.
2. Quote freshness was initially widened from 30 seconds to 10 minutes, with schema/policy validation and audited running-policy changes. A generic freshness/provenance rejection was split so an explicit stale flag has a clear error message.
3. The user corrected the provider update cadence to 20 minutes. The final age ceiling/default became **1,200 seconds**, with migration 010 and 14 running policies updated at that time. The user explicitly chose to keep rejecting `stale: true`, even inside that window. This preference remains in force. These were price-validity changes, not settlement-delay changes.

See `docs/market-data-session.md`, migrations 009/010, `TradingRules`, `TradingSession`, the candle adapter and their regression tests. The reported live state in that chat is historical evidence, not a guarantee about the provider today.

### Refactor portfolio and page styling — first cleanup

The preceding turn in this chat:

- Filtered zero-owned positions from the current portfolio query while retaining ledger rows and history. Full sales remove the ticker; partial sales and reserved sell quantities remain visible; repurchases reappear.
- Fixed the first order-status refresh clearing page data and collapsing its height. Portfolio and orders refresh in place, the currency component retains its selection, and deferred heading focus no longer scrolls the viewport.
- Removed the duplicate Portfolio navigation item and redirected its former URL to Overview.
- Aligned the cash/FX panels and reused the existing styled currency selector, responsive spacing and heading actions.
- Reformatted changed Java/Angular/Python files, expanded compressed SQL, separated methods/annotations and aligned import layout with the repository. Existing unrelated changes were retained.
- Added full-sale, reserved-share, fractional-repurchase and background-refresh regressions. Updated integration-test setup to apply the already-existing quote-policy migrations 009/010.

Verification: **349 backend + 19 database + 133 frontend = 501 passing tests**, production build and style checks. Browser checks covered desktop/mobile layouts, the old URL redirect, synthetic buy/full-sale and stable scrolling across the first status poll (388px before and after). The disposable preview was removed after verification. No production deployment or commit was claimed for this cleanup.

### Current cleanup — three metrics and consistent forms

This request adds the Investing estimate, moves available cash into the Settled balance panel, retains the FX panel, and standardizes the Trading Operations form controls against the other workspace pages. Settlement behavior remains immediate internal booking. The initial regression tests were run before implementation and failed because the valuation service and three-panel/form structure did not exist. Implementation then made those tests pass, and further tests cover partial-price failures, exact decimal values and the complete buy/sell lifecycle.

### Navigation and instrument management follow-up

The next cleanup addressed shared sidebar behavior and the Operations create/edit flow:

- The old **Add instrument** button only reset an inline form underneath the catalog, so it gave no visible navigation feedback. The catalog and editor are now separate components. **Add instrument** appears in the Operations sidebar and opens `/operations/instruments/new`; existing policies open `/operations/instruments/{id}`. A protected GET endpoint loads deep-linked policies and their current revisions.
- The shared sidebar stays below the sticky header while the document scrolls. Its desktop offset follows the actual header height through a ResizeObserver. A long multi-role menu can scroll within the available viewport. At the existing mobile header breakpoint, the header scrolls away and the sidebar pins to the viewport top; mobile retains the collapsible workspace menu. Browser testing caught and fixed the initial mobile gap caused by retaining the desktop header offset.
- Identity fields have examples such as SPY and NYSE Arca. Trading limits have short, lighter descriptions directly below their labels and linked through `aria-describedby`: share increments, per-order quantity, USD consideration and quote age. These descriptions explain existing rules; provider identity, stale-price rejection, USD scope, revision checks and role restrictions still apply.
- The isolated HTTP/database regression confirmed that successful creation already commits a catalog row and policy. Visibility problems included unchanged open catalogs, retained list filters, and errors displayed far above the old form. The editor now shows validation/save failures beside the save action, keeps the entered values on failure, and confirms the saved symbol and revision on success. A **View saved instrument in catalog** link searches for that symbol, so previous filters or alphabetical pagination do not hide it.
- Browser testing also reproduced a separate edit failure: PostgreSQL returns numeric limits padded to twelve decimal places, while the save parser previously accepted only eight. The parser now removes insignificant trailing zeros before checking the eight-place precision limit. A database regression resubmits the exact saved values and separately rejects a genuinely over-precise limit; no rounding or floating-point conversion is involved.
- Saving a new instrument replaces its URL with the persisted ID while retaining the same editor component and form. Saving an edit remains on that editor. Neither operation reloads the document. A late save response from an editor the operator has already left cannot overwrite the newly selected instrument.
- Open client and Operations catalogs re-query every 15 seconds while visible and when focus/visibility returns. This allows separate accounts to observe saved changes without a manual page reload. Existing rows stay mounted during refresh/failure; applied filters and pagination remain selected, and a partly typed search is not submitted by polling. A saved instrument appears in matching results once enabled; inactive rows remain manageable by Operations and are excluded from the client catalog. Quote/banking/side checks still determine whether it can be traded.

Verification and screenshots for this follow-up are recorded at the end of this document.

## Implementation details

- `GET /api/workspace/client/portfolio/valuation` resolves the client from the persisted authenticated identity. A supplied client ID cannot select another account; staff and unauthenticated requests are rejected. The endpoint and decimal-string response are documented in `docs/api.yaml`.
- `PortfolioValuationService` uses a read-only, repeatable-read transaction for cash, current positions and price policies. It closes that transaction before any external quote request. It then validates the prices at calculation time and returns `COMPLETE` or `UNAVAILABLE`, the amounts, snapshot time, oldest accepted quote time and any unpriced tickers.
- `AccountPageComponent` loads valuation, settled cash/positions and recent orders independently. It refreshes them when the bounded order-event watcher reports a change. **Refresh estimate** refreshes the valuation alone. This is an on-demand estimate, not a live streaming price feed; the timestamps remain visible. A pending refresh keeps the previous estimate visibly marked as updating; a failed refresh removes the displayed total rather than presenting it as current.
- The cash and valuation requests can capture slightly different moments if an order commits between them. The Investing total is internally consistent because its cash and quantities come from one database snapshot. Its quote timestamps are separate from that ledger snapshot. Provider calls are currently sequential and use the existing provider timeout configuration, so a larger portfolio can take longer to price without blocking the separate cash request.
- Desktop uses three equal columns; narrow screens stack them. All three panels share borders and spacing. The USD cash selector keeps its selection across background updates.
- The Trading Operations Add/Edit instrument form uses three explicit fieldsets: identity/provider validation, trading limits and trading permissions. Labels sit directly above their controls with a consistent gap; the provider-test button sits beside its symbol field and wraps on small screens. Checkboxes retain their natural size, and text inputs, selects, textareas and buttons share the workspace typography, borders and minimum control height. Existing save/validation/permission behavior remains in place.
- New Java and Angular code uses expanded methods, named records, separated form groups and repository-style formatting. No database schema or new settlement obligation was required for these display metrics.

## Verification for this cleanup

Automated results: **377 backend + 19 database + 136 frontend = 532 passing tests**, with zero failures/errors/skips in the Maven suites. The production Angular build, repository style check and `git diff --check` also passed. The OpenAPI YAML was successfully parsed using the installed formatter.

The new coverage includes:

- Cash plus fractional owned holdings, reserved cash/shares without double counting, summing before rounding, and amounts above JavaScript's safe integer range.
- Cash-only portfolios, missing cash, missing mappings/provider failures, and refusal to return a partial total when any held instrument lacks a price.
- Seventeen missing/stale/mismatched quote cases, the exact age boundary, and valid informational prices while trading is halted or the market is closed.
- Authentication, persisted client ownership (including an ignored foreign client-ID parameter), and rejected staff access.
- A PostgreSQL buy → reservation → fill → reserved sell → full sale cycle, with reconciliation. The test asserts that provider I/O happens after the snapshot transaction closes. It verifies that Investing includes owned shares while a sale is pending and becomes the new cash balance after the final fill.
- Three dashboard panels, separate valuation failure handling, precise decimal-string rendering, unavailable totals replacing prior estimates, preserved cash/currency DOM during updates, and explicit labelled Operations field groups. Existing full-sale removal, refresh, banking, chart, role and recovery regressions remain green.

The initial failing runs established the missing valuation class and missing dashboard/form structure before implementing them. Backend tests ran on Java 21 with disposable PostgreSQL databases; frontend tests used the existing Angular/Vitest setup. No external market or bank was used for these automated checks.

Browser checks used a disposable account, database and synthetic HTTP price provider:

- At 1440px desktop width, all three panels had the same top coordinate, width (356.875px) and height (462.109px). At 390px mobile width they stacked, with no page-wide horizontal overflow. Tables retain their own horizontal scroll.
- A two-share test buy executed at USD 101 per share. Cash became USD 9,798; the fixture's indicative USD 100 price valued the holdings at USD 200; **Investing displayed USD 9,998.00**. The difference from the original USD 10,000 reflects the fixture's ask/indicative-price spread.
- Stopping the synthetic provider and refreshing the estimate changed Investing to **Unavailable**, named AAPL as unpriced, and kept USD 9,798 settled cash and the currency selector visible.
- The Operations inputs measured 44px high with consistent 10px/12px padding, 640px desktop field width, and 305px mobile field width. The symbol/test-button row wrapped cleanly on mobile. The reason textarea remained taller. Provider validation still recognized AAPL and reported eligible USD data after the provider restarted.
- The earlier automatic refresh/scroll regressions remained covered by the frontend tests. This turn's browser checks focused on the new valuation and forms; the first cleanup's explicit scroll-position comparison is recorded above.

Screenshots: [desktop dashboard](screenshots/client-cleanup-dashboard.jpg), [mobile dashboard](screenshots/client-cleanup-mobile.jpg), [price outage](screenshots/client-cleanup-price-outage.jpg), [Operations fields](screenshots/client-cleanup-operations.jpg), and [mobile Operations form](screenshots/client-cleanup-operations-mobile.jpg).

The preview tab was closed and the temporary viewport override reset. The disposable preview was removed after verification. This cleanup is saved in the working tree; it does not deploy the application or change real account data.

## Verification for navigation and instrument management

The initial frontend regressions failed for missing editor routes, the Add instrument navigation link, and catalog content being replaced during refresh. The initial database test failed because the individual policy GET endpoint did not exist. A later browser/API round-trip reproduced the saved-decimal edit failure; its database regression failed with HTTP 400 before the parser fix. These failures were checked before their corresponding fixes.

Coverage now includes:

- Create/edit routing, sidebar links, deep-linked policy reads, and denied client/mixed-role access.
- Required form fields, inline server failures, preserved form values, successful saves retaining the mounted editor, and late responses not taking over another editor.
- Timed/focus/visibility refresh, hidden-page pauses and logout cleanup, retained catalog rows after failures, and unfinished search text not becoming an applied filter.
- A real PostgreSQL/HTTP create → read → edit → disable lifecycle, client visibility after enabled saves, exclusion after disable, and retained Operations access. Edits reuse the exact decimal strings returned by persistence; an actually over-precise limit is rejected.
- Shared header measurement and ResizeObserver teardown. Browser checks supplement component tests because DOM unit tests do not calculate sticky layout.

Browser verification uses the disposable preview and synthetic SPY quotes, with no real account or provider changes. Creating SPY through the new route confirmed revision 1 and showed the row in the searched Operations catalog. Saving kept the operator at the bottom of the form: the scroll coordinate changed from 1681px to 1395px when the temporary provider-result panel disappeared, without resetting to the top. At that position, both the desktop sidebar top and header bottom were 80.15625px. At 390px width, the expanded mobile menu pinned at 0px after the header scrolled away; the document had no horizontal overflow. The initial mobile gap was reproduced and corrected before this check passed.

With the client catalog left open, an independent authenticated Operations API request edited SPY using its persisted decimal strings. The new name appeared automatically without navigation or reload. A second update, with the preview stable, kept `scrollY` exactly **720px before and after**. The client sidebar top and sticky header bottom both remained **80.34375px**. During the preview rebuild, the catalog also retained its rows through a temporary fetch error and recovered on a subsequent poll.

Final automated results: **378 backend + 19 database + 148 frontend = 545 passing tests**. The full backend/database suites were rerun after the decimal fix, including the failing round-trip regression and precision rejection. The production Angular build was rerun after the mobile CSS correction; the repository style check and `git diff --check` passed. OpenAPI parsing passed as well.

Screenshots: [saved policy and fixed desktop navigation](screenshots/navigation-saved-instrument.png), [mobile navigation and field guidance](screenshots/navigation-mobile-editor.png), and [automatically updated client catalog](screenshots/navigation-client-catalog.png).

The temporary viewport override was reset, the preview tabs were closed, and the disposable preview was removed. These changes are saved in the working tree. The existing application containers and their data were not redeployed or modified.
