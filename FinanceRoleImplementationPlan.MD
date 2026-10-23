# Finance Frontend Implementation Plan

**Plan status:** Approved to proceed within the frontend-only scope stated in `FinanceRoleImplementationInstruction.MD`.
**Baseline checked:** 22 October 2026, branch `feature/finance_page`.
**Implementation status at plan creation:** No application code or tests changed/run. The plan and instruction are untracked user-provided workspace context; preserve them. `.vscode/` is also pre-existing untracked content and must remain untouched.

## 1. Goal and completion boundary

Deliver two dedicated Finance pages using the existing Angular workspace shell:

- `/finance/overview`: persistent UTC date scope, honest source/status context, typed summary rendering only when the approved summary response is valid, and independently loaded reconciliation status.
- `/finance/reconciliation`: latest persisted report, explicit single-flight run action, locally searchable current exceptions, separate historical exception count, precise scope and non-repair/external-settlement limitations.

Keep this assignment frontend-only. Do not change Java, SQL, migrations, grants, authentication, shared state classes, shared API clients, global CSS, shell/navigation policy, Operations, or root/container files. The two Finance loaders in `frontend/src/app/app.routes.ts` are the only application integration edits allowed; Finance-specific route acceptance cases may be added to the existing routing test. Preserve `shared/workspace/reconciliation-page.component.ts` and its test for any other consumers.

A successful result means **Finance frontend complete against the current contracts**, not backend reporting complete. Summary remains unavailable because the real endpoint deliberately returns 503. Fill/movement/posting detail APIs and export are proposed only. Do not invent or locally calculate ledger totals, turn failures into zero, fabricate fixtures in production, or make an unaudited download.

## 2. Verified checkout and source-of-truth map

The earlier exploratory result claiming Finance files were absent was contradicted by a fresh check after the branch context changed. It is discarded. The following actual checkout sources were read and are authoritative for this plan:

### Angular route, identity, and state

- `frontend/src/app/core/auth/workspace-policy.ts`: registers `FINANCE`, the existing `/finance/reconciliation` home, Overview and Reconciliation links, and role resolution for both Finance paths.
- `frontend/src/app/core/auth/auth.guard.ts`: `workspaceGuard` gates paths based on resolved identity. It does not explicitly block a mixed `CLIENT`+`FINANCE` identity from Finance paths; do not change this shared guard under this assignment. Backend requests remain authoritative and deny mixed roles.
- `frontend/src/app/layout/authenticated-shell.component.ts` and `frontend/src/app/shared/top-nav/`: role-filtered navigation and shared shell. Reuse; no shell or navigation redesign.
- `frontend/src/app/app.routes.ts`: `/finance/reconciliation` currently lazy-loads the shared reconciliation placeholder. `/finance/overview` is in a staff-route data array and currently lazy-loads `StaffPageComponent`. Replace only those two loaders.
- `frontend/src/app/core/api/workspace-api.service.ts`: shared `WorkspaceApi`, including a generic staff-availability method. Do not widen or modify it; implement a Finance-local API adapter.
- `frontend/src/app/shared/workspace/page-resource.ts`: one cancellable request and protected data clear on session changes; error text reads `message`, not FastAPI `detail`.
- `frontend/src/app/shared/workspace/request-state.component.ts`: loading/error/retry UI. It labels 503 unavailable and provides retry; normalize errors in Finance adapter, not in this shared component.
- `frontend/src/app/shared/workspace/reconciliation-page.component.ts` and `.spec.ts`: existing exact reconciliation paths and basic report view. Keep this shared consumer intact; replace its Finance route with a Finance-owned page.
- `frontend/src/app/core/auth/workspace-policy.spec.ts`, `workspace-router.spec.ts`, and `frontend/src/app/app.spec.ts`: existing role/home/deep-link and shell route tests. Add only Finance-specific integration assertions where necessary; avoid unrelated edits.
- `frontend/src/styles.css`: existing workspace classes, semantic table overflow, focus treatment, colors and square-corner conventions. Reuse class names/variables and keep new CSS local to the Finance feature.
- `frontend/package.json`: Angular 22 standalone app, Vitest via `ng test`, `npm run test:qof-styles`, and `npm run build`. `frontend/package-lock.json` exists; `frontend/node_modules` is absent. Do not edit the lockfile. Use `npm ci` only if needed for actual validation.

