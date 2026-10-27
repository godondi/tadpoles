> **Current implementation:** See the [provider-backed USD trading guide](ClientRefactorCrashCourse.MD) for startup, banking, execution, roles and recovery. The material below is historical implementation evidence and may describe retired behavior.

# Client simulation implementation

## Step 1 — 14 October 2026 — Baseline inspection (BR-01–18)

Read the complete `ClientAndBuySell.MD` before editing. The only pre-existing working-tree changes were the untracked instruction file and `impDocs/HailMary.md`; both are preserved. No applicable AGENTS.md was found. Confirmed the disabled `WorkspaceClientController.submit`, legacy-only `WorkspaceReadMapper`, scalar cash and dated holdings, and opaque refresh token implementation. The frontend instrument page is a disabled notice; account reads use a coupled forkJoin.

Started `bash scripts/test-role-workspaces.sh` in disposable Docker containers; baseline result pending. Docker requires the tool's sandbox escalation; no development database is used. Schema tests currently split SQL on semicolons, so that harness must change before procedural migrations are tested.

Implementation follows the brief's simulation policy: explicit opt-in deterministic test prices, zero fees, per-currency cash, reservation at admission estimate with execution-time affordability recheck, one fill per order, no real execution or currency conversion. The selected impact preview is accepted as part of executing the supplied brief. Independent reporting, staff investigations, and live execution remain separate dependencies.

## Usability assessment and design direction

The current UI is useful for inspecting legacy records but is not usable for a casual trader placing an order: every action is disabled, the cash summary lacks a currency, and the same technical warning dominates both instrument and ticket screens. A casual user needs a clear sequence: choose an instrument → enter units → review estimated impact → confirm → see a durable receipt and eventual outcome.

The implementation will use a focused ticket with Buy/Sell choices, a labelled decimal input, a read-only server preview, and one explicit confirmation button. Spendable cash and available units will be prominent; reservations and posted balances will remain visible with plain-language explanations. Simulation, zero fees, quote time, and variable execution price will stay next to confirmation. The receipt will distinguish accepted from filled and preserve an uncertain request for recovery. These choices reduce accidental duplicate orders and explain affordability before the user commits. Separate currencies will never be combined. Charts remain secondary context, not execution evidence.

Status flow: draft → review → submitting → durable receipt → accepted/executing → filled or rejected → authoritative portfolio refresh. Unknown network outcomes enter recovery with the original key.

## Current completion state

Client simulation implementation and the browser trade walkthrough are complete. Final verification results and explicit visual/deployment/platform limits are recorded below.

## Step 2 — 14 October 2026 — Schema, policy and transaction implementation (BR-04–16)

Baseline aggregate passed: backend 299, database 13, frontend 95; style check and production build passed. The initial schema/rules implementation was written before new tests, so this portion does **not** satisfy a strict test-first red phase. This deviation is recorded rather than presented as TDD evidence. PostgreSQL behavioral tests now exercise the actual service and mapper boundaries, with failure injection after each booking write; results pending.

Added migration/fresh schema 004, exact currency accounts, zero-preserving positions, reservations, quote/decision records, single fills, append-only journals/events/attempts, leased jobs, outbox, and auth-session storage. Database triggers protect evidence, accepted intent, terminal outcomes, legacy financial writes for migrated accounts, and unsafe currency edits. Legacy prices, dates, and snapshots are retained. A dedicated opt-in simulation seed provisions five named synthetic instruments covering US/UK/India equities, FX and crypto, plus journaled openings for the two local client identities. Reruns use unique setup references and never refill spent cash.

`TradingRules` validates exact quantities, quote mapping/freshness/normalization, configured limits and sessions. UK fixture quotes explicitly normalize GBX to GBP. `TradingTransactions` serializes by persisted client, commits acceptance before the worker can see it, snapshots policy, and writes events/outbox in the same transaction. Execution claims use expiring tokens; stale workers cannot book. Fresh quote acquisition happens outside account locks. A failure leaves the durable job recoverable. No external broker or analytics database has been added.

The resumable status endpoint uses authenticated cursor polling, an equivalent bearer-authenticated transport rather than SSE. Every poll reauthorizes; per-client cursors are allocated under the same account lock as events. Polling avoids bearer tokens in URLs and supports explicit reset/reload.

