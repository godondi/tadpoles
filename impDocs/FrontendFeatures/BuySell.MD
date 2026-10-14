# Client buy/sell feature — implementation guide

> Reviewed: 2026-10-12. Source: [HailMary.md](../../HailMary.md), read in full, especially §§2, 4–5, 8–11, 13 and 15.
> This document describes the client experience, the code currently present, and the work needed to deliver it. **Only this documentation file was added; no backend, database or frontend behavior was changed.**

Use these labels throughout implementation:

- **CURRENT:** verified by reading the repository; this does not imply a passing runtime test.
- **REQUIRED:** a requirement carried from HailMary or the requested screen layout.
- **PROPOSED:** a concrete implementation recommendation, not an existing API or completed feature.
- **DECISION:** a business rule that HailMary deliberately leaves unresolved.

## 1. Outcome and scope

The client opens an instrument, sees its latest indicative price and chart, enters a buy or sell instruction, reviews its estimated effect, submits it once, and follows the persisted order to a fill or rejection. Cash, holdings and order history must agree after execution and after reload.

The desktop layout is mandatory: **chart on the left, buy/sell ticket on the right, current price above the ticket, and the remaining instrument information below the chart/ticket region.** Use the information hierarchy requested as similar to Robinhood; retain this application's existing visual styles and components. No dependency on Robinhood APIs, branding or trading behavior is implied.

HailMary distinguishes source requirements from design proposals. Apply that distinction here:

| Requirement | Feature responsibility |
|---|---|
| BR-02, Must | Only the authenticated client can act on their own account. |
| BR-04/05, Must | Buy/sell submission with authoritative cash, holdings and tradability validation. |
| BR-06, Must | Accepted intent is committed before execution starts. |
| BR-07, Should | Status changes arrive without manual refresh; reconnect recovers authoritative state. |
| BR-08/09, Must | Execution uses a current server-obtained quote; fill, cash and position effects commit atomically. |
| BR-10/11, Must | Accurate account state and chronological own-order/fill history. |
| BR-12, Must | Eventually demonstrate the agreed UK, US, India, FX and crypto coverage; a single equity demo is not full coverage. |
| BR-13, Should | Indicative price, currency, age and explanation before submission. |
| BR-14/15, Must | Durable, attributable original instruction, pricing decision and outcome evidence. |
| BR-18, Should | Pre-trade impact preview is HailMary's proposed extra feature, subject to selection/approval. |

Initial proposed delivery is a clearly identified simulator with quantity-based buy/sell and a single complete fill or rejection. Do not introduce advisor approval into self-directed client trading merely because legacy tables have approval fields. Limit/stop orders, partial fills, cancellation, margin, shorting, banking, automatic currency conversion and tax-lot calculations require separate decisions. Analytics remains downstream; it must never supply buying power or block the booking transaction.

## 2. What is implemented today

### 2.1 Architecture and reusable code

The repository has an Angular application with SSR configuration, a Java 17 / Spring Boot 3.3.5 backend, MyBatis annotation-based SQL, and PostgreSQL initialization scripts. Backend layers use controllers, record DTOs, service interfaces/implementations, mappers and a shared exception handler. Keep those patterns when introducing the order feature. The current Compose file runs frontend, backend, PostgreSQL and an auth stub; it has no Kafka/execution-worker/reporting services.

| Area | CURRENT behavior and evidence | Remaining work |
|---|---|---|
| Instrument screen | [TradeComponent](../../frontend/src/app/features/trade/trade.component.ts), its HTML/CSS, and `app.routes.ts` implement authenticated `/trade/:symbol`. | Add instrument-ID resolution, account context and an actual order ticket. The route guard checks authentication, not client trading permission. |
| Chart | `CandleChartComponent` renders daily OHLC history. The page sorts candles chronologically and displays the last 30, fetching a 90-calendar-day window. | Preserve loading, empty, limited-history, partial/stale and retry behavior. Do not relabel it an intraday chart. |
| Quote | [MarketDataService](../../frontend/src/app/core/api/market-data.service.ts) loads quotes separately from candles. The right panel shows price, change, bid/ask, spread, currency, market state, timestamps and source. Refresh is manual. | Move secondary details to the bottom. Add quote-age rules and refresh while the active ticket is visible. |
| Page resilience | Route changes cancel requests and reset displayed market data; quote-refresh failures preserve the old quote. Tests cover these behaviors. | Add draft/receipt isolation, account-change cleanup and an explicit unusable-price state after failed refresh or expiry. |
| Dashboard | [dashboard.ts](../../frontend/src/app/dashboard.ts) contains hard-coded account values/holdings. Its order modal calls `alert()`; it sends no trade request. | Route order entry into the real ticket and replace mock account reads for the delivered journey. An alert is not acceptance. |
| Trade history | [TradesService](../../frontend/src/app/trades.service.ts) is an in-memory fixture with fabricated commissions and market/limit labels. | Replace with authenticated order/fill reads. Do not append a fake completed trade after clicking Buy. |
| Identity | JWT security, auth interceptor, `/api/users/me` and client onboarding/profile endpoints exist. | Resolve the account from trusted backend identity; cached profile IDs are navigation context only. |
| Trade persistence | [ClientTradeServiceImpl](../../backend/src/main/java/com/neueda/leap/service/impl/ClientTradeServiceImpl.java) creates/lists/reads/patches `client_trades`. It checks positive values, client/instrument existence, side and allowed status values. | It does not validate cash/holding sufficiency or active tradability before creation, reserve resources, deduplicate submissions or schedule execution. |
| Booking | [OrderFillServiceImpl](../../backend/src/main/java/com/neueda/leap/service/impl/OrderFillServiceImpl.java) uses `@Transactional`, locks trade/client/latest holding, checks cash or quantity and inactive instruments, updates holdings/cash and marks the trade `EXECUTED`. Full sale deletes the selected holding row. | Reuse the local transaction principle, but add authoritative pricing, immutable fill/evidence records, reservation handling and idempotent results. |
| Schema | [001_tadpoles_schema.sql](../../database/schema/001_tadpoles_schema.sql) contains clients, holdings, trades, instruments and generic audit logs. | No order reservations, cash currency accounts, order idempotency keys, execution quotes, fills, financial journals or outbox tables. |
| Tests | Java controller/service/mapper tests; two H2 booking integration tests; separate PostgreSQL schema tests; Angular component/service tests. | No evidence here of the complete browser → accepted order → worker → execution quote → booked fill journey. |