### Existing backend contracts (read-only)

- `backend/src/main/java/com/neueda/leap/controller/StaffWorkspaceController.java`: authenticated FINANCE `GET /api/finance/summary` deliberately returns HTTP 503 with a message explaining period reporting is not available and operational reconciliation is available. It is not a valid empty/zero summary.
- `backend/src/main/java/com/neueda/leap/controller/TradingOperationsController.java`: exact existing `GET /api/finance/reconciliations` for latest and `POST /api/finance/reconciliations` for a new run. Both require `FINANCE` and explicitly exclude `CLIENT`.
- `backend/src/main/java/com/neueda/leap/service/impl/ReconciliationService.java`: POST runs under `REPEATABLE_READ`, reads current exceptions, persists a `reconciliation_runs` report, records findings, and returns it; GET returns that latest report or `{runId:null,message:...}`. It does not repair books.
- `backend/src/main/java/com/neueda/leap/mapper/ReconciliationMapper.java`: current matcher checks cash and position journal totals, active holds, order-processing/fill relationships, exact fill postings, movement postings/outbox and order event/outbox consistency. Current exceptions are stable ordered by `kind,reference`. Historical migration exceptions are counted separately.
- `backend/src/test/java/com/neueda/leap/controller/StaffWorkspaceControllerTest.java`: summary 503 and unauthorized-role behavior.
- `backend/src/test/java/com/neueda/leap/controller/WorkspaceAuthorizationTest.java`: staff roles cannot use legacy client financial reads/writes; mixed CLIENT+FINANCE retains client ownership boundaries.
- `backend/src/test/java/com/neueda/leap/trading/TradingPostgresTest.java`: Finance HTTP/PostgreSQL coverage includes persisted real mismatch references, reports, and Finance-vs-Operations reconciliation authority. No backend test edits are authorized here.

### Database and operational context

- `database/README.md` and `database/fresh/001_schema.sql` were read. The current model includes USD cash/position projections, append-only cash/position journals, settled/rejected `cash_movements`, immutable `order_fills`, immutable `reconciliation_runs`, and `financial_migration_exceptions`.
- The retained migration sequence is documented through `database/migrations/012_managed_support.sql`; the Database guide explains 003–012 roles and behavior, fresh/upgrade parity, runtime grants, immutable evidence and migration policy. Never change this sequence in the frontend task.
- `docs/operations-runbook.md`, `ImplementationDocumentation/Frontend/ClientPageImplementation/ClientRefactorCrashCourse.MD`, and `ImplementationDocumentation/Frontend/OperationsPageImplementation/OperationsRefactorCrashCourse.MD` were read for accounting, settlement, evidence, scope, and operational limitations. In particular, a booked fill is internal booking, not exchange/custody proof; cash movements are internal and synchronous; journals are not a full general ledger; Operations cannot run or repair reconciliation.
- The `impDocs/Frontend/*` paths found during the first audit are not present in this branch. The governing instruction’s named `ImplementationDocumentation/...` docs and `docs/operations-runbook.md` are present and read.

### Finance facts that constrain UI copy

- Internal financial model: one USD ledger, provider-backed quotes, internal booking, zero project fees today. These facts do not authorize projecting totals without a valid response or labeling a journal a complete/double-entry ledger.
- `matched: true` means no current differences found within the response’s stated internal scope; it does not clear historical obligations or establish external settlement.
- `exceptions` belong to that persisted run. Browser search/filter applies only to the fetched run and must be labeled that way.
- Current summary is unavailable. A no-run reconciliation is a successful response with `runId: null`, not a matched report with zero exceptions.
- No detail or export endpoint is present in the read-only source map. Do not link to Operations routes, issue detail requests, or serialize a CSV.

## 3. Requirement and source/test matrix

State labels mean: **existing** = verified in current checkout; **missing frontend** = required UI work; **backend prerequisite** = genuine API/source work outside this task; **policy decision** = business/retention/scope/definition choice not supplied; **deferred** = expressly not delivered by this assignment.