Migration rollback is forward-only: disable admission, keep evidence and durable workers, and preserve schema 004. Do not drop populated tables or reactivate legacy financial writes.

## Step 3 — 14 October 2026 — Regression findings and Client UI (BR-01–03, BR-07, BR-10–13, BR-18)

The first service integration run passed all eight PostgreSQL tests. The complete backend run exposed a legacy H2 fixture missing `submitted_at`. The first correction targeted the wrong fixture (`revised_schema.sql`, which has no trades table); that produced fixture setup errors. Removed that correction and added the column to the actual `mapper/schema.sql` fixture. The PostgreSQL tests continue to exercise the real migration, not the H2 approximation.

Test-first regression evidence: `TradingRulesTest.missingQuoteSymbolFailsClosedInsteadOfThrowing` initially failed with a null-symbol exception (3 tests, 1 error). Changed mapping validation to compare from the configured non-null symbol. Focused rerun: 3 passed, no failures/errors. The preliminary compilation failure from the changed CORS factory signature was a fixture/API adjustment, not the behavioral red phase.

Added ticket/preview/confirmation, user-scoped same-tab pending-command recovery, explicit rejection receipts, new lifecycle details/timeline, independent portfolio/recent-order loading, currency balance cards, zero-preserving positions, and authenticated cursor polling. The initial frontend run passed 102 tests (95 existing + 7 ticket tests), including duplicate clicks, lost responses, same-key retry, reload recovery, session clearing and expired previews. Status reads are authoritative; event notifications only trigger HTTP reloads. Background detail refresh preserves the last known pending state through a temporary outage so reconnection can converge.

Login/signup now issue signed JWTs with persisted session IDs, issuer and audience. Logout submits the current token through a dedicated HTTP client before local clearing; offline logout explicitly says revocation was not confirmed. Cross-tab session changes clear private state. Automatic refresh is deliberately omitted: the old opaque access-string response is replaced by an explicit unavailable/relogin contract. Legacy administrative refresh-token management is retained but cannot mint working access sessions.

Approval-review limitation: an attempted combined command including automatic runtime-role provisioning (default password plus broad grants) was rejected. No role was created and no such provisioning code was retained. Continued with the existing development database credentials and narrower application/schema work. A separately approved, least-privilege runtime database identity is still required for a hardened deployment; the local development owner connection is not described as hardened. Database evidence triggers remain active and tested.

## Step 4 — 14 October 2026 — Complete suite checkpoint and HTTP contracts

Aggregate checkpoint passed: **313 backend tests, 16 database tests, 105 frontend tests**, no skipped tests; style checks and production build passed. The backend count includes actual signed-JWT HTTP ownership checks against PostgreSQL, staff denials for preview/submission/status transport, session revocation, and completion of an accepted order after logout. Additional status/partial-resource tests and stronger deferred database evidence checks were added after this checkpoint and will be included in final verification.

PostgreSQL support is aligned to **15**, matching the existing Compose volume format; the test image was changed from 16 to 15 rather than upgrading a development volume in place. The complete schema is executed through PostgreSQL JDBC, preserving dollar-quoted functions. Compose now has a version-tracked, advisory-locked migration service before demo seed/backend startup, so existing volumes receive migration 004.

| Endpoint | Before | After |
| --- | --- | --- |
| POST /api/orders | Always 503 | Keyed simulation admission; 202 accepted, 422 durable admission rejection, same-key replay, 409 conflict; disabled mode retained |
| POST /api/orders/preview | Missing | Read-only policy/quote/resource impact, exact decimal strings |
| GET /api/orders/by-request/{key} | Missing | Account-scoped durable receipt lookup |
| GET /api/orders and /{id} | Legacy records | Preserved legacy fields plus record kind, exact quantity, lifecycle, fills, decisions, reservations, quote evidence, events |
| GET /api/orders/{id}/events | Missing | Bounded account-scoped event page |
| GET /api/workspace/client/order-events/stream | Missing | Authenticated resumable cursor batches, explicit reset, polling transport |
| GET /api/workspace/client/portfolio | Scalar/dated snapshots only | Legacy fields retained; separate authoritative balances/currentPositions and financialModel |
| GET /api/workspace/client/trading-availability | Constant disabled | Configuration, schema, account, onboarding, currency and instrument policy eligibility |
| Login/signup | Expiring JWT only | Persisted revocable session, issuer/audience checks |
| POST /api/auth/logout | Refresh-token-only revoke | Revoke authenticated access session; browser clears even when offline |
| POST /api/auth/refresh | Opaque unusable access string | Explicit 503/relogin; no automatic refresh |