### 2.2 Important differences from HailMary

1. **`PENDING` is not proof of HailMary acceptance.** Current creation persists a row but does not perform BR-05 admission checks, reserve resources or dispatch execution. States are `PENDING`, `APPROVED`, `REJECTED`, `EXECUTED`, rather than the proposed lifecycle. Do not relabel legacy `PENDING` as “Accepted” without adding the guarantees.
2. **The browser can currently influence execution facts.** Create accepts price, trade date, submitted/approved user IDs and status. PATCH accepts status, execution time and attribution. The fill request can supply price/time; otherwise booking uses the stored trade price. No execution-time quote service is called.
3. **Authentication is broader than the required authorization.** `SecurityRoleSupport.requireClientOwnership` checks ownership when the JWT contains `CLIENT`, but returns without an ownership check for other authenticated roles. Trade endpoints require only `isAuthenticated()`. Staff-only and roleless callers are therefore not explicitly excluded from this client action surface.
4. **Other writes bypass order invariants.** Current client PATCH accepts `cashBalance`; holdings POST/PATCH alter positions directly; instrument POST/PATCH require authentication only. Hiding these controls in Angular does not prevent API calls. Restrict these routes as part of enabling client trading.
5. **Atomic booking is a useful foundation, not complete order integrity.** A second normal fill of an `EXECUTED` row is rejected, but repeated order creation can make another row. Mutable status can bypass that guard. There is no stable idempotency key or immutable execution identifier.
6. **Cash has no currency and holdings have snapshot ambiguity.** `clients.cash_balance` is `NUMERIC(18,2)` with no currency field. `client_holdings` is unique on `(client_id, instrument_id, as_of_date)`, so several dates may exist; the fill method uses only the latest row. Summing those rows would overstate ownership, and deleting the latest after a full sale can expose an older snapshot as current.
7. **Price/quantity storage is not a universal asset rule.** Trade price is `NUMERIC(10,2)` and quantity `NUMERIC(18,6)`; booking rounds consideration to two decimals with `HALF_UP`. These choices are not proof of correct FX/crypto precision, currency rounding or UK pence conversion.
8. **Evidence and delivery are incomplete.** Generic audit storage exists, but the trade create/fill methods do not write a complete pricing/order/journal trail. There is no implemented order event stream, preflight API, durable worker/outbox path or broker routing in the reviewed sources.

These are implementation findings, not claims that the existing tests fail. This documentation review did not execute application tests.

## 3. Existing endpoint inventory

The paths below come from controllers, including the actual prefixes. [docs/api.yaml](../../docs/api.yaml) explicitly mixes implemented and planned routes and sets a `/api` server root; do not treat every entry as available. For example, auth login is actually `/auth/login`, and proposed `/api/v1/orders` routes in HailMary do not exist today.

### 3.1 Reads used by this screen

| Method and actual path | CURRENT contract | Integration instruction |
|---|---|---|
| `GET /api/users/me` | User fields, `roles`, nullable `clientId`, `advisorId`. | Establish enabled client/account context; no hard-coded client ID. |
| `GET /api/users/me/profile` | Client onboarding/profile details; requires `CLIENT`. | Use existing onboarding flow when no client account is associated. |
| `PUT /api/users/me/onboarding` | Completes client onboarding. | Reuse onboarding; do not create account/funds from the ticket. |
| `GET /api/instruments` | `{ "instruments": [...] }`; fields include `instrumentId`, `instrumentName`, `ticker`, `currency`, `assetClass`, `securityType`, `isActive`. | Normalize the route symbol and require exactly one matching instrument. No matching catalog record means view-only market data. No current server search/filter contract. |
| `GET /api/instruments/{instrumentId}` | One instrument DTO. | Refresh metadata/tradability; use numeric instrument ID for commands. |
| `GET /quotes/{symbol}` or `GET /api/quotes/{symbol}` | `{ data, meta }`; data contains `price`, `bid`, `ask`, `spreadBps`, nullable currency/change/previousClose, `asOf`, `marketState`. Metadata contains source, disclaimer, spread source and stale flag. | Current Angular service uses `/quotes`. Preserve provenance; do not treat nullable or missing values as zero. |
| `GET /candles/{symbol}` or `GET /api/candles/{symbol}` | Optional `from`, `to` dates and `interval`; current screen sends `1d`. Returns `{ data: { symbol, interval, currency, candles }, meta }`. | Candle dates and synthetic/partial/stale indicators describe history, not an execution price. |
| `GET /api/clients/{clientId}` | Client data including `cashBalance`. | Own-account read; not currency-aware buying power. |
| `GET /api/clients/{clientId}/balance` | `{ clientId, cashBalance }`. | No reserved/available amounts, currency or as-of timestamp. Do not fabricate them. |
| `GET /api/clients/{clientId}/holdings` | `{ "holdings": [...] }` with holding ID, client ID, instrument ID, quantity, as-of date. | Historical-row semantics must be resolved before displaying available-to-sell. |
| `GET /api/clients/{clientId}/holdings/{holdingId}` | One holding DTO. | Own-account scope; not a reservation-aware position API. |
| `GET /api/clients/{clientId}/trades` | `{ "trades": [...] }`, ordered by `trade_date DESC, trade_id DESC`. | No pagination/filter parameters implemented; dates are not acceptance timestamps. |
| `GET /api/clients/{clientId}/trades/{tradeId}` | One trade DTO. | Can read legacy status; there is no linked fill/timeline response. |