| Requirement / feature | State | Current source symbol / boundary | Acceptance evidence |
|---|---|---|---|
| Finance role registration, home, menu links | existing | `WORKSPACES`, `landingPage()`, `routeRole()` in `workspace-policy.ts`; shared shell role filtering | Existing routing policy remains unchanged; Finance shell shows only its approved links and `/finance/reconciliation` remains home. |
| Finance route guard and role denial UX | existing, backend authoritative | `workspaceGuard`; controller `@PreAuthorize("hasRole('FINANCE') and !hasRole('CLIENT')")` | Direct navigation by CLIENT, ANALYST, ADVISOR, COMPLIANCE resolves to denial; adapter renders safe 403 for unauthorized API responses; mixed role is never enabled by frontend workaround. |
| Finance overview layout | missing frontend | Overview currently maps to `StaffPageComponent` via route data | Route/component spec renders dedicated page; shell stays shared; independent summary 503 does not hide reconciliation result. |
| Overview date scope, UTC and URL persistence | missing frontend; server filtering backend prerequisite | Proposed summary filter; no current implementation accepts/uses this range | Valid ISO calendar dates, inclusive display dates explicitly labeled UTC; Apply/Reset round-trip to query parameters; invalid/reversed range is blocked; never label this as applied server filtering while summary contract is unavailable. |
| Summary totals and no-activity state | backend prerequisite; metric policy decision; UI missing | Existing `GET /api/finance/summary` is 503; no valid summary response contract yet | Fixtures for valid populated and valid-empty response; invalid shape, 503, offline, 401/403 never render numeric zero. No metric is shown until valid typed response. |
| Movement/fill totals and reporting metadata | backend prerequisite; policy decision | No Finance-specific summary DTO/mapper/source endpoint | Only authoritative response fields render; distinguish event time/data-as-of from response generation/fetch time; show currency, definition version, source/status and limitations. |
| Latest reconciliation on overview | missing frontend; API existing | `ReconciliationService.latest()` / `GET /api/finance/reconciliations` | Summary failure and latest GET success render independently; no-run differs from no-current-exceptions. |
| Dedicated reconciliation report | missing frontend; API existing | `ReconciliationPageComponent` currently uses shared `PageResource` and GET/POST endpoint | Dedicated Finance page preserves existing URLs/response fields; tests for no run, matched, exceptions, history count, scope, UTC time and GET failure. |
| Explicit reconciliation run | partial existing (shared page); missing Finance interaction hardening | `TradingOperationsController.reconcile()`; POST persists an evidence record | Button sends exactly one POST on one activation; busy/repeat-click protected; save/run feedback separate from GET state; no client-side retry on timeout/unknown outcome. |
| Exception search/review | missing frontend | Existing report `exceptions[{kind,reference}]`; no server-side search/pagination contract | Search filters only loaded report rows and says “this run”; semantic caption/headings/scope; stable row identity; zero results is distinct from fetch error. |
| Fill/movement/posting record detail | backend prerequisite | No approved Finance detail endpoints | Show disabled explanatory sections/no broken links; no Operations deep links or unbacked detail requests. |
| Accounting export | policy decision + backend prerequisite; deferred in frontend | No Finance export API, scope/purpose/retention contract | Export affordance visibly disabled with reason; test no download/network request. |
| Fees / full trial balance | policy/definition gate; complete trial balance deferred | Fee rule is currently zero in internal booking; no full GL | Only show fee if an authoritative response includes a defined value; explain current journals are not a complete general ledger; no balancing-entry/tax invention. |
| Protected state / cancellation | existing utility, feature behavior missing | `PageResource<T>` clears on `sessionChanged` and cancels current request | Specs verify obsolete route/query requests cancel; 401/403 clear protected report/summary; no late response restores old identity data; no offline/503 converted to zero. |
| Accessibility and responsive behavior | missing frontend | global styles support focus and `.table-wrapper` | Labeled date/search controls; semantic tables with caption and `scope`; keyboard-visible focus; textual state labels not color-only; narrow viewport horizontal scrolling without page-level layout break. |
| Backend integration / reporting data | backend prerequisite; explicitly outside code scope | Summary intentionally 503, proposed detail/export APIs absent | Handoff describes exact current and proposed contracts, SQL/role/testing/deploy gates; final report marks integration pending. |

## 4. Target design and contracts

### Feature-local boundaries

Create a Finance-only feature under `frontend/src/app/features/finance/`, with separate `api/`, `overview/`, and `reconciliation/` ownership areas. Keep models next to the adapter and components. Do not add methods or types to shared `WorkspaceApi`.