Exact new values use strings; `exactQuantity` supplements legacy numeric quantity for compatibility. History uses new UTC submission instants and a date-only UTC sort key for legacy rows, without manufacturing legacy acceptance or timezone evidence. A stored admission rejection's command response is 422; its recovery/detail GET is 200. `status` remains the order lifecycle string in receipts; `httpStatus` supplies the numerical rejection HTTP code alongside the legacy-style error fields.

Local role credentials and explicit seed instructions are in `RolesCredentials.MD`; credentials and tokens are deliberately excluded from this implementation log and screenshots.

## Step 5 — 15 October 2026 — Browser trades, final fixes and usability review

The real browser session used the isolated local simulation stack. Bought 10 SIM-US shares at an execution ask of USD 50 (order 11), sold 4 at bid USD 49 (order 12), then sold the remaining 6 at USD 49 (order 13). Every receipt advanced automatically to FILLED. The account moved from USD 10,000 → 9,500 → 9,696 → 9,990. The zero-quantity position remains present, and its Sell action disappears. GBP 10,000 and INR 100,000 remain separate and unchanged. These are synthetic fixture transactions, not brokerage executions.

Reviewed the portfolio and sell preview at a 390 × 844 viewport as well as desktop. The ticket's labelled controls, preview, currency, projected cash/units, quote time and explicit confirmation were visible and operable. A transient instrument-load failure recovered using its visible retry control; the provider chart was intentionally unavailable and was not used for execution. Saved screenshots: `screenshots/simulated-buy-receipt.jpg`, `screenshots/mobile-portfolio.jpg`, `screenshots/mobile-sell-preview.jpg`, and `screenshots/completed-portfolio.jpg`. The first mobile captures predate the final header improvement.

**Casual-trader assessment:** the original disabled screen was not usable for placing orders. The implemented flow is usable for a basic simulation, with important limits: this is an engineering walkthrough, not a study with representative traders. The long mobile header, raw timestamps and dense evidence could still distract a casual user. Changes made after review: readable UTC order dates; automatic focus on the review heading; accurate instrument eligibility labels; separation of provider charts from synthetic order prices; and a non-sticky mobile header so the stacked navigation does not cover the focused review or receipt.

Further design ideas and reasons:

| Proposed refinement | Why it helps | Boundary |
| --- | --- | --- |
| Compact mobile brand/account row with expandable search | Gives the order and account balance more screen space | Preserve visible navigation and keyboard access |
| Group digits and format currency amounts from exact decimal strings | Makes 100000 easier to scan as 100,000 without losing precision | Never use browser floating-point arithmetic for financial calculations |
| Show the main estimated cost/proceeds and post-fill balance first, with secondary details expandable | Reduces reading effort while preserving the explanation | Quote age, simulation label and price-change warning stay visible at confirmation |
| Render mobile positions as labelled cards | Avoids horizontal table scrolling for common Buy/Sell actions | Keep the desktop comparison table and accessible labels |
| Offer an explicit “sell all available units” draft action | Helps casual traders exit a position without retyping a long decimal | It only fills the draft; the server still previews and reserves on confirmation |
| A separate optional maximum-spend/limit-order product | Addresses discomfort with a changing market-order estimate | Requires a new accepted business policy; do not imply the current estimate caps spend |

The preview is the extra capability selected in the brief and accepted through the instruction to implement it. It is read-only and indicative. It does not reserve funds or guarantee execution price.

Final review found that `/auth/register/client` creates the client before onboarding, unlike `/auth/signup`. Added a shared new-account initializer to both creation paths; it creates configured currency accounts at zero only for newly created clients. Repeated onboarding never funds/reset accounts. Added a real signed-JWT registration/onboarding/expiry test. Added concurrent first-position fills and simultaneous sells, rejection-stage filtering, and exact price-precision cases. A new preview-contract assertion initially referenced a nonexistent nested quote; corrected it to the actual top-level decimal-string price. The review-focus change initially failed TypeScript compilation; fixed the injected `ElementRef` type, then reran the entire frontend suite and build.