All these reads require authentication; the ownership limitation in §2.2 applies to client-ID routes. `GET /api/users/me/profile` has its additional client-role restriction. Existing DTO decimal values are Java `BigDecimal`; current Angular market-data models use JavaScript numbers. New financial command/response contracts below deliberately use decimal strings.

### 3.2 Existing writes — compatibility reference, not the target client workflow

| Method and path | CURRENT behavior | Required treatment |
|---|---|---|
| `POST /api/clients/{clientId}/trades` | `201`; inserts a trade. Requires instrument ID, BUY/SELL, positive quantity/price and trade date; status defaults to `PENDING`. | Replace or route through secure order intake before connecting the ticket. |
| `PATCH /api/clients/{clientId}/trades/{tradeId}` | `200`; mutates attribution/status/executedAt/reason. | Remove client ability to manufacture outcomes or reopen executed trades. |
| `POST /api/clients/{clientId}/trades/{tradeId}/fill` | `200`; optional body containing `approvedByUserId`, `price`, `executedAt`, `reason`. Returns `clientId`, `cashBalance`, `holdingId`, `holdingQuantity`, `tradeId`, `tradeStatus`, `executedAt`. | Move execution into a backend-owned workflow. A client must not choose a fill price. On full sale, returned holding ID may refer to the deleted row; re-read authoritative positions. |
| `POST /api/clients/{clientId}/holdings`, `PATCH .../holdings/{holdingId}` | Creates/updates holdings directly. | Reserve for controlled setup/corrections under explicit authorization, not client order entry. |
| `POST /api/clients`, `PATCH /api/clients/{clientId}` | General client creation/update, with cash fields in relevant DTOs. | Audit and restrict funding/account mutations; use existing self-onboarding for clients. |
| `POST /api/instruments`, `PATCH /api/instruments/{instrumentId}` | General instrument creation/update. | Restrict to authorized reference-data administration. |

Current `CreateClientTradeRequestDto` fields, for recognizing legacy callers:

```text
instrumentId, submittedByUserId?, approvedByUserId?, tradeType,
quantity, price, tradeDate, status?, reason?
```

Current application errors use `{ timestamp, status, error, message, path }`: validation generally maps to `400`, missing resources to `404`, authentication to `401`, and client ownership mismatch to `403`. There are no order-specific reason codes, conflict/recovery semantics or correlation IDs in that DTO yet. Never classify an HTTP timeout as a definite rejection.

### 3.3 Adjacent APIs and boundaries

Authentication includes `POST /auth/login`, `/auth/signup`, `/auth/register/client`, protected `/auth/register`, and `POST /api/auth/refresh`, `/api/auth/logout`. Other implemented controller families include users/roles, advisors, client subscriptions, trade suggestions, model portfolios/holdings, refresh-token management and audit-log reads. These do not form the client buy/sell transaction.

In particular, advisor suggestions and `approvedByUserId` are legacy portfolio features, not required approval steps for self-directed orders. Global `/api/trades`, analytics/dashboard routes listed in OpenAPI, and HailMary's balances/orders/preflight/stream examples must not be called until corresponding implementations and contract tests exist.

## 4. Screen design and client instructions

### 4.1 Desktop and responsive layout

```text
Instrument name · symbol · currency · trading availability
┌────────────────────────────────────┬────────────────────────────┐
│ Daily price chart                  │ Current indicative price   │
│                                    │ Change · currency · as-of   │
│ Existing 30 daily candles          │ Refresh · freshness notice │
│                                    ├────────────────────────────┤
│                                    │ Buy | Sell                 │
│                                    │ Quantity                   │
│                                    │ Cash / available to sell   │
│                                    │ Estimated total and impact │
│                                    │ Review → Submit            │
│                                    │ Order receipt / status     │
└────────────────────────────────────┴────────────────────────────┘
┌─────────────────────────────────────────────────────────────────┐
│ Instrument details · bid/ask/spread · previous close             │
│ Market/source information · your position · recent own orders   │
└─────────────────────────────────────────────────────────────────┘
```