- `FinanceApi` uses Angular `HttpClient`, dependency injection, `buildApiUrl()` and RxJS teardown. It exposes only `summary(scope)`, `latestReconciliation()`, and `runReconciliation()` for current frontend work.
- Current paths are exact: GET `/api/finance/summary`; GET `/api/finance/reconciliations`; POST `/api/finance/reconciliations` with `{}` to preserve the existing client contract. Current reconciliation GET/POST accept no query filters. Summary date filters are proposed and must be labeled unsupported until backend implementation. Keep the URL state; do not claim current server applies it.
- Normalize Spring/FastAPI style errors in this adapter (`message` and `detail`) into a safe Finance-local error model. Never expose sensitive response payloads or raw account data. HTTP status remains available for specific 401, 403, 404, 409, 422/400 validation, 429, 5xx/unavailable and offline states.
- Define a strict reconciliation model matching the verified response: `evidenceVersion`, `runId`, `checkedAt`, `matched`, `exceptions[{kind,reference}]`, `historicalExceptions`, `scope`, and no-run `message`. Type nullable/no-run instead of inferring a match.
- Define a strict **proposed** summary response beside the API: `schemaVersion`; `status` (`CURRENT`, `DELAYED`, or `PARTIAL`); `currency: "USD"`; `period {from,to,timeZone:"UTC"}`; `dataAsOf`; optional `generatedAt`; `definitionVersion`; `source`; `limitations[]`; `totals {settledDeposits,settledWithdrawals,grossBuyConsideration,grossSellConsideration,bookedFillCount,fees}`. Monetary values are decimal strings; fees are either absent/null or `{amount,definition}`. The page must validate the shape before rendering; zero activity is a successful typed summary, not an error fallback. The final backend handoff must reproduce the implemented proposal exactly and identify all business-definition decisions that block implementation.

### State separation and component behavior

- Overview owns independent summary and latest-reconciliation `PageResource`s. Summary failure never suppresses the latest report. Query changes cancel/reload only the appropriate resources and clear/mark scope-bound summary data before a new scope is displayed.
- Date filters use strict `YYYY-MM-DD`, UTC calendar semantics, Apply/Reset and query-string persistence. Until the backend honors summary filters, explicitly label the selected scope as requested UI scope only. Do not attach unrecognized `from`/`to` to existing reconciliation routes.
- Summary shows no amount/count tiles while unavailable or invalid. Valid empty shows source-backed zero/empty values with timestamp, scope and limitations. Populated fixtures render only authoritative returned values. An error is never “no activity.”
- Reconciliation has read state separate from run state. GET report stays visible during an explicit run where safe; POST failure does not erase the report or draft filter. Button guard prevents duplicate POSTs. No automatic retry, idempotency invention, mutation endpoint, or retry after a timeout.
- Render no-run message; current `matched` status; exception count; historical exception count; exact source `scope`; checked-at UTC. Search is local to the loaded exception array and uses stable `{kind,reference}` keys. Historical migration count is not merged into current exception rows.
- Details and export are honest read-only unavailable placeholders, not broken links. No request to mutation/trade/banking or protected Operations endpoints.
- Use existing shell, headings, `.workspace-heading`, `.panel`, `.notice`, `.section-heading`, `.table-wrapper`, CSS variables, buttons and focus patterns. No new component library or shell styles.

## 5. Execution phases, TDD and ownership

Complete each test-first phase in order. The `node_modules` directory is absent, so install from the existing lockfile (`npm ci` in `frontend`) before attempting the required Angular tests. No test has yet run; no red/green result is claimed. Confirm `ng test --help` / selector support from this installed CLI before relying on the exact focused command.

### Phase 0 — Preserve and establish baseline

1. Recheck `git status --short`; preserve the pre-existing untracked `.vscode/` and `FinanceRoleImplementationInstruction.MD` and branch `feature/finance_page`.
2. `npm ci` only if needed for test/build verification; do not change the lockfile or environment/compose volumes.
3. Record current role landing, exact GET/POST contracts and the current summary 503 in focused tests/fixtures; do not add a test claiming the backend summary is populated.

### Phase 1 — API/model contract, TDD

Owner: Finance API contributor/integrator. Files exclusively under `frontend/src/app/features/finance/api/`.

