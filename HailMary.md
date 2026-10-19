### BR-01 — Register and securely sign in | **MUST**

**[SOURCE]** A prospective client can register and securely sign in on subsequent visits.

**Why:** This is the entry point to self-service trading and establishes a stable actor identity for every legally relevant event.

**[PROPOSAL — implementation]** Provide Angular registration/login screens; integrate OIDC identity provider or approved test fixture; create an internal `client_account` linked to the immutable identity-provider subject (`sub`); validate unique account creation; secure password processing through the provider, not the application database; expire tokens and provide sign-out. All authenticated requests must validate issuer, audience, signature and token lifetime. Audit registration, significant authentication events and access changes without collecting raw credentials.

**Acceptance evidence:** (1) New fixture-eligible user registers once; duplicate attempt cannot create another cash account. (2) User signs out and back in and sees the same account. (3) Wrong credentials and expired/tampered tokens are rejected; secrets are absent from logs. (4) A test fixture account is clearly labelled not production KYC-verified.

**Dependencies/questions:** Identity service vs sign-in fixture, email verification, account-state rules, MFA policy, jurisdiction-specific registration restrictions.

### BR-02 — Client-only access to own data/actions | **MUST**

**[SOURCE]** A client may only see and act on **their own** holdings, cash and orders.

**[PROPOSAL — implementation]** Resolve current account from trusted authentication context; enforce ownership on **every** endpoint and repository query (`WHERE account_id = authenticated_account_id`). Use row-level security as a possible second layer, not a substitute for authorization design. Separate internal privileged use cases and write explicit policy tests. Never assume Angular guards or hidden menu items provide authorization.

**Acceptance evidence:** Client A cannot enumerate/read/update Client B orders by changing IDs, query params, request body, search filters, WebSocket channel or export URL; all attempts receive non-disclosing failures and are security-audited where appropriate. Staff roles never inherit client write privileges by accident.

**Dependencies/questions:** Multi-account clients, joint accounts and delegated access are undefined; phase-one model should be one or more explicit owned accounts only if approved.

### BR-03 — Time-limited, revocable signed-in session | **SHOULD**

**[SOURCE]** Sessions should have a bounded lifetime and be revocable.

**[PROPOSAL — implementation]** Define idle timeout and absolute lifetime; support logout/session revocation; implement staff MFA and refresh-token controls; reject revoked sessions on protected APIs with an acceptable revocation delay approved by Security; display session expiry gracefully without losing an already accepted order.

**Acceptance evidence:** Expired session cannot read data/place order; revoked client session loses access; logging out during asynchronous execution does **not** cancel a persisted order; user gets a clear reauthentication prompt.

**[DECISION REQUIRED]** Actual idle/max session durations and revocation maximum delay.

## 4.2 Order placement and execution — PDF §8.2, pp.3–4

### BR-04 — Submit a buy or sell order | **MUST**

**[SOURCE]** Client can submit a buy or sell for a supported instrument.

**[PROPOSAL — implementation]** A single order ticket takes instrument, `BUY`/`SELL`, quantity and any approved order parameters; show quote/position context; submit a unique idempotency key; validate types and precision in UI for usability and **again on server for authority**. Return a unique order ID and durable state. Follow the state machine in §5.

**Acceptance evidence:** Valid buy and valid sell are recorded as distinct client-owned orders; duplicate click/retry does not produce two orders; invalid symbol/side/zero/negative quantity is rejected without moving money.

**[DECISION REQUIRED]** Supported order types (market-style only, limit, time-in-force), fractional-quantity rules, maximum order size, quantity/price tick precision, cancellation/amendment support.

### BR-05 — Enforce trading rules before acceptance | **MUST**

**[SOURCE]** Validate cash/holding sufficiency and instrument tradability **before accepting** an order.

**[PROPOSAL — implementation]** Build a centrally configured rule engine in Spring domain code, not duplicated as the decision maker in Angular. Check authenticated account status; asset allowed; market/session permitted; side/quantity/lot/tick limits; restricted instruments where applicable; available **unreserved** cash for buy; unreserved holdings for sell; asset/quote availability and any approved exposure/order limits. Lock or atomically reserve spendable resources so concurrent orders cannot double-spend the same cash or shares. Preserve each rule result and ruleset version for audit.

