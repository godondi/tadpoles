# UI cleanup: analysis and implementation

## Scope and reading of the references

The screenshots supplied with the request are visual references for the current Support form and case list, banking controls, trade confirmation, and the Analyst navigation strip. They are evidence of current layout and interaction friction; the requested changes come from the user's message. There were no attached text documents whose instructions needed to be followed.

## Design review

- **Support intake and case queues:** The form and filters spread across the available width, controls feel ungrouped, and repeated default button styling makes important actions hard to distinguish. Case lists benefit from compact filters, a readable form measure, clearer input focus, and consistent surface and spacing tokens.
- **Trade ticket:** The estimate is long and presents six values as a tall detail list. Although review and confirmation already happen on the ticket page, the data layout consumes unnecessary vertical space and makes comparison harder.
- **Markets:** The old page asks users to type an asset class, then returns a paged table. It has no useful landing state and cannot filter by the catalog's true market or listing venue. The seed files use `X:BTC-USD`, `X:ETH-USD`, `X:DOGE-USD` and FX symbols such as `FX:EURUSD` and `FX:USDJPY`; FX and crypto rows use `OTC`. US listings include NYSE, several Nasdaq labels, NYSE Arca, and Cboe BZX.
- **Operations instrument editor:** Listing venue is free text even though the catalog has a finite set of known values. Quote age is numeric despite having a small set of understandable operational intervals. Quantity and notional limits are actual numeric values and should remain numeric.
- **Analyst navigation:** The Overview, Trends and Instruments links duplicate the role navigation/sidebar and add a second navigation system to each report page.
- **Theme:** There was no persisted theme preference. A user selecting dark mode would otherwise need to rely on browser or OS-level behavior.

## Implemented changes

### Shared controls and forms

In `../../frontend/src/styles.css`, standardized control height, padding, borders, focus rings, hover feedback and panel corners. Shared form styles now apply to client and staff workspace controls, including support intake. Support case filters wrap as an adaptive grid, and the ticket review shows its estimate metrics in a compact three-column grid on desktop, two columns on mobile, and one column on narrow screens. This makes the price, cash and quantity estimates easier to compare without changing review or confirmation behavior.

### Market discovery and filtering

Rebuilt the client Markets page around two explicit states:

1. With no query or filters, show three columns for Stocks & ETFs, Crypto, and Forex. Featured symbols are selected from the catalog using the seeded symbols (`SPY`, `QQQ`, `IWM`, `X:BTC-USD`, `X:ETH-USD`, `X:DOGE-USD`, `FX:EURUSD`, `FX:USDJPY`); additional instruments fill a group when a featured code is absent.
2. After a search or filter is applied, show paged matching catalog rows across all asset types, with dropdown filters for market and actual listing exchange. A ticker search remains cross-market unless a market filter is selected.

Extended the client catalog endpoint and SQL filters with `market` (`STOCK_ETF`, `CRYPTO`, `FOREX`) and `exchange`, derived from the existing `security_type` and `exchange` catalog columns. Asset-class filtering remains supported for existing clients. The dropdown options follow values found in the US, crypto and FX reference catalogs rather than normalizing away provider symbols.

### Operations instrument editor

Changed venue to a dropdown with the catalog's known listing labels. Changed maximum quote age to 1, 5, 10 or 20 minutes. Quantity step and maximum quantity/notional use numeric input semantics and bounds. Existing explanatory text continues to distinguish an Operations venue label from provider listing verification.

### Analyst navigation

Removed the duplicate Overview / Trends / Instruments tabs from Analytics pages. The role sidebar remains the navigation source, and route handling is unchanged.

### Dark mode

Added a theme toggle to the Account menu. The preference is saved in `localStorage` and applied on the document root so it follows the user across role pages and browser sessions. Dark theme tokens cover the shared workspace surfaces, borders, text, controls and notices.

## Files changed

- `../../frontend/src/styles.css`
- `../../frontend/src/app/features/client/markets-page.component.ts`
- `../../frontend/src/app/features/operations/instrument-policy-editor.component.ts`
- `../../frontend/src/app/features/analytics/analytics-page.component.ts`
- `../../frontend/src/app/shared/top-nav/top-nav.component.ts`
- `../../frontend/src/app/shared/top-nav/top-nav.component.html`
- `../../frontend/src/app/shared/top-nav/top-nav.component.css`
- `../../backend/src/main/java/com/neueda/leap/controller/WorkspaceClientController.java`
- `../../backend/src/main/java/com/neueda/leap/service/WorkspaceReadService.java`
- `../../backend/src/main/java/com/neueda/leap/service/impl/WorkspaceReadServiceImpl.java`
- `../../backend/src/main/java/com/neueda/leap/mapper/WorkspaceReadMapper.java`