1. Write failing adapter tests for endpoint methods, body `{}` on POST, `buildApiUrl()` resolution, date parameter serialization for the proposed summary request (only if adapter sends it), normalizing `message`/`detail`, status preservation, cancellation and no request to any other API.
2. Implement typed response/request models and the narrow adapter.
3. Validate that cancellation unsubscribes outstanding requests and normalized errors do not retain protected payloads.

### Phase 2 — Overview, TDD

Owner: Overview contributor. Files exclusively under `frontend/src/app/features/finance/overview/`; consume Phase 1 public API/model exports.

1. Add a failing component behavior for summary HTTP 503 while latest reconciliation succeeds; expect explicit unavailable state and usable report.
2. Add failing behaviors for valid populated, valid-empty, malformed response, delayed response, offline, 401, 403, 404, 409, validation and 429 as applicable; assert no unsupported/failure state paints numeric zero.
3. Add date validation/query-param round trip, Apply/Reset, UTC labels and report/scope-clearing behavior.
4. Implement layout, source metadata, optional-fee display only when defined, independent latest report and clear detail/export policy notices.
5. Verify cancellation and protected-state clearing on identity change.

### Phase 3 — Reconciliation, TDD

Owner: Reconciliation contributor. Files exclusively under `frontend/src/app/features/finance/reconciliation/`; consume Phase 1 API/model exports.

1. Add failing behaviors for no run, matched, current mismatch, historical exceptions distinct from current findings, precise scope/checked-at, empty local search, and GET unavailable/401/403/404/409/429/offline states.
2. Add a failing rapid-double-click test that expects one POST; add timeout/unknown POST test proving no automatic retry and independent GET/run feedback.
3. Implement report UX, explicit single-flight run, safe message normalization, local search, semantic table, status text, and permission/session clearing.
4. Assert no financial mutation or export requests occur.

### Phase 4 — Serialized route integration

Owner: Integrator only, after both feature components compile. Files limited to `frontend/src/app/app.routes.ts` and Finance-specific additions to existing route test(s), most likely `frontend/src/app/app.spec.ts`.

1. Replace only the `/finance/reconciliation` `loadComponent` and the `/finance/overview` loader currently generated by the staff-route map. Do not change path, metadata, shell, route guards, any other staff/role route or existing `StaffPageComponent`.
2. Test finance role home/menu, both direct links and refresh resolution, unknown query preservation where relevant, unauthorized CLIENT/ANALYST/ADVISOR/COMPLIANCE behavior and 403 from backend for mixed role. Do not weaken `@PreAuthorize` or change workspace guards.
3. Preserve all shared reconciliation consumers/specs and all non-Finance route expectations.

### Phase 5 — Review, full checks, handoffs

Owner: Integrator; docs edited only after final API/component contract stabilizes.

1. Audit keyboard-only flow, date/search labels, status text independent of color, UTC wording, stable table keys/captions/column headers, narrow widths, error visibility, and no layout shift.
2. Run focused Finance specs first; then the full prescribed frontend checks once: `npm test -- --watch=false`, `npm run test:qof-styles`, `npm run build` from `frontend`.
3. Write `FinanceRoleBackendInstruction.MD` and `FinanceRoleFrontend_CrashCourse.MD` in repository root (the instruction file’s directory), based on shipped source and real test output. Do not claim backend implementation or runtime login verification.
4. Final `git status --short`; verify only Finance feature files, the two authorized route/test integration files, the two handoffs and this plan are new/changed beyond pre-existing untracked files. No lockfile changes.

## 6. Parallel work and merge-conflict controls

This is designed for parallel contributors while keeping shared seams serialized:

| Lane | Owns | Must not touch | Dependency |
|---|---|---|---|
| A — API/contracts | `features/finance/api/**` | Routes, shared API/resource, components, DB/backend | Define models and public adapter first; publish exact signatures before B/C start. |
| B — Overview | `features/finance/overview/**` | API contracts after freeze, routes, shared styles/auth | Starts after A contract freeze; can work in parallel with C. |
| C — Reconciliation | `features/finance/reconciliation/**` | API contracts after freeze, shared reconciliation, routes | Starts after A contract freeze; can work in parallel with B. |
| D — Route integrator | `app.routes.ts`, Finance-only route assertions | All other route/guard/policy/nav paths and feature source | Runs after B/C component exports stabilize; one serialized owner. |
| E — Handoffs | root `FinanceRoleBackendInstruction.MD`, `FinanceRoleFrontend_CrashCourse.MD`, final plan updates | Application source before review freeze | Runs after A–D and actual checks so examples and statuses match reality. |