After the environment restarted, the existing review database was restarted and queried directly: USD 9,990, zero SIM-US units, and zero reserved amounts were intact. Reconciliation returned **zero** mismatches for cash versus cash journals, units versus position journals, cash/unit reservations versus active reservations, and events missing source outbox records.

## Schema rationale and operating boundaries

| Change | Reason |
| --- | --- |
| Extend `client_trades` with record kind, exact quantity, immutable keyed intent, snapshots, UTC instants and version | Keep legacy IDs/history while distinguishing new commitments; no fabricated old acceptance or new execution price |
| Currency cash accounts and instrument positions | Prevent currency mixing, overcommitment and resurrection of old holdings after full liquidation |
| Explicit reservations | Separate owned/posted values from amounts already committed to pending orders |
| Quote observations, policy decisions, single fills, cash/unit journals and lifecycle events | Reconstruct pricing and outcomes from committed facts; enforce atomic reconciliation and append-only evidence |
| Leased jobs, fencing tokens and attempt records | Survive worker/process loss and reject superseded claims without duplicate fills |
| Versioned source outbox in the booking transaction | Provide a durable downstream ingestion boundary; not a reporting database |
| Persisted authentication sessions | Revoke access tokens and validate ownership, expiry, issuer and audience on every protected request |
| Version-tracked advisory-locked migration runner | Upgrade existing PostgreSQL 15 volumes once without changing legacy records or volume format |

Local configuration and setup are in `RolesCredentials.MD`. Admission and worker switches default to false. The deterministic simulator uses a new quote identifier and timestamp on each request; it is explicitly synthetic, zero-fee and `SIMULATION_24_7`. GBX is normalized to GBP with raw units retained. FX/crypto quantities represent base-unit positions in a pair; cash remains in the quote currency. There is no FX settlement/conversion, fee engine, external venue submission, cancellation, partial fill, top-up endpoint or market valuation/performance claim.

A safer database deployment option now uses `database/runtime-grants.sql` and `docker-compose.hardened.yml`: an administrator supplies an existing non-owner, unprivileged identity and its secret. The script refuses privileged/owner/member roles, creates no login/password, preserves the legacy application's explicit CRUD tables, grants the trading evidence tables SELECT/INSERT only, and denies evidence rewriting/truncation and table-trigger control. The hardened override also removes the inherited development env-file from backend/frontend containers so database-owner credentials are not passed to those apps; only explicit app settings remain. Compose configuration was validated with version 5.6. The migration identity remains separate. The rejected default-password automatic provisioning was not reinstated. The ordinary development Compose/review stack still uses its development owner connection; production provisioning and secret management are deployment responsibilities.

## Step 6 — 15 October 2026 — Restore, permissions and acceptance evidence

`python3 scripts/test-client-recovery.py` passed against the packaged application in a separate PostgreSQL 15 cluster. It created one filled order, stopped/restarted the application with the worker disabled, accepted another order, and took a logical `pg_dump`. Restored into a second database: one fill plus one active reservation were present. Restarted against the restore with **admission disabled and worker enabled**: the accepted order filled, a same-key submission recovered that same result, and a new key received 503. After another process restart there were still exactly two fills, no active reservations, no incomplete jobs, and zero cash/position/outbox mismatches. The migration runner also completed twice without replaying schema 004.

The rehearsal then created a temporary **NOLOGIN** role only in that disposable cluster, applied the runtime grant script, and proved UPDATE/TRUNCATE/trigger-disabling on fill evidence were denied. A JDBC session running under that non-owner role successfully authenticated, accepted and booked a third order. This tests application permissions without creating persistent credentials or broadening access to the development database. The test cluster was removed on exit.

Restore limits: this proves recovery from a completed logical backup of acknowledged data. It does **not** establish production RPO/RTO, WAL/PITR recovery, replication/failover, or retention guarantees for commits made after the backup. The rollback rehearsal disables admissions while the compatible worker drains acknowledged jobs; it does not run an older binary that predates schema 004. Do not drop schema/evidence or re-enable legacy posting for migrated accounts when rolling back.