Retain the existing two-column grid (`minmax(0, 1fr) 340px`, 32px gap) as a starting point. Put the price and ticket in one right-hand column with price first. Add a full-width details section beneath both columns. Optional sticky behavior must keep the ticket reachable below the top navigation and must not obscure controls on short screens.

At the existing 850px breakpoint, stack chart, price/ticket, then details. Keep price immediately above the ticket. Use readable labels, visible focus, keyboard-operable side selection, associated validation messages and a polite live region for order status. Do not announce every quote tick or communicate side/status using color alone. Check the layout at desktop, 850px, narrow phone width and browser zoom; DOM tests alone cannot prove element placement.

### 4.2 Price and bottom information

- The headline uses the latest quote's `data.price`, currency and change. Label it **indicative** and display quote time/timezone, source availability and freshness. Missing currency means “Currency unavailable,” not USD.
- Ticket estimates use the quote's **ask for Buy** and **bid for Sell**, under the selected pricing policy. The headline price, chart close and indicative estimate must never be submitted as the authoritative execution price.
- Keep a readable “Execution price may change” notice above review/submit. Display educational/simulated and modelled-spread disclosures from the provider contract. `meta.stale === false` is insufficient on its own: the current proxy defaults an absent stale flag to false, so validate timestamp age and quote completeness independently.
- Move bid, ask, spread, previous close, full source/spread-source fields and market details into the bottom area. Show instrument name, symbol, asset class, security type, currency and active status from the instrument catalog.
- “Your position” shows posted, reserved and available quantities only when the new account read contract supplies them. Recent orders come from authenticated persisted records. Hide unsupported company biography, P/E, market cap, news and fundamentals; no such endpoints were found. Candle-derived statistics must state their actual period/date, not claim live daily statistics.
- Chart failure can leave quote/ticket usable if the instrument and authoritative preflight are valid. Quote/account/permission failures disable submission. An existing quote retained after refresh failure remains clearly marked as previous data and cannot silently keep a ticket executable.

### 4.3 Buy journey

1. Sign in and open `/trade/:symbol` from instrument search or a holding. Resolve the instrument and authenticated client. Show a view-only explanation for a quoted symbol that is absent/inactive in the tradable catalog.
2. Select **Buy** and enter a quantity in the instrument's defined unit. Start with quantity ordering; do not add a dollar-amount toggle without an approved conversion/rounding contract.
3. Show available cash in the matching currency, indicative ask, estimated cost, approved fee treatment, projected available cash and projected holdings. Clearly mark every projection as an estimate.
4. Select **Review buy**. Request server preflight and present instrument, quantity, currency, estimated consideration, fee assumptions and price-movement policy. Editing side/quantity/instrument invalidates this review. A preview makes no reservation and creates no accepted order.
5. Select **Submit buy** once. Freeze that request snapshot, create one idempotency key, disable duplicate submission and retain the key until the outcome is known.
6. On durable acceptance, show order ID and “Accepted for processing; execution is not yet confirmed.” On fill, show actual quantity, execution price, total and time, then refresh cash, holdings and history. On rejection, explain the reason and confirm the authoritative account refresh.

### 4.4 Sell journey

Use the same review and submit flow with **Sell**, indicative bid and estimated net proceeds. Show **available to sell**, not a sum of historical holdings. Reject zero/negative/excess quantities and unsupported precision in the form and again on the server. If nothing is available, explain why and keep Sell submission disabled. A sell-all shortcut, if added, uses the latest available quantity and still receives server revalidation; it is not an exemption from quantity rules.

Accepted sells reserve quantity. Only a committed fill reduces posted holdings and credits cash; rejection releases the reservation. A complete sale displays zero current quantity and must not revive an older holding snapshot.

### 4.5 UI states and recovery

| State | Client display and allowed action |
|---|---|
| Loading context | Independent placeholders for chart, quote and account; submit disabled. |
| Draft | Editable side/quantity with field errors and indicative estimates. |
| Reviewing | Server preflight in progress; duplicate review disabled. |
| Ready to submit | Review of one frozen draft; submit enabled only while required context remains valid. |
| Submitting | “Submitting…”; one in-flight command. No optimistic filled balance/position. |
| Accepted / Executing | Persisted order ID, status and elapsed context; track backend outcome. |
| Filled | Actual fill receipt and authoritative account/history refresh. |
| Rejected | Stable reason and readable explanation. An explicitly revised order gets a new key. |
| Unknown / Recovering | “Checking whether your order was received.” Lookup or retry the identical command with its original key; never automatically submit with a new key. |
| Session expired | Reauthenticate; already accepted orders continue server-side. Resume lookup after sign-in to the same account. |

On symbol changes cancel reads and discard unsubmitted drafts/reviews. Unsubscribing from an HTTP POST does **not** prove the server cancelled the order: retain its account-scoped recovery reference and keep it discoverable in history. Clear private caches/subscriptions on account switch/logout; never show one account's recovery state to another. If reload recovery uses session storage, store only the minimal account-scoped key/order reference and frozen command needed for recovery, never credentials; clear it when resolved. A refresh failure after a known fill means “Filled; account data refresh delayed,” not “Order failed.”

## 5. Proposed implementation contract

### 5.1 Frontend structure

Extend `features/trade` instead of adding another competing trade page:

