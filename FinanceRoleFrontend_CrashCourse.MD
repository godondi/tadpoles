# Finance frontend crash course

**Implementation date:** 22 October 2026.
**Frontend implementation:** dedicated overview and reconciliation screens, Finance-specific adapter/tests, and two lazy route-loader replacements.
**Backend integration:** summary-only API is implemented in the backend source tree and tested, but the backend container has not been rebuilt/restarted in the reported Compose environment. Existing reconciliation GET/POST remain unchanged. Fill/movement/posting details, reconciliation history/detail and export remain deferred.
**Business context:** this UI supports internal Finance evidence review, not money movement, balance repair, external settlement, custody proof or full general-ledger accounting.

## What changed and why

The existing application had Finance role navigation, `FINANCE` landing at `/finance/reconciliation`, an overview metadata route, a placeholder overview request to `/api/finance/summary`, and a shared reconciliation page. The shared page worked against the current GET/POST paths, but it was not Finance-owned and did not have the required independent reporting states, run feedback separation, exception search or explicit unavailable details/export affordances.

The implementation provides:

- `frontend/src/app/features/finance/api/finance-api.service.ts`: role-local typed adapter for `GET /api/finance/summary`, current GET/POST `/api/finance/reconciliations`; decimal-string DTO validation; UTC instant checks; Spring `message` and FastAPI `detail` normalization; status-preserving errors. Its strict `from`/`to` request now matches the backend implementation; backend deployment still needs to be verified.
- `frontend/src/app/features/finance/overview/finance-overview.component.ts`: a dedicated overview with strict date range validation, Apply/Reset query persistence, UTC labels, USD label, separately loaded summary and reconciliation resources, response metadata, unavailable/empty/populated/delayed states, and explicit detail/export/accounting limitation copy. No totals render until a typed valid successful summary response exists.
- `frontend/src/app/features/finance/reconciliation/finance-reconciliation.component.ts`: dedicated persisted report review, explicit single-flight POST run, separate GET/run errors, no automatic POST retry, current exception search limited to loaded report rows, explicit historical exception separation, scope and timestamp labels, and cancellation/data clearing on session changes.
- `frontend/src/app/app.routes.ts`: only the two permitted Finance component loader substitutions. Shared reconciliation component remains in place for unrelated consumers. Workspace role policy, auth guards, shell, global CSS, backend and database are untouched.
- Colocated Finance API, overview and reconciliation specs plus Finance-specific app route/auth behavior assertions.

## Current live vs proposed contract

| Behavior | State now | Authority |
|---|---|---|
| Latest reconciliation | Live: GET `/api/finance/reconciliations` returns the latest persisted report or `{runId:null,message}`. | `TradingOperationsController.latest()` and `ReconciliationService.latest()`. |
| Run reconciliation | Live: POST `/api/finance/reconciliations` with `{}` runs a repeatable-read snapshot and persists report evidence. It may write a run; it does not repair books. Browser does not automatically retry an uncertain POST. | `TradingOperationsController.reconcile()` and `ReconciliationService.run()`. |
| Current checks | Live internal checks include cash/position journal totals, holds, order/job links, fills/postings, movements/outbox and event/outbox consistency. | `ReconciliationMapper.exceptions()`. |
| Summary | Implemented in backend source: authorized GET `/api/finance/summary` returns typed aggregates on successful primary PostgreSQL reads and 503 on source failure. No total or fee is fabricated in UI. Backend container deployment is not confirmed. | `StaffWorkspaceController.financeSummary()` and `FinanceSummaryServiceImpl.summary()`. |
| Summary filters/model | Implemented: strict `from`, `to` UTC dates, up to 366 inclusive calendar days, no future end date; versioned USD DTO, source/snapshot/generation metadata and decimal-string totals. | Finance adapter and backend summary DTO/service/mapper. |
| Fill/movement/posting details | Not connected; no Finance endpoints. Screen explains this and does not link to privileged Operations pages. | Backend handoff only. |
| Accounting export | Disabled; no endpoint, approved scope/purpose, manifest, redaction, download authorization or retention policy. No browser CSV is generated. | Backend handoff only. |

The public local fixture identity `demo.finance` may be used in an explicitly disposable configured local setup. Do not reset credentials, reseed balances or volumes for this UI task. Do not paste local credentials into source, fixture files, logs or browser storage. A login/profile failure is an environment/auth issue, not a reason to bypass the verified identity flow.

## UI and state model

### Finance overview