## Verification notes

The seeded catalog manifests were inspected to confirm market symbols and exchange labels. Angular's compiler (`ngc -p tsconfig.app.json --noEmit`) completed successfully, checking component templates and TypeScript. The full Angular build could not finish its SSR stage because the sandbox denied a local `::1` listener (`EPERM`). Backend Maven compilation could not start because Maven could not open its dependency-cache lock under `~/.m2`. Automated tests were not run. The market and exchange filters rely on catalog values in `security_type` and `exchange`; unsupported or future exchange labels will need to be added to the dropdown options when the reference catalog expands.

## Follow-up audit — October 25, 2026

The second set of screenshots is treated as evidence of defects, while the user's message defines the requested corrections. Before changing implementation, signed in through the UI as each of the six seeded roles at the running local application on port 8087 and navigated its sidebar pages.

| Role | Pages visited | Current state and proposed correction |
| --- | --- | --- |
| Client | Overview, Markets, Orders, Support, Banking & cash, SPY instrument detail, trading-availability support intake | Markets rows have an explicit arrow grid placement that collides with the name column and creates oversized rows. The embedded chart adds a second page-sized margin/padding layer. Support updates are a large mostly empty card. Intake and bank forms stack every field and hint, leaving unused horizontal space. Use explicit grid placement for each result cell, remove nested page padding, compact update status and filters, and group related form fields. |
| Operations | Overview, Support cases, Exception queue, Reconciliation, Orders & monitoring, Instruments, Add instrument | Overview reports zero enabled policies even though catalog entries exist; catalog availability differs from provider health. Investigation pages correctly require an access-purpose context. Add instrument has a long single column despite three natural field groups. Use responsive identity/limits/permissions groups, compact queue filters, and keep access controls intact. |
| Compliance | Review cases, Investigations, Evidence & exports | No review cases in the demo queue. Investigation/export views present the purpose gate. The gate has wide empty areas and oversized surrounding margins. Tighten shared panel/heading spacing and align labelled controls. |
| Analyst | Overview, Activity trends, Instrument analysis | Duplicate tabs are removed. Reports load demo data. Hardcoded light report colors and a global button color override conflict with dark mode. Replace literal surface/text colors with semantic theme tokens while preserving report warnings and metadata. |
| Finance | Overview, Reconciliation | Aggregate overview loads; no saved reconciliation run exists. Primary/secondary actions are visually inconsistent under the global dark override. Use separate primary-action and link/accent tokens and compact panel spacing. |
| Advisor | Assigned clients, assigned Client detail | One assigned client is present. Search occupies a full panel and unnecessary vertical space. Client detail loads behind its own request state. Use an inline search row, shared panel rhythm, and theme-aware legacy screens. |

Additional observed issues: the universal 8px radius affects headers, table cells, and navigation in addition to cards; the login card is hardcoded white while inherited dark inputs remain active after logout; focused headings receive the global focus ring; blanket dark button text overrides can put dark text on dark outline buttons.

### Correction plan before implementation

1. Restore square corners; remove the decorative theme switch and use `Enable Dark Mode` / `Disable Dark Mode` text.
2. Remove the blanket dark control/button overrides. Define explicit page, surface, text, border, primary action, secondary action, notice, and error colors, then apply them within the components that own those controls.
3. Tighten the shared spacing scale and fix the actual layout defects: market result columns, nested instrument padding, support status/filter layout, and wide forms with useful responsive groups.
4. Reproduce stock, crypto and FX market-data requests independently. SPY quote and candles returned HTTP 200 during this audit, so the reported failure is not assumed to affect every instrument.
5. Rebuild the local application, exercise every role again in light and dark modes, check button/input contrast and layout bounds, and run focused automated tests plus compiler/build checks. Record actual outcomes below.

### Implemented corrections and reasons