- Keep `TradeComponent` responsible for route/instrument/account orchestration and composing the page.
- Keep `CandleChartComponent` reusable. Extract a compact price summary, an order-ticket component and a bottom instrument-details section as complexity requires.
- Add typed `InstrumentService`, `AccountService` and `OrderService` under `core/api`, following existing URL-building and auth-interceptor conventions. Financial form values and new API monetary/quantity fields use decimal strings; server `BigDecimal` calculations remain authoritative.
- Use a reactive form for side/quantity and explicit state for preview/submission/receipt. Avoid duplicated BUY and SELL handlers with different validation rules.
- Reuse server preflight calculations instead of writing a separate authoritative financial calculator in Angular. Formatting may use display conversions only where precision is preserved.
- Replace the relevant mock dashboard/history paths with shared authoritative reads and refresh/invalidation after committed outcomes. Do not introduce local simulated success as a fallback for unavailable APIs.
- Run timers and subscriptions only in the browser, stop them on destruction/visibility changes as appropriate, and account for the existing SSR setup. Do not place authenticated account data in a shared server-render cache.

### 5.2 New client routes — PROPOSED, not currently callable

Prefer an additive `/api/orders` surface and `/api/users/me/...` account reads, matching existing `/api` conventions. Keep integer instrument IDs. This avoids extending the legacy trade CRUD body into a larger mass-assignment interface. Existing trade reads may remain as a compatibility/history projection, but disable unsafe write bypasses before enabling the new ticket.

| Proposed endpoint | Contract |
|---|---|
| `GET /api/users/me/balances` | Per-currency `posted`, `reserved`, `available`, `asOf`; all monetary amounts decimal strings. |
| `GET /api/users/me/positions` | One current row per instrument with posted/reserved/available quantity and `asOf`; optional explicit instrument filter. |
| `POST /api/orders/preflight` | Validates an owned account and instrument; returns non-binding estimates, rule/version context, quote time, expiry and reasons. No booking/reservation. |
| `POST /api/orders` | Required `Idempotency-Key`; authorize client/account, validate/reserve, commit immutable instruction + acceptance event + outbox, then return `202` with ID/status/status URL. |
| `GET /api/orders?cursor=...` | Paginated own history with stable `(submittedAt, orderId)` ordering and explicit instrument/status filters if implemented. |
| `GET /api/orders/{orderId}` | Current state, submitted/accepted times, original instruction, rejection reason and actual fills. Enforce ownership. |
| `GET /api/orders/by-idempotency-key/{key}` | Recover an own-account submission result. Resolve this explicit route without collision with `{orderId}`. |
| `GET /api/orders/{orderId}/events` | Ordered, client-safe lifecycle events with sequence/cursor. |
| `GET /api/users/me/order-events/stream` | Authenticated SSE status stream with replay cursor; polling reads provide fallback. |

Illustrative new order command (IDs/amounts are examples):

```http
POST /api/orders
Authorization: Bearer <access-token>
Idempotency-Key: <one-key-for-this-confirmed-instruction>
Content-Type: application/json

{
  "instrumentId": 11,
  "side": "BUY",
  "quantity": "2.500000",
  "orderType": "MARKET"
}
```

`MARKET` is conditional on an approved market-style simulator/risk policy. Reject unapproved order types. The body contains no client ID, actor ID, price, execution time, status or approval identity. Reject prohibited fields rather than letting a caller assume they took effect. Resolve account/actor and record timestamps on the server.

```json
{
  "orderId": "server-generated-id",
  "status": "ACCEPTED",
  "acceptedAt": "2026-10-12T14:00:00Z",
  "statusUrl": "/api/orders/server-generated-id",
  "message": "Order accepted for processing. Execution is not yet confirmed."
}
```

Idempotency is account-scoped and database-enforced: same key plus normalized command returns the original result; same key plus different command returns `409`. Concurrent same-key requests must resolve to one result. Persist enough submission/rejection evidence to distinguish a failed pre-check from an accepted instruction; rejected admission has no `acceptedAt`. Document whether replay returns the original response or latest state and test that exact policy. A lookup miss during an in-flight request is not authorization to generate a new key.

Add order reason codes such as `INSUFFICIENT_CASH`, `INSUFFICIENT_HOLDINGS`, `INSTRUMENT_NOT_TRADABLE`, `QUOTE_UNAVAILABLE`, `QUOTE_STALE`, `CURRENCY_MISMATCH`, `INVALID_QUANTITY` and `PRICE_MOVED`, plus a correlation ID and optional field errors. Specify admission errors separately from a persisted order's later rejection. Keep `401` for missing/expired credentials, `403` for forbidden roles, non-disclosing resource access errors, `409` for conflicting key reuse, and a documented validation response (proposed `422`) for admission rules.

Extend CORS allowed headers for `Idempotency-Key`; currently only Authorization and Content-Type are allowed. Reuse bearer authentication for streams: native `EventSource` does not let this application attach its existing Authorization header, so use an authenticated streaming transport or a deliberately designed session mechanism. Never put JWTs in stream URLs. Apply per-account filtering, sequence deduplication and current-state refetch on reconnect; a push event is not the source of truth.

## 6. Backend/database changes needed before enabling submission

**No such changes were made for this documentation task.** The layout can be implemented without a database change. Enabling a safe, HailMary-aligned order workflow requires the changes below; merely wiring the current POST and `/fill` calls together in Angular would preserve the defects in §2.2.

### 6.1 Authorization and server-owned facts first