- Reporting date controls validate exact `YYYY-MM-DD` UTC calendar dates and start ≤ end. Apply updates the route query parameters; Reset clears them and uses the default range.
- When the running backend still returns 503 (for example, before rebuilding/restarting it), the page explicitly says the source is unavailable, displays no numeric tiles for that state, and explains that unavailable is not zero activity.
- A valid summary response is validated by the adapter before it reaches the view. A successful zero report is shown as no activity with its source-backed zero values. Populated values are decimal strings from the API; client-side aggregation is not used.
- Reconciliation has an independent `PageResource`. A summary error does not suppress latest report or a no-run message.
- “Data as of” and “Generated at” are separate server facts and explicitly UTC. Request/fetch time is not substituted for a source watermark.
- Export is disabled and accompanied by the missing-contract reason. Fill, movement and posting detail remain explanatory unavailable sections only.

### Reconciliation

- Initial GET yields either a stored run or explicit no-run response. `runId: null` is not a matched report.
- A persisted run shows status, run ID, checked-at in UTC, current run exception count, historical migration exception count, and exact scope. Matched means only that the listed current internal checks found no differences; it does not clear historical exceptions or establish bank/exchange/custody settlement.
- Search is local to that response and labeled “Filter this run.” No server-wide search is claimed.
- The Run button is disabled during POST; repeat activation cannot send another request. POST progress/success/error has its own state and does not erase the loaded report. A timeout/server failure is potentially uncertain; the browser does not resend. The operator should first read the latest stored run.
- HTTP 401/403 and identity changes clear protected report state through the existing `PageResource` and session cancellation. Finance does not change shared auth or guard behavior; the backend remains authority, including mixed CLIENT+FINANCE denial.

### Accessibility and responsive behavior

The feature uses semantic headings and labeled date/search inputs; tables use captions, scoped headers, stable `kind:reference` row keys and the existing `.table-wrapper`; focus uses the existing global visible-focus rule. Status is written as text and not communicated by color alone. Local layout styles use existing theme variables and collapse scope controls on narrow screens. Keyboard tab order follows document order. No global CSS or shell redesign was added.

## Requirement-to-component-to-test matrix

| Requirement | Component/API | Acceptance test |
|---|---|---|
| BR-09 atomic booking and no Finance mutation | Read-only `FinanceOverviewComponent`; `FinanceReconciliationComponent` only calls existing evidence-recording run; `FinanceApi` exposes summary and reconciliation only | `finance-api.service.spec.ts` checks only the existing/proposed report GET and reconciliation POST body; reconciliation spec verifies POST once and no automatic retry; no order/trade/cash-movement mutation endpoint is called. |
| BR-10 correct cash/positions, do not invent ledger values | Typed decimal-string summary gate; explicit “not full general ledger” wording | Overview spec checks valid populated/empty values are API-backed, 503 shows no totals, and ledger limitation is present; adapter rejects malformed success rather than defaulting totals. |
| BR-14 durable attributed evidence | Reconciliation run/report view shows run ID, server checked-at, evidence version, scope and separate run/save state | Reconciliation spec covers persisted run/no-run, run report and uncertain POST; API spec preserves exact GET/POST paths/body. |
| BR-15 reconstruction and historical obligations | Current exceptions stay in current run; historical migration exception count is displayed separately | Reconciliation spec asserts separate current/historical evidence and only loaded-row filtering; overview spec renders latest report independently. |
| BR-16/17 defined historical reporting | Versioned summary contract is implemented in backend source; named-owner production approval, query-plan review and deployment remain pending. Historical isolated reporting is not implemented. | Overview specs cover populated, successful-empty, delayed, 503 and filter query persistence; backend controller/service/PostgreSQL tests cover the source-backed summary and unavailable source. |
| Finance workspace authorization and identity safety | Existing workspace routes/guards, API status handling, `PageResource` plus session cancellation | App spec denies CLIENT/ANALYST/ADVISOR/COMPLIANCE and handles mixed-role 403; overview/reconciliation specs assert requests cancel and protected data clears on session change. |
| No unauthorized export or privileged detail links | Disabled export affordance and explanatory detail section; no export/data-detail API call | Overview 503 test asserts export unavailable; adapter surface and frontend tests issue no download, Operations link, posting mutation or trade request. |

## Run and reproduce states

Run from the `frontend` directory. Dependencies were installed with the existing lockfile using `npm ci`; it reported 8 audit advisories (1 low, 3 moderate, 4 high). The lockfile was not intentionally edited.