- **Square geometry:** Removed the universal 8px radius and all newly introduced card/control/tag rounding. The repository's square-corner check now passes; the shared loading spinner remains circular. Removed the added card shadows and hover movement from the first pass.
- **Theme action:** Account now contains a normal menu item labelled `Enable Dark Mode` or `Disable Dark Mode`. There is no switch graphic or checkbox role.
- **Theme ownership:** Moved persisted preference handling to `ThemeService`, initialized by the application root. Login, signup, profile, history, onboarding, reports, notices and form controls use semantic surface/text tokens. Primary action background (`--action-bg`) is separate from links/accent text (`--dark-green`), preventing light accent colors from producing unreadable white button labels. Removed the blanket dark-mode button-text rule that made outlined chart controls disappear. Storage-disabled browsers retain the in-memory theme.
- **Actual contrast defects found during testing:** The dark login button still used an accent color after the first correction; its foreground/background ratio was 1.65:1. Changed it to the dedicated primary action color and repeated the browser check successfully. Login placeholders now use the secondary text token. Profile status badges use paired success foreground/background colors. Analytics charts redraw when the theme changes, and candlestick colors follow positive/negative tokens.
- **Market rows:** The arrow had been explicitly placed in grid column 2, colliding with the instrument name. It now occupies its own final column. Results use compact rows with useful widths and adapt to a stacked layout on narrower screens. Default featured panels retain the three market groups. Featured loading now uses the cancellable page-resource lifecycle and reports errors instead of leaving an endless loading message.
- **Broad search:** Alphabetical paging could hide every crypto/FX match behind stock results. Cross-market searches now interleave matching market groups with SQL window ordering before pagination; explicit market filters retain ordinary ticker ordering. Counts still use the same filters. A live `B` search now includes stock/ETF, crypto and FX security types on page one, including Bitcoin.
- **Shared spacing:** Reduced nested heading, panel, action and notice spacing. Support has a heading/action row, a compact updates summary, and a single filter toolbar containing Search and Refresh. Checkbox filters keep native 16px dimensions. Support intake uses the available width and two columns for related fields; prose areas remain full width.
- **Banking:** Balance information is compact, saved accounts and cash-movement forms share the desktop width, and the layout becomes one column on small screens. Field hints stay directly beside their related controls. Empty status messages no longer consume a grid row.
- **Operations editor:** Identity and limits sit beside each other; permissions and audit reason use the full width below. Existing non-preset quote ages and legacy venue labels remain selectable when editing. Decimal limits use decimal keyboards while preserving exact string input, avoiding JavaScript number coercion.
- **Trade and profile:** Removed the extra page padding around embedded charts. Instruction entry and estimate review sit beside each other on desktop and stack on mobile. Removed nested profile/history page padding. Order eligibility, confirmations and request-key recovery continue to use their existing logic.
- **Narrow reports:** Real browser testing found both table-container minimum widths and long exact metric values widening Analytics pages. Added shrinkable report panels, a single metric column at narrow widths and wrapping for exceptionally long values. Tables scroll inside their own wrappers.

### Fauxnance investigation and changes

Live requests against the running local proxy succeeded for `SPY`, `X:BTC-USD` and `FX:EURUSD`, including both quotes and daily candles. The reported provider failure was therefore not reproducible as a persistent outage during this audit; there is no evidence supporting a claim that a UI layout change broke the external provider itself.

Made failure behavior robust without inventing prices: idempotent quote/chart reads retry once after 500ms for network failures or HTTP 500/502/503/504. Missing instruments and authorization errors are not retried. A failed refresh preserves the prior quote/chart where available. The UI shows a useful temporary-unavailability message. Backend transport timeouts now return 503 and upstream server failures return 502 instead of presenting them as unexplained application errors. No orders, banking movements, policy changes, advisory recommendations or evidence exports were submitted during browser inspection.

### Verification results

| Check | Outcome |
| --- | --- |
| Frontend unit suite | **340 passed** across 53 test files. Includes theme persistence/menu label, bounded market-data retry, chart/quote recovery, support, trade-ticket and role-page coverage. Two existing error-path tests were updated to exercise both retry attempts before manual recovery. |
| Focused backend suite | **21 passed**: quote service 6, candle service 7, catalog mapper 5, client workspace controller 3. New cases cover transport timeouts, provider 5xx classification, market/exchange filtering, matching row/count behavior, and cross-market first-page results. |
| Square-corner/style check | Passed. |
| Production builds | Frontend browser/SSR bundles and Java 21 backend image built successfully in Docker. The Analytics component retains a non-failing 4.50KB stylesheet warning against its 4KB warning threshold. |
| Runtime | Rebuilt local frontend/backend containers and restarted the local proxy. The application remains available on port 8087. |
| Six-role browser inspection | Signed in separately as Client, Operations, Compliance, Analyst, Finance and Advisor. Visited all **21 sidebar pages in both themes**, plus Client support intake/instrument/profile pages, Advisor client detail, login after logout, and authorized Operations/Compliance views. |
| Rendered contrast | Measured computed foreground/background colors for enabled controls and visible text on visited pages, checking 4.5:1 for normal text and 3:1 for large text. Hidden screen-reader labels and disabled controls are excluded from the visible-control conclusion. Repeated the found login failure after correction. This is focused rendered-page QA, not a claim covering every possible application state. |
| Responsive checks | Exercised a 375px viewport for Client pages, login, Operations editor/purpose gate, Analyst reports and Finance pages. Used actual DOM bounds/scroll widths, identified and corrected Analytics overflow, and restored the normal browser viewport afterward. |
| Live market-data checks | All six representative requests returned **HTTP 200**: SPY, Bitcoin and EUR/USD quotes plus candles. Candle responses contained 261, 367 and 261 rows respectively for the requested UTC range. |
| Live search check | `B`, page size 20, returned HTTP 200 with `COMMON_STOCK`, `ETF`, `CRYPTO` and `FX_PAIR` matches on page one; `X:BTC-USD` was present. |
| Patch hygiene | `git diff --check` passed. Existing staged catalog manifests were preserved. |