Require an enabled `CLIENT` with a mapped account for order entry, then derive account/actor from authentication and recheck ownership on reads, retries and streams. Explicitly deny staff-only/roleless principals rather than relying on the current ownership helper's fall-through behavior. Keep internal reads/admin actions separately authorized. Restrict legacy trade writes, fill, balance updates, holding CRUD and instrument mutations so none can bypass the new rules.

Why this approach: one secure command boundary makes pricing and attribution authoritative and prevents the browser from manufacturing fills. Removing controls from HTML alone cannot enforce any of these invariants. Do not replace unrelated authentication infrastructure just to implement this screen.

### 6.2 Durable intake and execution

1. Fetch/check admission pricing under a documented freshness policy, avoiding provider HTTP calls while holding financial row locks. Inside the acceptance transaction revalidate its age, lock the account resources, validate instrument/quantity/currency/rules, reserve cash or units, persist the immutable instruction, append acceptance/decision evidence and insert an execution-request outbox event.
2. Commit before responding “Accepted” or dispatching execution. Make the outbox publisher retryable; an in-memory executor alone loses pending work on process failure. HailMary proposes Kafka, but neither the broker nor its dependencies are presently installed in this project.
3. A backend execution worker claims accepted work and fetches a fresh permitted execution quote. Validate instrument, source, timestamp, side, currency, session and price bounds. Use ask for buy and bid for sell under the selected policy; never use chart close or client price.
4. Book fill, immutable quote/fill evidence, cash/position journals, current balances, reservation consumption and terminal status/audit/outbox in one local PostgreSQL transaction. Enforce unique execution/booking IDs and legal state transitions under locks.
5. On final rejection, commit reason/evidence and release reservations together. Bound retries for transient failures and surface stuck orders. For future external venues, an ambiguous execution must be reconciled using the same external reference before any resubmission.

Keep the current local transactional booking structure, but separate external I/O from locked booking and retain immutable original instructions instead of overwriting their price. This satisfies the cash/holding/trade atomicity requirement without distributing the three writes across services.

### 6.3 Minimal data responsibilities and migration plan

| Proposed change | Why needed / preferred approach |
|---|---|
| Orders with account, immutable instrument/side/quantity/type, currency, status, submitted/accepted timestamps, rule version, key and command hash | Existing trades conflate requested and executed facts. Unique `(client_id, idempotency_key)` closes duplicate-intake races. Use additive order tables and link legacy executions rather than inventing missing historical acceptance evidence. |
| Cash accounts per `(client_id, currency)` and cash reservations per order | Existing unlabelled cash cannot fund multi-currency orders safely. Track posted/reserved/available amounts explicitly; never infer currency from country or current ticker. |
| One authoritative position per `(client_id, instrument_id)` plus sell reservations | Removes the dated-snapshot ambiguity and resurrection after full sale. Keep historical snapshots separate. Reconcile opening quantities before migration; do not sum snapshots. |
| Instrument rules/provider mapping | Add explicit quantity step/scale, price tick/scale, venue/session and currency-unit conventions as needed. Global ticker uniqueness currently exists but is not a long-term multi-venue identity model. |
| Immutable quote observations, fills and cash/position journals | Preserve execution provenance and reconstruct every effect without browser state. Enforce positive fills, references and unique booking IDs; no normal client edits/deletes. |
| Order events, rule decisions, outbox and consumer deduplication | Recover accepted work after restart, explain outcomes and publish only committed facts. Generic audit rows alone do not implement this lifecycle. |
| Decimal precision, checks and UTC timestamps | Define asset/currency-specific rounding and storage precision from approved rules. Validate scale before persistence so the database cannot silently round an invalid instruction. Use instant-based timestamps for new events. |

For a deliberately limited first simulator slice, an explicit single cash currency with incompatible instruments disabled can defer full currency accounts. That is a scoped demo, not BR-12 completion. It still requires deduplication, safe ownership, server pricing, durable processing and correct current holdings. Unknown fees must be displayed as unknown; zero fees require a documented simulator policy.

Introduce versioned, reviewed migrations; initialization SQL only runs for a fresh PostgreSQL data directory. Editing `001_tadpoles_schema.sql` alone will not upgrade existing volumes. Use expand → validated backfill/reconciliation → switch reads/writes → later cleanup. Preserve legacy records and document gaps in their provenance. Do not delete/recreate a volume to make migration tests pass. Coordinate mapper SQL, DTOs, OpenAPI, H2 fixtures and PostgreSQL tests, and test both a fresh install and upgrade from representative existing data.

Code rollback must remain compatible with the expanded schema. If rollback could lose acknowledged orders, disable further intake and reconcile durable work instead of rolling back financial records. Financial corrections create linked compensating entries; they do not overwrite history.

### 6.4 Decisions required for executable policy

Resolve HailMary D-01–08, D-17, D-20 and D-23 as applicable: simulator/sandbox/live tier, provider eligibility, instruments/venues, supported market order semantics, quantity/precision, cash reservation and price-movement rule, no-short/no-margin policy, fees/rounding, internal booking meaning, test funding and market calendars. Record rules with owners/version and matching examples/tests.

In particular, a buy must have an approved reservation/maximum-spend policy or an explicit execution-time insufficiency rejection policy. Do not invent a slippage percentage, quote TTL or universal two-decimal rule to satisfy a test. Preserve the ability to demonstrate layout and deterministic simulator fixtures while those business policies remain marked as decisions.