```sh
npm test -- --watch=false --include='src/app/features/finance/**/*.spec.ts'
npm test -- --watch=false --include='src/app/app.spec.ts'
npm test -- --watch=false
npm run test:qof-styles
npm run build
```

The installed Angular CLI accepted the focused `--include` selector and all three Finance spec files ran.

To reproduce views without contacting a real account, use the HTTP test fixtures in:

- `frontend/src/app/features/finance/api/finance-api.service.spec.ts` for summary shape, date query values, exact reconciliation GET/POST contract, error normalization and malformed response.
- `frontend/src/app/features/finance/overview/finance-overview.component.spec.ts` for summary 503 with reconciliation still usable, populated response, successful empty response, delayed status, query persistence, and session-change cancellation.
- `frontend/src/app/features/finance/reconciliation/finance-reconciliation.component.spec.ts` for no run, matched/historical exceptions, mismatches, rapid repeated activation, loaded-row filtering, POST 503 with no retry, and session-change cancellation.
- `frontend/src/app/app.spec.ts` for dedicated route resolution, Finance-only route rendering, CLIENT/ANALYST/ADVISOR/COMPLIANCE denial, and mixed-role backend 403 behavior.

No fixtures enable themselves in normal operation; these are deterministic test-only responses and contain no personal/bank details.

## Verification record

Observed on 22 October 2026:

- Focused Finance suite: **25 tests passed across 3 files** using `npm test -- --watch=false --include='src/app/features/finance/**/*.spec.ts'`.
- App route/auth suite: **13 tests passed** using `npm test -- --watch=false --include='src/app/app.spec.ts'`.
- Full frontend suite (final run): **211 passed, 1 failed across 34 test files**. The failing existing `src/app/features/analytics/analytics-page.component.spec.ts` assertion says the whole page text must not contain character `0`; current “Overview” label makes that assertion fail. Analytics is outside Finance ownership and was not modified. This is not a Finance test failure, but the complete suite is not green.
- `npm run test:qof-styles`: passed (spinner, square-corner and no-gradient check).
- `npm run build`: passed. Existing Analytics component style budget warning: 4.37 kB against a 4.00 kB budget (372 bytes over); no Finance build errors.
- Focused Finance/app suite had an initial expected red failure because Finance summary overview was still generic and did not issue reconciliation GET. It passed after the dedicated route/component implementation. One API spec test matcher was corrected to account for query params; final API suite passes.
- No live browser login/session, backend API calls, account data, database migration, provider, Docker or deployment was exercised. Build output does not imply runtime backend availability.

## Backend integration handoff

Use `FinanceRoleBackendInstruction.MD` as the contract and release handoff. Before treating the summary as integrated in a running workflow:

1. Confirm the Finance owner’s scope, purpose/access audit, metric definitions, date semantics, max range/query budget, empty/unavailable rules, reporting source and `dataAsOf` behavior.
2. Rebuild/restart the backend service so the implemented `GET /api/finance/summary` contract is actually served; verify in the approved environment. The backend tests validated source, not deployment.
3. Decide if fill, movement, posting or reconciliation history/detail endpoints are approved and implement bounded stable pages with scope-enforced authorization before counts.
4. Decide export purpose/retention/redaction, immutable manifest/digest, authorization-at-download and audit before adding any UI download.
5. Preserve current `GET/POST /api/finance/reconciliations`, repeatable-read matcher, evidence protection, mixed-role denial, financial writers/idempotency/reservation guarantees, fresh-vs-retained database parity and runtime grants. The 23 October implementation added no schema/migration/grant changes.
6. Deploy schema/API only after migration rehearsal and least-privilege role tests; then replace explicit unavailable/integration messaging with the confirmed contract and additional tests. Do not call the frontend fully integrated until a valid approved data source is connected.

**Backend verification on 23 October 2026:** the summary-only backend source implementation passed the focused controller/service/PostgreSQL tests (10 controller + 5 service + 1 PostgreSQL scenario) and the full backend suite (**408 tests passed; 0 failures/errors/skips**). `backend/target/team-skeleton.jar` was packaged and contains the summary classes. Testcontainers used disposable PostgreSQL; no retained database was changed. The running backend container was not rebuilt/restarted, and no browser-to-running-backend integration check was performed. Named-owner policy approval and representative query-plan review remain production gates. This handoff describes no external bank transfer, exchange venue, custody system, complete ledger or trial balance. A backend requirement to modify shared security, financial writers, global exception handling or the core matcher triggers the separate stop-and-proposal procedure before editing.