Empty support/review queues and the absence of stored reconciliation/order records limit inspection of populated detail states. The purpose-gated list/filter screens were inspected within the demo roles' existing account scope; existing automated support/order tests cover those state transitions without submitting financial actions through the browser. Browser audit records and screenshots were saved under `/tmp/tadpoles-ui-cleanup/` for this session.

## Final refactors — requested featured instruments and numeric display (2026-10-25)

### Analysis and decisions

- The featured cards previously filled gaps with unrelated catalog instruments. That made the requested three-per-column selection unpredictable. Define one ordered selection shared by loading and rendering, and show only matching real catalog rows.
- The initialized US manifest contained SPY but lacked QQQ and IWM. Append those two ETF reference entries and run the existing idempotent catalog seed. This added exactly two database rows. Existing trading eligibility and order policies continue to determine whether an instrument can be traded.
- Reuse the initialized provider identifiers: `X:BTC-USD`, `X:ETH-USD`, `X:BNB-USD`; `FX:EURUSD`, `FX:USDJPY`, `FX:GBPUSD`. Featured labels are BTC/ETH/BNB and EUR/JPY/GBP, with full instrument names explaining the Forex pair direction. Detail links retain actual instrument IDs; searches retain provider tickers.
- Inconsistent raw decimal strings appeared in balances, trade reviews, quantities, retained order evidence, currency estimates and reports. Formatting must happen at presentation time so rounding cannot change orders, validation, reservations, balances or stored evidence. Integer counts, IDs, dates and pagination remain integers.

### Implementation

- Client Markets now has exactly SPY/QQQ/IWM, BTC/ETH/BNB, and EUR/JPY/GBP, in that order. Removed the unnecessary broad catalog fetch and unrelated fallback selection.
- Added a reusable two-decimal display pipe. It rounds decimal strings using integer arithmetic, including negative amounts, exponent notation and values above JavaScript's safe integer range. It pads whole amounts with `.00` and avoids negative zero.
- Applied display formatting to Client account/banking/currency estimates/order receipts, trade history and review tickets, Advisor positions, Finance monetary totals, Analyst performance/allocation/quantity tables and chart labels, and Operations/Compliance order quantities and financial evidence fields. Existing quote/OHLC formatting already used two decimals; candle volume and explicit chart price precision now follow the same display convention. Profile net worth also always shows two decimal places.
- Mixed evidence views identify financial fields while preserving reference/version strings, IDs, counts, missing values and source objects. Captured values are formatted on a display copy; underlying evidence and API responses retain their original precision. Editable quantities and policy limits retain their full precision.
- Removed the sidebar tagline and its unused styling. Retained square corners and the existing light/dark theme behavior.

### Verification

- Frontend suite: **356 tests passed across 54 files**. New cases cover exact featured ordering, all nine real detail links, exclusion of unrelated matches, provider pair identity, decimal rounding/padding, exponent notation, large exact decimal strings and unchanged source values. Updated older display assertions, including net-worth choices, to expect two decimal places.
- Square-corner/style check and `git diff --check`: passed.
- Static monetary labels (bank movement limits, order-notional limits and net-worth ranges) also use two decimal places; their numeric form values remain unchanged.
- Production frontend build: passed; the existing non-failing Analytics stylesheet warning remains 4.50KB against a 4KB warning threshold. Refreshed the local frontend and proxy.
- Browser checks: confirmed all nine featured instruments in the requested order, the missing sidebar tagline, two-decimal Client account/banking balances, and two-decimal Analyst performance/allocation/quantity values. Checked report cells individually: none contained more than two fractional digits; integer fill/order counts remained integers. QQQ opened the newly seeded real instrument page with a live quote (`$704.40`) and bid/ask values to two decimals. Trading remains controlled by the existing eligibility rules.
- Screenshot: `/tmp/tadpoles-ui-cleanup/markets-final.jpg`. Left the Client Market page open for review. No financial actions were submitted.