## 7. TDD procedure and regression strategy

### 7.1 Establish the baseline

Record the revision, environment, commands and results before implementation. Read existing tests as behavior evidence, then compare their expectations to HailMary and the approved contract. Some tests characterize legacy permissive writes; those behaviors must intentionally change rather than become permanent requirements.

Existing relevant suites:

- Frontend: `trade.component.spec.ts`, `candle-chart.component.spec.ts`, `market-data.service.spec.ts`, auth and dashboard tests.
- Backend: `ClientTradeControllerTest`, `ClientTradeServiceImplTest`, `OrderFillServiceImplTest`, `OrderFillServiceIntegrationTest`, ownership/security tests and related mapper tests.
- Database: `SchemaConstraintValidationTest`, `SchemaBusinessFlowQueryTest` in the separate `testing` module.

**Known test infrastructure mismatch:** `AbstractPostgresIntegrationTest` searches for `database/schema/tadpoles_schema.sql`, which is absent; the actual file is `database/schema/001_tadpoles_schema.sql`. Repair the test loader to use the canonical schema/migrations before relying on that suite. The schema suite uses PostgreSQL 16 while Compose uses PostgreSQL 15; align or deliberately test both. Do not create a second drifting schema merely to satisfy the old filename. The Compose testing service is currently commented out.

The existing booking integration tests use **H2 in PostgreSQL mode**, proving a buy commit and rollback on a later trade-update failure. They do not establish production PostgreSQL locking, concurrent idempotency, oversell or restart recovery. Add real PostgreSQL service integration tests using independent connections/transactions for concurrency cases.

### 7.2 Red → green → refactor, one behavior at a time

1. Choose one externally observable behavior and name its BR/invariant, fixtures and expected result. Example: two concurrent identical confirmed requests create one accepted order and one reservation.
2. Write the smallest test at the appropriate layer. Execute it and establish that it fails for the intended missing behavior, not a compiler error, unavailable Docker, stale schema or wrong fixture.
3. Implement the smallest production change that enforces the rule. Put transaction/ownership rules in the backend and database, not just a disabled button.
4. Run the new test and the directly affected regression tests. Assert outcomes and stored records, not incidental private method calls or component structure.
5. Refactor once green: remove duplication, retain clear transaction boundaries and rerun relevant tests. New behavior gets another red/green cycle.
6. Complete the integrated buy/sell slice, then run backend/database/frontend regression and browser acceptance checks. Record results and unresolved limitations in the change description; a documentation checkbox is not test evidence.

Suggested order: repair baseline harness → secure write boundaries → current position/currency model → admission/idempotency/reservations → fresh-quote execution/atomic booking → read/status APIs → ticket/review/recovery → dashboard/history integration → restart/concurrency/accessibility acceptance. The visual layout can progress independently behind disabled submission while command guarantees are built.

### 7.3 Test matrix

| Test focus | Required evidence |
|---|---|
| Layout and retained chart behavior | Price above right-hand ticket; other details below both columns; responsive stacking; existing candle sorting/loading/empty/error/refresh behavior still works. Use real-browser layout checks. |
| Quantity and review | Empty/zero/negative/malformed/excess-scale/step values rejected; side and symbol changes invalidate review; available quantity/cash cannot be bypassed through requests. |
| Authorization | Client A cannot read/submit/recover/stream B's orders; staff-only/roleless identities cannot trade; attempts to set price/status/actor/cash/holdings through legacy routes are denied. |
| Buy and sell | With synthetic no-fee USD fixtures: buy 2 at execution ask 51 debits exactly 102 and adds 2; sell 1 at bid 49 credits 49 and removes 1. Assert journals, reservation, fill and current projections together. |
| Full sale | Zero current position remains zero even with older dated legacy snapshots; no duplicate current rows. |
| Admission and currency | Insufficient cash/units and inactive/unknown instruments fail before acceptance; null/mismatched currency cannot silently become USD or convert funds. |
| Execution quote | Preview differs from execution; actual price uses new ask/bid. Stale/missing/future-invalid time, wrong instrument/currency and invalid spread cannot produce a fill. Inject a clock and deterministic provider fixtures. |
| Duplicate and concurrent commands | Double click, same-key HTTP retry and parallel same-key requests produce one order/effect. Different payload with same key conflicts. Distinct concurrent buys/sells cannot over-reserve resources. |
| Rollback and replay | Inject failure after a financial write and before final status/audit: nothing partial commits. Replayed execution gives one fill/journal group and returns the known result. |
| Durable processing | Stop worker after acceptance; restart and process once. Lose response after commit; original key recovers the order. Outbox/publisher outage retains intent. |
| Status and navigation | Polling/push converges after disconnect, ignores stale/duplicate events, survives reload, stops after terminal state, and does not cross accounts or instruments. |
| Session/account refresh | Expiry during submission leads to recovery after sign-in, not a new instruction. Fill followed by account-read failure remains visibly filled with a retryable refresh. |
| History and evidence | Own filled and rejected records persist across restart with ordered events, execution quote and linked effects; pagination is stable; mock history/commissions never appear as actual fills. |
| Migrations and precision | Existing data backfill reconciles, constraints hold, initial install and upgrade agree; boundary decimal values either persist exactly or fail explicitly. |