**Acceptance evidence:** Insufficient available cash rejects buy; insufficient available holdings rejects sell; disabled/closed instrument rejects or queues only according to approved policy; two simultaneous orders cannot reserve more than available; rejected attempts carry reason codes and do not create fills.

**[DECISION REQUIRED]** How to fund price movement between acceptance and execution, supported trading sessions/holidays, compliance/restriction rules and whether pre-acceptance rejection attempts must be retained (recommended: yes, separately from accepted-order legal history).

### BR-06 — Persist accepted commitment before asynchronous execution | **MUST**

**[SOURCE]** Accepted order must already be recorded as a firm commitment before execution; execution may later fail or be delayed.

**[PROPOSAL — implementation]** `POST /orders` transaction inserts order (`ACCEPTED`), accepted timestamp, full accepted instruction, validation decisions, reservations, audit timeline and Kafka outbox record. **Only after commit** can the worker execute. Return a status appropriate to asynchronously pending processing (e.g., HTTP `202` with `order_id` and `ACCEPTED`; exact REST choice is implementation detail). No external quote/broker call is relied on to make intent durable.

**Acceptance evidence:** Kill execution worker after acceptance: order is still queryable after restart. Failure to publish Kafka event is retried from outbox. Failure of database transaction creates **no accepted order** and **no execution request**. An accepted order exists regardless of final fill/reject.

### BR-07 — Push status changes without manual refresh | **SHOULD**

**[SOURCE]** Client can see `submitted`, `accepted`, `filled`, `rejected` transitions live.

**[PROPOSAL — implementation]** Publish status after each **committed** transition and stream to that client's authenticated Angular subscription via SSE or WebSocket. On reconnect, load current status and missing events by cursor/sequence; never trust volatile push notifications as the only state store. Display rejection explanation in plain language.

**Acceptance evidence:** Submit order with browser open; progress updates appear without refresh. Simulate connection drop and reconnect; screen converges to authoritative stored state. A client cannot subscribe to another user's stream.

### BR-08 — Execution-time current quote determines fill/rejection | **MUST**

**[SOURCE]** Re-price against a **current market quote at execution**, then fill or reject accordingly.

**[PROPOSAL — implementation]** Adapter obtains timestamped, source-identified executable/approved quote **when execution is attempted**; select correct buy (`ask`) / sell (`bid`) side; check age/staleness, spread, market session, tick/lot precision, configured max price movement and available reserved funds; record quote ID, provider, observed/provider times, currency, applied rules and result. Reject safely when a valid current quote cannot be obtained. Indicative prices are **not binding fills**.

**Acceptance evidence:** Price changes between order acceptance and execution; filled amount uses **execution quote**, not earlier indication. Stale/missing quote produces controlled non-fill state with evidence. Same quote facts can be retrieved during dispute investigation.

**[DECISION REQUIRED]** Market data and execution providers, quote validity windows by asset class, spread/slippage policy, supported currencies, exchange-tradability entitlement and whether partial fills exist.

### BR-09 — Atomic cash, holdings and trade-record update | **MUST**

**[SOURCE]** A successful fill must update holdings, client cash and permanent trade record **all together**, with no partial state even on failure.

**[PROPOSAL — implementation]** In a **single ACID PostgreSQL transaction**, conditionally transition order from valid pending state; persist fill and execution evidence; append cash and holdings journals; adjust authoritative balances/positions; consume/release reservations; append audit/outbox records. Enforce constraints, version checks and unique `external_execution_id`/`fill_id`. Keep any non-transactional broker/external side effect outside that transaction, then reconcile retry uncertainty with a stable external request reference. A saga alone does **not** satisfy this internal atomicity rule.

**Acceptance evidence:** Inject a crash after cash write but before position write → whole transaction rolls back; restart/retry yields one complete fill. Duplicate execution confirmation leaves exactly one fill/ledger posting. Holdings, cash and booked fill amounts reconcile for each currency and instrument.