If another contributor is already editing any serialized file, do not overwrite or reformat it; provide an exact proposed patch and wait for its integration. Avoid changes to `workspace-policy.ts`, `auth.guard.ts`, `WorkspaceApi`, `PageResource`, `RequestStateComponent`, global CSS, `StaffPageComponent`, Operations, Java, SQL and infrastructure. If an implementation need crosses those boundaries, stop code edits and create `SharedChangeProposal/README.MD` at the instruction directory with the exact impact/evidence/alternatives/order/security/tests/deployment/rollback/ownership/decisions before requesting approval. No such broader change is currently needed for the scoped frontend pages and docs.

## 7. Backend handoff prerequisites and policy gates

These are explicit future work, not frontend implementation tasks:

1. Implement/approve a genuine summary source and typed `GET /api/finance/summary` response matching the adapter proposal. Define if `from`/`to` are inclusive UTC dates and the max supported period before the UI claims they filter data.
2. Define settled movement inclusion vs rejected movement count; whether deposits/withdrawals are gross flow totals; fill-vs-order counts; buy debit/sell credit signs; gross vs net; fee definition/authority; opening/setup journal inclusion; reservation exclusion; historical migration exception treatment; as-of and generation timestamps; source and definition versions.
3. Keep exact current reconciliation transaction-record checks independent from historical summary. If history is too large for live query budget, ingest committed source facts through the reporting/outbox boundary; do not add wide historical scans to order booking or replace the matcher without scope authorization.
4. Decide Finance account scope, purpose/access audit, export purpose/redaction/retention, range/page/query limits, no-run/unavailable semantics, and immutable export manifest/download authorization. Generic ADMIN must not silently bypass Finance policy.
5. If detail endpoints are approved, supply list/detail contracts for fills, movements and postings with bounded pagination, stable ordering, decimal strings, UTC timestamps, ownership, scope and authorization. If exports are approved, authorize server-side immutable manifest and protected download, not browser-generated data.
6. Backend agent owns DTO/service/mapper/controller/schema, additive SQL/fresh-install parity/runtime grants, PostgreSQL/controller tests, migration allocation with integrator, rehearsal and rollback. No DB schema migration or backend code change is part of current frontend acceptance.

## 8. Risks, stop conditions, and final reporting

- **Stale source or session:** cancel obsolete requests; 401/403 clear protected data; no late response restore; no protected material in local/session storage.
- **Unavailable conflated with zero:** valid no-activity requires typed successful response; 503/offline/malformed are unavailable.
- **Unexpected contract drift:** normalize errors locally and fail closed on malformed typed success. Do not change shared error/resource behavior.
- **Financial claims:** keep words “internal”, “booked”, “scope”, “historical exception”, and explicit UTC/as-of; no “all accounts verified”, external transfer/custody claim, complete trial balance, or balance-repair action.
- **Scope expansion:** any need for shared guard/security/API, Java, SQL, global styling, financial writer/matcher or export framework change pauses code edits and triggers the proposal workflow.
- **Verification reporting:** report commands actually executed, actual focused/full counts and errors. If test tooling/install is blocked, state that precisely; do not claim acceptance checks passed. Frontend UI verification and backend source integration are separate status lines.

## 9. Requirement trace summary

- BR-09 atomic booking: frontend read-only/no write guarantee; backend booking remains existing and untouched.
- BR-10 correct cash/positions: display only returned authoritative totals; current reconciliation scope labels exact journal and hold checks; no balance repair.
- BR-14 durable attributed evidence: show existing run ID/check time/scope; reconciliation POST remains existing durable backend action; no edit/delete/export of evidence.
- BR-15 reconstruction: distinguish current findings from retained historical migration exception count; detailed reporting remains source/API prerequisite.
- BR-16/17 historical reporting: proposed isolated reporting endpoint, definitions/as-of/source gated; not represented as delivered by frontend.

The implementation can be marked frontend-complete only after both dedicated routes, API tests, independent summary/reconciliation states, read-only constraints, route preservation, accessibility checks and actual frontend verification pass. Backend reporting/detail/export integration remains pending until its contract and source prerequisites are delivered.