Use provider stubs for deterministic tests; tests must not require live prices or real trades. Concurrency tests need synchronized starts and database assertions, not arbitrary sleeps. Browser tests must check keyboard operation, focus/error announcements and visible positions; jsdom does not establish visual layout.

### 7.4 When regression or implementation tests go in the wrong direction

Stop expanding the change and reduce the failure to a reproducible case. Save the failing command, assertion, seed, relevant sanitized logs and revision; compare to the recorded baseline. Classify the cause before editing assertions:

| Failure type | Correct response |
|---|---|
| New code broke supported behavior | Keep the regression assertion. Isolate the offending change and fix/revert that implementation in a small step; rerun the original regression and the new feature test. |
| Existing test encodes an unsafe/obsolete contract | Cite the requirement and approved new contract. Replace the expectation deliberately and add the missing invariant/denial coverage. Example: a test accepting client-supplied `EXECUTED` must become a denial test, not disappear. Update affected DTO/OpenAPI/consumer expectations together. |
| New test asserts an internal detail | Rewrite it around client-visible behavior or persisted effects. Moving the quote details should not fail solely because a CSS class/private helper changed; price placement still needs browser evidence. |
| Incorrect fixture or schema | Correct the fixture to model the requirement and migration, then prove the intended red failure again. Do not seed impossible balances or reuse the implementation's formula as the only expected-value oracle. |
| Environment/harness failure | Repair missing dependencies, Docker access, schema path or version mismatch separately. Record the test as blocked/not run, not passed. Do not change trading logic to compensate. |
| Timing/flaky test | Reproduce with fixed clock/provider and explicit synchronization. Remove shared state or await committed transitions; do not extend sleeps or retry until green to hide a race. |
| Security, duplicate booking, negative funds or partial commit | Treat as a release blocker. Disable new intake where necessary, preserve order evidence and reconcile effects. Fix the invariant and add a deterministic reproduction before resuming rollout. |
| Requirement itself is ambiguous | Write down the competing expected behaviors and obtain the relevant business-rule decision. Continue independent layout/refactoring work, but do not silently choose a financial rule to make assertions pass. |

Never delete/skip a failing test, lower thresholds, weaken ownership, remove locks, catch-and-ignore booking errors or mock away the database merely to obtain green output. A legitimate contract change may change a test, but its replacement must strengthen or preserve the intended guarantees and explain the change. If the approach is accumulating patches, return to the last known-good implementation while preserving the new failing behavior test, then deliver a smaller vertical slice.

After the repair, run the reproducer → affected suite → relevant integration/contract suites → full feature acceptance. Repeat broader testing when the repair affects shared code or another failure appears, not as a substitute for diagnosis. Record remaining baseline failures separately. README requires a peer review and passing Jenkins checks for PRs.

### 7.5 Repository commands and CI evidence

Run from the repository root after installing the project's declared Java/Maven and Node/npm dependencies; Docker is required for Testcontainers. These are instructions for the implementation stage, **not commands reported as executed for this document**:

```bash
# Existing focused backend tests
mvn -B -f backend/pom.xml -Dtest=ClientTradeControllerTest,ClientTradeServiceImplTest,OrderFillServiceImplTest,OrderFillServiceIntegrationTest test

# Backend regression and coverage, matching the Jenkins backend stage
mvn -B -f backend/pom.xml clean verify -Djacoco.skip=false

# PostgreSQL schema tests, after repairing the schema loader
mvn -B -f testing/pom.xml test

# Frontend regressions and production compilation
npm --prefix frontend test -- --watch=false
npm --prefix frontend run test:qof-styles
npm --prefix frontend run build
```

Add the new order tests to the focused invocation as they are introduced. Jenkins currently runs backend verify, database integration, image build and a backend startup smoke check; it does not run Angular tests or browser order acceptance. Add those gates for the delivered feature. Starting the Spring process is not evidence of a working trading flow. Archive test reports and migration/reconciliation results and distinguish passed, failed, skipped and blocked checks.

## 8. Completion checklist

- [ ] Desktop price is above the ticket to the right of the chart; secondary instrument information is below; narrow layout and keyboard use are verified.
- [ ] Real catalog identity, authenticated client context, quote and account availability drive the ticket; no mock balances/history are used for this journey.
- [ ] Preview/review clearly distinguishes estimates from actual fills and discloses provider/simulator limitations.
- [ ] Server-owned order intake rejects spoofed facts, validates/reserves before acceptance and persists recoverable work before execution.
- [ ] Execution uses a fresh quote and books cash, holdings, fills, journals and events atomically and once.
- [ ] Full sale, concurrent orders, response loss, restart, session expiry and status reconnect behave correctly.
- [ ] Legacy mutation routes cannot bypass the order/account rules; client/staff authorization tests cover the complete write surface.
- [ ] Authoritative cash/holdings and persisted filled/rejected history refresh after outcomes and remain correct after reload.
- [ ] Approved scope/rules and unsupported capabilities are recorded; BR-12 and production readiness are not claimed from a single simulator demo.
- [ ] Relevant regressions, PostgreSQL concurrency/rollback, migration and browser acceptance pass with recorded evidence and peer review.

The deliverable is complete when the client can understand and verify the outcome of their own buy/sell instruction, and the backend can reconstruct that same outcome without relying on browser state or manually edited trade rows.