### BR-10 — Accurate current holdings and cash | **MUST**

**[SOURCE — §8.3, p.4]** Client sees holdings and cash correct as of last executed trade.

**[PROPOSAL — implementation]** Read strongly consistent OLTP-derived balances (or materialized balances maintained **in same booking transaction**); show quantities held versus reserved and currencies explicitly. Include `as_of`/last-booked timestamp; calculate market valuation separately from owned quantity. A stale OLAP projection is **not** an acceptable source for the client’s authoritative available cash or shares.

**Acceptance evidence:** After fill response and refresh/restart, holdings/cash match journals exactly. Pending buy reserves **available** cash without falsely claiming holdings increased before fill. Pending sell reduces available-to-sell holdings but not yet settled/owned quantity unless booked under approved rules.

### BR-11 — Chronological own order and fill history | **MUST**

**[SOURCE — §8.3, p.4]** Client sees a chronological blotter of own orders and fills.

**[PROPOSAL — implementation]** Query OLTP for paginated orders, status, timestamps, execution result and linked fills. Default newest first, with stable `created_at, order_id` ordering and separate accepted/fill timestamps. Allow detail timeline drill-down, controlled filtering and timezone-display clarity. Preserve rejected orders and distinguish them from cancelled/expired only if those states are supported.

**Acceptance evidence:** Every client accepted order, including failures, appears once and is searchable; a filled order has a linked fill; Client B history is invisible to Client A; dates remain correctly ordered across time zones and restarts.

## 4.3 Market data and pricing — PDF §8.4, p.4

### BR-12 — Execution quotes across supported instruments | **MUST**

**[SOURCE]** Quote supported launch instruments: **UK equities, US equities, Indian equities, FX pairs, crypto assets**.

**[PROPOSAL — implementation]** Use a normalized instrument master (`asset_class`, market/MIC, symbol, base/quote currency, trade precision, exchange calendar, status). Implement asset/provider adapters under a common quote interface; validate real-time entitlements, provider error handling, quote timestamp, currency and tick precision. Avoid treating all symbols as globally unique or all markets as open 24/7. Use decimal arithmetic; do not conflate indicative and executable prices.

**Acceptance evidence:** Configured representative instrument from each requested group can pass the quote → acceptance → execution-time quote → fill/reject flow in a demonstrable approved environment. Negative tests cover unavailable markets, stale data, incompatible precision and unavailable currency balance.

**[DECISION REQUIRED]** Approved exchanges/venues, exact instrument list, market-data licenses, price/execution vendors, whether FX is a conversion or tradeable position, crypto asset regulatory eligibility and custody/execution assumptions.

### BR-13 — Indicative price before submission | **SHOULD**

**[SOURCE]** Client sees a price indication prior to placing an order.

**[PROPOSAL — implementation]** Show bid/ask, currency, last quote time, applicable spread/fees if approved, estimated consideration and an explicit **“Indicative only; execution price can change”** message. Auto-refresh while instrument screen is open; halt submission or warn when quote age exceeds approved threshold. Always repeat pricing and business checks server-side.

**Acceptance evidence:** Client can see price prior to submit, quote age and currency are understandable; price change after submit correctly affects final execution; no guarantee of an earlier quote is falsely implied.

## 4.4 Record-keeping and audit — PDF §8.5, p.4

### BR-14 — Permanent, attributed trade/pricing/cash/holding evidence | **MUST**

**[SOURCE]** Every accepted order, pricing decision and cash/holdings outcome is permanently recorded with client and time; records survive restart and failed deployment; regarded as firm's legal position.

**[PROPOSAL — implementation]** Maintain append-only domain event/audit tables, durable fill and financial journals in PostgreSQL; store immutable identifiers, accountable actor/service, event and recording timestamps, correlation IDs, validation/ruleset versions, quote provenance, old/new outcome references and transaction references. Protect logs from routine mutation; take encrypted recoverable backups, protect access to backup/evidence and verify recovery. Consider hash chaining, signed export manifests and immutable/offline backup retention as **additional safeguards**, not a substitute for storage control or legal retention decisions.