| Acceptance references | Implemented behavior and evidence |
| --- | --- |
| BR-01 | Existing registration/onboarding/profile/routing regression tests retained; real HTTP new-client registration, repeated onboarding and zero accounts verified |
| BR-02/03 | Persisted client resolution; two-client receipt/detail/events isolation; all four staff roles denied client preview/submission/status; signed-JWT logout and expiry; issuer/audience/session-owner checks; frontend identity-change cleanup |
| BR-04/05 | Exact buy/sell rules; simultaneous buys, simultaneous sells, first-position booking races, same-key races, oversell and price-rise reservation protection in PostgreSQL tests |
| BR-06 | Acceptance, reservation, event, job and outbox commit together; durable receipt and restore of accepted work; malformed caller identity rejected |
| BR-07 | Authenticated cursor polling, duplicate/reset/disconnect tests, automatic browser receipt update; bounded 10-minute connection disclosed |
| BR-08/12/13 | New execution quote with buy ask/sell bid; age/future/mapping/normalization/precision rejection tests; five synthetic market/class fixtures; provider chart availability independent |
| BR-09/10 | Failure injection after every booking write and release, transactional rollback/retry, exact journal reconciliation, partial/full sale and persistent zero positions |
| BR-11 | Account history/detail/events, UTC new submissions, rejected-outcome stage filter, exact fill amounts, preserved legacy dates/statuses/disclosures |
| BR-14/15 | Immutable intent, snapshots, decisions, quote observations, journals, timeline and attempt evidence; lease fencing, timeout/deadline, restart/restore tests; restricted runtime evidence grants |
| BR-16 | Transactional source outbox with versioned schema and stable event IDs delivered; independent ingestion/reporting storage remains outstanding |
| BR-17 | Reporting remains unavailable and explicitly out of Client scope; no fabricated volumes/trends or freshness claims |
| BR-18 | Read-only estimated cost/proceeds and account impact; expired/input-changed previews cannot confirm; no reservation or order from preview |

Verified role boundaries:

| Role | Client orders/preview/status | Own workspace |
| --- | --- | --- |
| CLIENT | Own account only; other client's references return 404 | Portfolio, markets, ticket, orders and profile |
| TRADING_OPS | 403 | Existing operations workspace; operational actions remain unavailable |
| COMPLIANCE | 403 | Existing investigation workspace; full investigation workflow remains outstanding |
| ANALYST | 403 | Existing analytics workspace; reporting dependency remains unavailable |
| FINANCE | 403 | Existing finance workspace; reporting dependency remains unavailable |

Existing authorization regression coverage for ADMIN and mixed ADMIN+CLIENT legacy mutation restrictions remains in the suite. Staff roles add no client impersonation capability. No credential or access-token values are included in this log.

Final visual-check limitation: after rebuilding the review apps with saved data, the browser tool rejected selection of the localhost order page under its URL policy. No alternate browser surface or workaround was used. The earlier desktop/mobile trade walkthrough and screenshots are actual evidence; the final mobile header/focus/date refinements have compilation/style/test coverage but no fresh post-build visual confirmation. Screenshots intentionally remain labelled evidence from the earlier walkthrough, not a claim of the exact final rendering.

Remaining platform scope: independent analytics storage/compute and ingestion with deduplication/replay/freshness tests; authorized staff investigation workflows; real-provider coverage validation and any future broker execution; deployment-managed credentials and production backup/retention procedures. The Client simulation is not whole-platform completion or a live-trading release.

## Final verification — 15 October 2026

`bash scripts/test-role-workspaces.sh` completed successfully on the final source: **317 backend tests, 16 database tests, 108 frontend tests across 16 files**, zero backend/database failures, errors or skips; shared QoF style checks passed; Angular production build passed. The backend count includes 14 real-PostgreSQL trading tests, 3 exact-rule tests and the signed session issuer/audience/owner regression, alongside the preserved suites. `git diff --check` passed and migration 004 matches the fresh schema byte-for-byte.

`python3 scripts/test-client-recovery.py` passed the separate restore/restart/replay/reconciliation/permissions rehearsal described above. Optional hardened Compose configuration passed a non-starting configuration check and confirmed owner credentials are absent from backend/frontend app environments. No production roles, passwords, deployment, external financial execution or reporting store were created.

`bash scripts/start-client-review.sh --reuse` rebuilt and started the local review apps while preserving their database. The script snapshots application artifacts so later test builds cannot alter the running review app's files. Access instructions and local fixtures are in `RolesCredentials.MD`. Stop only this disposable review stack with `bash scripts/stop-client-review.sh` when finished.