**Acceptance evidence:** Every accepted order and each fill/reject can be linked to immutable timeline entries and quote evidence. After disaster-recovery exercise, no acknowledged accepted order or posted fill disappears/duplicates. Privileged operators cannot silently edit prior legal records through application APIs.

**[DECISION REQUIRED]** Jurisdictions, record retention duration, evidentiary standard and authorized exceptional correction process are absent from source.

### BR-15 — Reconstruct full lifecycle on demand | **MUST**

**[SOURCE]** Any trade can be reconstructed from placement through pricing and settlement/outcome, without relying on human memory.

**[PROPOSAL — implementation]** Provide Trading Operations and Compliance investigation views returning ordered facts: original client instruction → validation outcomes → acceptance/reservation → execution request IDs → exact execution quote → execution decision/fill/reject → booked cash/position effects → retries/reconciliation actions. Index by order, client reference, timestamps, quote/execution IDs. Show when events occurred vs when received, with explicit time zones and actor provenance. Give authorized export a record count and digest/manifest.

**Acceptance evidence:** Choose any filled and any rejected order; authorized user reconstructs exact outcomes and amounts via UI/API without engineering-only log access. Audit evidence persists independently of a transient Kafka consumer or browser connection.

## 4.5 Reporting and insight — PDF §8.6, p.4

### BR-16 — Historical analytics isolated from live trading | **MUST**

**[SOURCE]** Analyse trading by instrument, time and client segment without performance contention with live trading.

**[PROPOSAL — implementation]** Build a **separate physical reporting database** and ETL/ELT pipeline; never run exploratory analyst joins against transaction-primary PostgreSQL. Publish committed facts via outbox/CDC; perform incremental upserts/aggregations in OLAP; control data masking/segment entitlements. Include data freshness/reconciliation and limit large queries in reporting service.

**Acceptance evidence:** Run heavy analyst reports concurrently with trading load and show no material degradation of core order processing against approved performance criteria. Aggregated fill/quantity/cash measures reconcile with OLTP for a selected period, allowing documented pipeline lag.

### BR-17 — Useful internal business insights | **SHOULD**

**[SOURCE]** Show a small set of business metrics: volumes, most-active instruments, activity trends.

**[PROPOSAL — implementation]** Deliver dashboard(s) showing number of submitted/accepted/filled/rejected orders, notional traded by asset class/currency, top instruments by fill count/quantity, active client count by period and trends. Clearly distinguish **count of orders** from **count of fills**, counts from notional amounts, base currency from quote currency, and trading time from data-load time. Use anonymized segments where possible. Only define client segments that have a legitimate approved source.

**Acceptance evidence:** Stakeholder can filter report over selected periods and instrument/segment; totals match agreed definitions; `data_as_of` is visible; no analyst can access arbitrary client PII.

## 4.6 Additional platform value — PDF §8.7, p.5

### BR-18 — Propose, justify and deliver one added capability | **SHOULD**

**[SOURCE]** Team should choose and build **one** additional capability that benefits clients or business, and justify the choice against alternatives.

**[PROPOSAL — recommended selection] “Pre-trade impact preview.”** Before final order submission, show the estimated impact on **available cash**, **projected post-fill holdings**, estimated fees/spread if configured, quote time and warnings such as insufficient headroom or market closure. This goes beyond `BR-13` (a price indication) by showing *account impact*, reducing avoidable retail mistakes in a dealer-free flow. All values are labelled **illustrative until execution**, with live server validation authoritative.

**Why over alternatives:** A watchlist/price alerts feature adds notification infrastructure but contributes less to safe execution; complex portfolio risk scoring introduces suitability/model governance; advisor delegated access conflicts with phase-one scope. The preview reuses existing quotes, balances and validation logic and can be demonstrated without banking connections.

**Acceptance evidence:** Preview displays instrument/side/quantity, indicative price, estimated consideration, estimated remaining available cash and/or available-to-sell shares, fee assumptions and timestamp; warns when a pre-check fails; actual fill can differ and is always displayed using execution-time quote. Show written alternatives considered and sponsor approval. **Sponsor may select a different capability.**

---


