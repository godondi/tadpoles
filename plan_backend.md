# Finance Backend Implementation Plan

**Plan date:** 22 October 2026  
**Task source:** `FinanceRoleBackendInstruction.MD` (read from the current checkout before this plan)  
**Branch:** `feature/finance_page`  
**Execution status (updated 23 October 2026):** the summary-only backend slice is implemented test-first. The pre-existing contract test failed as expected against the original 503, then passed after implementation. No SQL, migration, runtime grants, financial writers, shared security, or reconciliation matcher were changed. Transaction-level detail/history and export remain deferred. Production release remains contingent on named Finance/accounting/security/data-owner review and approved-environment query-plan assessment.

## 1. Goal and delivery boundary

Provide a Finance-only, read-only **aggregate summary endpoint only** backed by the current USD financial source records, while preserving the existing reconciliation endpoints and financial writer invariants. Transaction-level fills, movements, postings, reconciliation history/detail, and export are out of this implementation slice. The requester selected “summary only; details deferred” after clarifying an earlier conflict between aggregate-only scope and transaction-level endpoint choices.

This work is not permission to:

- Credit/debit client accounts, update posted balances, write/edit/delete journals or fills/movements, release reservations, repair mismatches, or change any order, booking, banking or worker rule.
- Turn internal journals into a double-entry ledger/trial balance or assert that internal booking is external bank transfer, exchange execution, or custody.
- Add an unreviewed ADMIN bypass, reuse Operations' grant/context model for Finance, expose all clients without scope approval, or trust a caller-provided account ID.
- Add wide scans to order admission/booking, replace/change the existing reconciliation matcher, add a new financial source of truth, fabricate history, or delete/modify a retained migration.
- Reset, migrate, reseed, or test against the retained Compose database volume. All database tests and rehearsals must use disposable databases/restored copies.

## 2. Worktree and pre-existing state to preserve

The current branch is `feature/finance_page`. At the audit start the worktree already contained:

- Modified `.env.template`, `docker-compose.yml`, `frontend/angular.json`, `frontend/src/app/app.routes.ts`, `frontend/src/app/app.spec.ts`, and `nginx/nginx.conf`.
- Modified `backend/src/test/java/com/neueda/leap/controller/StaffWorkspaceControllerTest.java` with an in-progress Finance summary contract test. This test change is pre-existing at continuation start; preserve and complete its intent rather than overwrite it.
- Untracked `.vscode/`, the frontend Finance feature, the frontend implementation instruction/plan/handoffs, and `SharedChangeProposal/README.MD`.

These are outside this backend task except that `FinanceRoleBackendInstruction.MD` and `FinanceRoleFrontend_CrashCourse.MD` define the contract/handoff. Preserve all existing frontend/proxy changes and all untracked work. Do not reset/stash/checkout or rewrite history. No repository instruction files were found in the searched parent/workspace scope. No database was touched.

Backend implementation files added or changed by the summary slice:

- Added `backend/src/main/java/com/neueda/leap/dto/FinanceSummaryResponseDto.java`, `backend/src/main/java/com/neueda/leap/service/FinanceSummaryService.java`, `backend/src/main/java/com/neueda/leap/service/impl/FinanceSummaryServiceImpl.java`, and `backend/src/main/java/com/neueda/leap/mapper/FinanceSummaryMapper.java`.
- Updated only the Finance summary handler in `backend/src/main/java/com/neueda/leap/controller/StaffWorkspaceController.java`.
- Extended `backend/src/test/java/com/neueda/leap/controller/StaffWorkspaceControllerTest.java`; added `backend/src/test/java/com/neueda/leap/service/impl/FinanceSummaryServiceImplTest.java`; added one disposable PostgreSQL scenario in `backend/src/test/java/com/neueda/leap/trading/TradingPostgresTest.java`.
- No database schema, migration, runtime grant, demo seed, environment, Compose, or Frontend source was edited as part of this backend implementation.

## 3. Current source and operational documentation inspected

### Current API and implementation

- `backend/src/main/java/com/neueda/leap/controller/TradingOperationsController.java`: current Finance reconciliation `GET`/`POST`; both use `@PreAuthorize("hasRole('FINANCE') and !hasRole('CLIENT')")`.
- `backend/src/main/java/com/neueda/leap/service/impl/ReconciliationService.java`: POST takes actor ID, runs `@Transactional(isolation = REPEATABLE_READ)`, reads mismatch list plus historical count, creates report with `evidenceVersion=2`, UUID `runId`, server `checkedAt`, `matched`, current `exceptions`, historical count and scope; persists `reconciliation_runs` and reconciliation findings. GET returns stored JSON or HTTP-200 `{runId:null,message}`. No repair occurs.
- `backend/src/main/java/com/neueda/leap/mapper/ReconciliationMapper.java`: current checks and stable `ORDER BY kind,reference`; `historicalExceptions()` counts retained `financial_migration_exceptions`. Keep this matcher and its report/result unchanged.
- `backend/src/main/java/com/neueda/leap/controller/StaffWorkspaceController.java`: `GET /api/finance/summary` now delegates strict UTC date inputs to `FinanceSummaryService`; uses `hasRole('FINANCE') and !hasRole('CLIENT')`. The service returns the approved summary-only DTO; source errors remain 503. The earlier placeholder is no longer the current working-tree contract.
- `backend/src/main/java/com/neueda/leap/controller/SecurityRoleSupport.java`, `config/SecurityConfig.java`, and `exception/GlobalExceptionHandler.java` are shared security/error paths. Do not modify without a separate change proposal/approval.
- Existing Java package layout: `com.neueda.leap.controller`, `.dto`, `.mapper`, `.service`, `.service.impl`. Spring Boot 3.3.5, Java 17, MyBatis, PostgreSQL, JUnit/Spring MockMvc/Testcontainers. Avoid a new framework or persistence approach.

### Existing backend tests

- `backend/src/test/java/com/neueda/leap/controller/StaffWorkspaceControllerTest.java`: Finance response/date/role tests plus authorized/unauthed/wrong-role behavior for remaining Compliance/Analytics placeholders.
- `backend/src/test/java/com/neueda/leap/controller/WorkspaceAuthorizationTest.java`: direct legacy financial read/write denials for workspace roles, including FINANCE; verifies denied calls do not reach mocks; mixed CLIENT+FINANCE still has owned CLIENT reads.
- `backend/src/test/java/com/neueda/leap/trading/TradingPostgresTest.java`: current reconciliation tests around `financeReconciliationPersistsRealMismatchReferences`, `reconciliationFindsFilledOrderWithoutFillEvidence`, and `operationsReconciliationRedactsOtherAccountsAndKeepsFinanceAuthority`. It verifies a persisted run and actual mismatch, existing role authority, and report separation. Keep these intact.
- `testing/src/test/java/com/neueda/leap/db/FreshTradingSchemaTest.java`: fresh/upgrade schema comparison includes migration 012; existing migration runner/postgres tests have version-12 assumptions. Add tests to existing isolated PostgreSQL paths; no H2-only validation of PostgreSQL semantics.
- `backend/pom.xml`: Spring/JUnit/Security/Testcontainers dependencies already exist; no new persistence or web framework should be installed.

### Database facts and runtime privileges

- `database/README.md`: PostgreSQL 15; fresh install is `database/fresh/001_schema.sql` plus `002_reference.sql`; retained upgrades use ordered `database/migrations/` through `012_managed_support.sql`; `migrate.sh` is advisory-locked and migration numbers/files are immutable once applied; fresh/upgrade parity and runtime grants are separate; retained volumes are never reset.
- `database/fresh/001_schema.sql`: existing source tables include `cash_movements`, `cash_journal`, `position_journal`, `order_fills`, `reconciliation_runs`, `financial_migration_exceptions`, order and reservation records; timestamps are `timestamptz` on these runtime facts. Amount/quantity columns use PostgreSQL `numeric`; `order_fills.fee=0` is currently constrained. `cash_movements` statuses are SETTLED/REJECTED; only settled rows correspond to cash postings. Setup/opening journal entries are separate source facts.
- `database/runtime-grants.sql`: current non-owner runtime already receives `SELECT` on source tables and `reconciliation_runs`; existing evidence is explicitly denied `UPDATE/DELETE/TRUNCATE`. Any new view/table requires least-privilege review; no grant change is presumed necessary if summary uses current tables.
- Performance gate: the current `cash_movement_history` index leads with `client_id`; no `booked_at`-leading `order_fills` index was found. This all-ledger date-range query may sequential-scan both immutable source tables. No index or migration was added without representative-volume evidence. Run isolated `EXPLAIN (ANALYZE, BUFFERS)` before production release; if the approved latency/query budget is not met, stop and coordinate an additive index proposal plus migration/fresh parity/runtime grant review.
- `database/migrations/003_workspace_roles.sql` through `012_managed_support.sql` were located; the Database guide documents their purpose. No migration is justified at plan time. If query analysis later proves an additive index/projection necessary, stop and coordinate migration number, fresh snapshot, runtime grants, parity tests, migration rehearsal, and rollback with DB integrator.

### Product and operational docs

- `FinanceRoleBackendInstruction.MD`: the task source and final status handoff, implemented/proposed endpoint distinction, role/data/test/rollback rules, selected metric definitions, remaining named-owner approval and performance gates.
- `database/README.md`: retained/fresh install, permissions, source-of-truth, immutability and upgrade constraints as above.
- `docs/operations-runbook.md`: reconciliation is an internal consistency view, not custody/bank reconciliation; Finance retains run authority; do not repair postings; preserve evidence.
- `ImplementationDocumentation/Frontend/ClientPageImplementation/ClientRefactorCrashCourse.MD`: current model is one USD ledger, booked fill means internal commit, internal movements settle synchronously, fee is zero, historical exceptions remain quarantined, no full GL; transaction outboxes define a separate reporting boundary.
- `ImplementationDocumentation/Frontend/OperationsPageImplementation/OperationsRefactorCrashCourse.MD`: scoped Operations exports/context/grants are specifically Operations behavior, not an automatic Finance contract; preservation, redaction, least-privilege and error behaviors.
- `FinanceRoleFrontend_CrashCourse.MD`: Finance adapter sends `from`/`to`, validates the implemented decimal-string/UTC DTO, and still handles 503 without rendering totals. This establishes frontend source-contract compatibility, not named-owner policy sign-off or a deployed backend.
- `SharedChangeProposal/README.MD`: existing proposal records outstanding Finance/reporting scope, metrics, source, export and optional permission-refresh blockers; no additional shared security changes are authorized.

## 4. Requirement and source/test matrix

State labels: **existing** = verified current behavior; **backend prerequisite** = absent server behavior needed for a frontend contract; **policy decision** = owner approval required; **deferred** = no implementation until gate is resolved.

| Feature / requirement | Current state and source | Backend target and acceptance evidence |
|---|---|---|
| Finance current latest/run reconciliation (BR-14/15) | Existing in `TradingOperationsController.latest()/reconcile()` and `ReconciliationService.latest()/run()`; HTTP authorization excludes CLIENT. Existing PostgreSQL/controller tests assert persistence and actual mismatch. | Preserve exact paths, request body compatibility, no-run JSON, report v2 fields, ordering, repeatable-read, historical count separate, and no financial repair. Extend characterization only if an identified gap exists. |
| Summary HTTP response | Implemented in `StaffWorkspaceController.financeSummary()`; requires both ISO dates and FINANCE without CLIENT; service failures remain 503. | Current handler returns typed summary only on successful repeatable-read source access. Unit/controller tests cover successful response, missing filters, denied role matrix and source unavailable. Production enablement remains owner-approval gated. |
| Summary metrics BR-09/10/16/17 | Implemented against current source tables for all-client aggregates. Project fee is omitted; internal USD only. | DTO returns settled movement amounts, separate positive gross BUY/SELL consideration, distinct booked-fill-row count, exact decimal strings, source/definition/limitations and separate DB `dataAsOf`/server `generatedAt`. PostgreSQL integration test covers settled/rejected, booked fills, UTC range and valid empty. No setup/opening/reserved amounts or historical exceptions enter totals. |
| User/account scope | Reconciliation matcher is an aggregate current-ledger consistency check. No approved Finance detail/account-scope policy exists in checked code/docs. | Decide all-ledger Finance scope vs explicit account grant scope, identifiers/redaction and count semantics before adding aggregates/details. Apply authorization filter in SQL before total/page for any scoped lists. Direct cross-account tests and 404 non-disclosure. No caller-supplied trusted account ID. |
| UTC date boundaries / data-as-of | Implemented: inclusive UTC dates translated to `[from 00:00Z, day-after-to 00:00Z)`; max 366 days; `to` cannot be after UTC today. `dataAsOf` is PostgreSQL `transaction_timestamp()` from the repeatable-read transaction; `generatedAt` is injected UTC clock time. | Service unit tests cover max/future/inverted dates, half-open UTC boundaries, and distinct timestamps. Owner still needs to approve as-of wording/staleness. |
| Fill / movement / posting detail | No Finance detail endpoint. Existing source facts are immutable; runtime read permission currently exists. | Only implement if approved as required: stable page/page-size max/order, decimal-string fields, UTC, no bank details/raw facts, counts after scope, source references. Controller/service/mapper/PostgreSQL tests. |
| Reconciliation history/detail | Only latest report GET and run POST exist. Current reports/finding rows are immutable source evidence. | Optional proposed routes must read stored reports/findings without rerunning matcher. Stable order, bounded paging and policy/scope approval; preserve latest contract and no-run behavior. |
| Export | No Finance export API/policy. Operations exports are purpose/context/scoped. | Deferred. Do not reuse Operations API or add bytes/download until purpose, scope, redaction, immutable manifest/digest, audit, TTL/retention and authorization-at-download are signed off. |
| Financial write invariants | Booking/movement writers and DB guards already enforce atomic updates/immutable evidence. | Zero writer changes. Summary is read-only. Full backend suite including transaction/authorization/reconciliation tests passed. |
| Fresh/retained DB and runtime grants | Current sources already grant SELECT; migrations through 012. | No migration unless query budget evidence justifies. If needed: integration allocates sequence, additive migration + fresh snapshot + reference marker + grants + equivalence + populated-upgrade rehearsal + rollback plan, all in separate authorized slice. |


## 5. Decision record and remaining release gates

The requester selected the following engineering scope through the clarification workflow on 22 October 2026. The requester's name/role was not provided, so this is the implementation instruction but not evidence of named Finance/accounting/security owner sign-off for production release.

### Requester-selected implementation decisions

| Decision | Selected value |
|---|---|
| Account scope | All-ledger aggregates only. No transaction-level rows or account-identifying detail in the response. |
| V1 endpoint scope | `GET /api/finance/summary` only; existing reconciliation GET/POST remain unchanged. Fills, movements, postings, reconciliation history/detail and export are deferred. |
| Movement metrics | Include only `SETTLED` USD movements in positive deposit/withdrawal totals. Rejected movements do not enter amounts or totals; no rejected count is added to the current DTO. |
| Fill metrics | Booked fills only; report distinct fill-row count, not order count. Keep BUY/SELL gross consideration as separate positive totals, no netting. |
| Fees/openings/holds/history | Omit fees; exclude setup/opening postings and reservations from movement/fill totals; historical migration exceptions remain separate from summary totals and visible through existing reconciliation only. No FX. |
| Time | Fills use `booked_at`; movements use `recorded_at`. UTC dates are inclusive in the request and implemented as half-open UTC timestamp intervals `[from 00:00Z, day-after-to 00:00Z)`. |
| `dataAsOf` | Primary PostgreSQL repeatable-read transaction snapshot time, not an ingestion watermark. `generatedAt` is a separate response-generation time. Response source/limitations must state this precisely. |
| Range/future dates | At most 366 inclusive UTC calendar days; reject end dates after server's current UTC date. |
| Authorization | Require FINANCE and reject CLIENT even if mixed. ADMIN-alone is denied. Aggregate-only response; details/exports deferred. No purpose/audit source is added for this summary slice absent a separately approved requirement. |

### Remaining named-owner release gates

- Finance/accounting owner: validate the selected amount/count definitions, fee omission, setup/reservation/historical exclusions, USD-only scope, and version label.
- Security/privacy owner: validate all-ledger aggregate permission, `FINANCE && !CLIENT`, ADMIN denial, no account identifiers, and whether aggregate access requires a separate audit event.
- Reporting/data owner: validate event timestamps, PostgreSQL snapshot-time `dataAsOf` meaning, status/source/limitations wording, staleness behavior, and query budget.
- Database integrator: review representative query plans and confirm existing indexes/runtime SELECT grants suffice. Any migration/index request is a separate approval gate.

If the requester is the authorized owner for any of these areas, record name/role and explicit sign-off before production release. Implementation may proceed test-first against the selected contract, but production release remains contingent on these review gates.

The requester selections resolve endpoint list, aggregate-vs-record scope, role matrix, metric signs/inclusions, fee/opening/reservation/history treatment, timestamps, `dataAsOf`, range, and future-date behavior. Remaining technical work is query-plan validation and confirmed definition/source metadata. Future detail/history/export requests require new scoped approval.


If the requester is the authorized owner for any of these areas, record name/role and explicit sign-off before production release. Implementation tests can proceed against the selected proposed contract, but the endpoint must remain fail-closed/unreleased until the owner gates are recorded.

Implementation decisions resolved by the selections above include endpoint list, aggregate-vs-record scope, role matrix, metric inclusion/sign, fee/opening/reservation/history treatment, timestamp axis, `dataAsOf`, 366-day maximum and future dates. Remaining technical discovery is query performance, representation/definition version confirmation, response cache policy, and named-owner sign-off. Any future detail/history/export request restarts the gate with its own field list, access purpose, pagination and redaction decision.

The following were the initial decision questions. The requester selections above resolve the implementation choices; named owner validation is still required before production release:

1. **V1 endpoint scope:** Summary only, or also fill/movement/posting detail and stored reconciliation history/detail? Export is expressly deferred until its own policy review.
2. **Finance scope:** Is FINANCE authorized to aggregate all client ledger data in this installation, or must scope be an explicit provisioned subset? Should any client/account-identifying records be returned? Who provisions/revokes that authority? The current matcher’s global consistency scan is not a drill-down authorization model.
3. **Role matrix:** Requester selection for summary is FINANCE without CLIENT and ADMIN-alone denied; implementation tests enforce this. Named Security/Finance owner must affirm the broad aggregate permission before release. Deferred detail/history remain separately gated; no implicit ADMIN bypass.
4. **Metrics:** Settled vs rejected movement count/totals; BUY debit and SELL credit representation; gross vs net; distinct booked-fill vs distinct-order count; fee (omit, or reported zero with exact definition/version); setup/opening entries; reservations excluded; historical migration exception inclusion/exclusion and presentation.
5. **Time and source:** Event timestamp selected for each metric (`booked_at` fills, `recorded_at` movements unless approved otherwise); inclusive UTC dates translated to half-open instants; maximum range; no future periods policy; snapshot-time vs source-ingestion watermark meaning; tolerated staleness and PARTIAL/DELAYED status criteria.
6. **Operational limits:** max page size, pagination strategy (offset/cursor), max response, query timeout, rate limit, data volume budget and acceptable latency. Measure existing indexes/query plans before deciding on any SQL or migration.
7. **Fee/report version policy:** definition owner, semantic version cadence and whether zero fee is exposed despite no current fee economics.
8. **Sensitive access:** purpose/access audit requirement for aggregate reports vs detail, audit failure behavior, request/cache headers, redaction; do not inherit Operations authorization by accident.
9. **Data source architecture:** bounded primary DB query under a repeatable-read snapshot vs isolated reporting projection. If a reporting projection is needed, its outbox ingestion, deduplication, watermark, lag, rebuild/replay and reconciliation need separately approved design; no wide history scan on live trading path.
10. **Export:** explicitly excluded from V1 or approve an independent format/purpose/scope/redaction/digest/download/expiry/retention/deletion policy. Until approved it remains absent.


### Decision record requirement

The requester selections above are recorded in this plan. Before production release, append named approver identities/roles, date, accepted definition version, and any changed choices. Do not infer backend deployment or production approval from the frontend adapter alone.


## 6. Implemented summary contract (working tree; not yet deployed)

The summary route now returns `FinanceSummaryResponseDto` from `GET /api/finance/summary` in `StaffWorkspaceController.financeSummary()`, delegating to `FinanceSummaryServiceImpl` and a single aggregate query in `FinanceSummaryMapper`. Its request is strict `from=YYYY-MM-DD&to=YYYY-MM-DD`, inclusive UTC dates mapped to a half-open database interval; maximum 366 calendar days, no future `to`, and no caller-selected account ID.

Successful response fields are `schemaVersion`, `status`, `currency`, `period:{from,to,timeZone}`, `dataAsOf`, `generatedAt`, `definitionVersion`, `source`, `limitations[]`, and `totals:{settledDeposits,settledWithdrawals,grossBuyConsideration,grossSellConsideration,bookedFillCount}`. USD amounts are exact two-decimal JSON strings; fees are omitted. `dataAsOf` is `transaction_timestamp()` within the primary PostgreSQL repeatable-read transaction; `generatedAt` comes from the injected UTC clock. Source errors/null aggregates return 503. A successful empty source result returns authoritative `0.00` monetary strings and zero fills; an error never becomes zero.

Authorization is `hasRole('FINANCE') and !hasRole('CLIENT')`. The response aggregates all installations' client data without client IDs, transaction rows, or bank fields, according to requester-selected policy; a named Security/Finance owner must still approve before production release. Existing reconciliation routes remain unchanged and tested.

| Method/path | State | Request / behavior |
|---|---|---|
| `GET /api/finance/summary` | Implemented in source; deployment not yet confirmed | Required strict `from` and `to` ISO dates; max 366 inclusive UTC days; reject future end date; no client/account selector. 200 for source-backed totals including valid-empty, 400 for invalid/missing range, 401 unauthenticated, 403 role denial, 503 primary source unavailable. |
| Existing `GET /api/finance/reconciliations` | Existing, preserved | No filters; stored v2 latest report or 200 `{runId:null,message}` when no run exists. |
| Existing `POST /api/finance/reconciliations` | Existing, preserved | Empty body/client `{}`; repeatable-read run persisted; does not repair/alter financial books. |

Optional future paths in `FinanceRoleBackendInstruction.MD` remain individually **proposed**, not part of V1 absent approval: `/api/finance/fills[/{fillId}]`, `/api/finance/movements[/{movementId}]`, `/api/finance/postings[/{postingId}]`, `/api/finance/reconciliations/history`, `/api/finance/reconciliations/{runId}`, `/api/finance/exports` and `/api/finance/exports/{exportId}/download`.

## 7. Implementation sequence and execution record

### Phase 0 — preserve and baseline (no destructive operations)

- Rechecked `git status --short` before source edits; preserved existing frontend/proxy changes and local environment.
- Capture Java version, Maven availability, Docker/Testcontainers availability, current branch and DB test prerequisites. Do not run against current Compose database.
- Preserve the pre-existing test-first red phase: `mvn -q -f backend/pom.xml -Dtest=StaffWorkspaceControllerTest#financeSummaryReturnsScopedAggregateContractForAnInclusiveUtcRange test` failed because actual summary status was 503, expected 200. Maven compilation completed. This is a meaningful contract failure, not an infrastructure failure.

### Phase 1 — freeze selected contract and traceability

- Implement only the requester-selected summary-only contract; do not implement detail/history/export in this task.
- Record the approved DTO fields, definition identifier, response status behavior and role checks in tests; named owner validation remains a production release gate.
- Have database/security/reporting owners review whether existing table SELECT privileges and indexes meet scope/query requirements before release.
- If a required change hits shared security filters, `SecurityRoleSupport`, `GlobalExceptionHandler`, financial writers, reservation/idempotency/booking, or current matcher, stop and write/update `SharedChangeProposal/README.MD`; obtain separate approval.

### Phase 2 — tests first, API contract and authorization

- Completed: the red MockMvc DTO contract was expanded to use a deterministic service response; role/date validation tests cover no token, CLIENT, ANALYST, ADVISOR, COMPLIANCE, TRADING_OPS, ADMIN-alone and mixed CLIENT+FINANCE, with no service call on denied access.
- Completed: `FinanceSummaryServiceImplTest` covers date rules, UTC boundaries, exact decimals and unavailable/null source.
- Completed: `TradingPostgresTest.financeSummaryUsesSettledMovementsBookedFillsAndUtcDateBoundaries` exercises internal settled/rejected movement and fill facts on disposable PostgreSQL, the inclusive date, source-backed empty range, and absence of account-identifying output.
- Detail endpoints remain out of scope; any future additions require separate approval and cross-account tests.

### Phase 3 — role-owned DTO/service/mapper, no financial writer changes

- Completed in role-owned components: `FinanceSummaryResponseDto`, `FinanceSummaryService`, `FinanceSummaryServiceImpl`, `FinanceSummaryMapper`, and the narrow summary handler replacement in `StaffWorkspaceController`.
- Completed: independent source aggregates avoid fill/journal multiplication; rejected movements, openings, reservations, fees and historical exceptions are excluded from these selected totals; no FX or trial-balance data is inferred.
- Completed: `BigDecimal` source values are serialized to two places with `RoundingMode.UNNECESSARY`, and any unexpected scale fails rather than silently rounding.
- Completed: 503 for source failures/missing source aggregate; 200 valid-empty only after successful primary PostgreSQL query.

### Phase 4 — detail/history (optional approved scope only)

- Implement only individually approved read endpoints. Each endpoint has server-derived scope, bounded date/page inputs, stable tie-break order, limit validation, filter-before-count/page, exact decimal/time serialization, omission/redaction of sensitive fields and no update/delete verbs.
- Read stored reconciliation JSON/findings only; never re-run matcher for detail/history.
- Add service and PostgreSQL tests for ordering ties, range boundaries, pagination completeness, zero-page vs unavailable, masking and cross-account non-disclosure.

### Phase 5 — performance, schema/grants, and rollout (conditional)

- Capture PostgreSQL EXPLAIN/ANALYZE on representative isolated data. Use current indexes first. Only if justified, request an additive index/projection change proposal and coordinate migration number with integrator; no migration number is assumed by this plan.
- If schema changes are approved, add retained migration and matching fresh snapshot/reference bookkeeping, runtime grants and guide/tests. Do not edit migrations 003–012. Rehearse on a disposable restored database with populated records; test repeated runner and rollback preserving evidence.
- Validate with non-owner restricted runtime role. New read sources get SELECT only; any report/export insert privilege must be narrowly justified. Reject UPDATE/DELETE/TRUNCATE on immutable evidence.
- Deploy backend only after test, security and DB parity gates; frontend adapter then aligns with the deployed DTO. Keep frontend unavailable state until deployment is confirmed.

### Phase 6 — full verification and handoff

- Focused `mvn -B -f backend/pom.xml -Dtest=StaffWorkspaceControllerTest test` plus newly created focused tests.
- `mvn -B -f backend/pom.xml test` and, when changed, `mvn -B -f testing/pom.xml test` with Docker/Testcontainers or isolated test DB documented. Do not claim success if infrastructure unavailable.
- Run the existing disposable fresh/retained migration equivalence and role-workspaces/recovery harness only when required and available. No live provider needed for financial summary tests.
- Verify unchanged Finance reconciliation contracts, authorization, no financial source mutations, exact decimals, UTC semantics, scope and unavailable/error behaviors; run `git diff --check` and inspect exact diff.
- Update `FinanceRoleBackendInstruction.MD` with actual implemented/proposed endpoints and `FinanceRoleFrontend_CrashCourse.MD` only with confirmed integration/status and actual results. Do not replace plan or claim complete if policy/source remain pending.

### Actual execution record — 23 October 2026

- TDD red phase: `mvn -q -f backend/pom.xml -Dtest=StaffWorkspaceControllerTest#financeSummaryReturnsScopedAggregateContractForAnInclusiveUtcRange test` failed as intended: endpoint returned 503 instead of contract-test expected 200.
- Focused Finance suite before the final malformed-date controller case: 9 controller tests, 5 service tests, and 1 PostgreSQL scenario passed. The final controller-only rerun passed **10 tests** after adding malformed ISO date coverage.
- Full regression rerun after all code/test changes: `mvn -q -f backend/pom.xml test` passed **408 tests, 0 failures, 0 errors, 0 skipped**. `TradingPostgresTest` ran 51 tests, including the new Finance summary scenario. Testcontainers used a disposable PostgreSQL 15 database.
- `mvn -B -f backend/pom.xml -DskipTests package` produced `backend/target/team-skeleton.jar`, and the jar contains the Finance summary controller, DTO, mapper and service classes.
- No schema/migration/runtime-grant change; no retained Compose database was reset or migrated. After the initial deploy revealed Nginx still resolving the removed backend container IP, Nginx alone was restarted to refresh upstream DNS. Backend/frontend were rebuilt/recreated and the authenticated smoke test through Nginx passed: Finance login 200, requested UTC range echoed exactly, DTO/two-decimal values valid, no account IDs, anonymous summary 401, latest reconciliation 200, Finance overview SSR 200. Demo token stayed in process memory and was not printed.
- Finance adapter now rejects a summary response whose period does not match the requested range; the overview scope message says dates are sent to the report service. Focused Finance suite passed 26 tests; app route/auth suite passed 13. The optimized SQL aggregates each source independently in one scan per table. Full backend suite after that SQL change passed 408 tests (0 failures/errors/skips).
- Query-plan/performance review: to get a directional estimate without touching retained data, a temporary PostgreSQL 15 container was populated with synthetic-only data (5,000 synthetic clients, 500,000 movements and 200,000 orders/fills over 730 dates), then removed. With current indexes, `EXPLAIN (ANALYZE, BUFFERS)` showed parallel sequential scans: about 256 ms for a 366-day range and 86 ms for a 1-day range. In the same disposable dataset, temporary date-leading covering indexes reduced the 1-day plan to about 1.6 ms, while the 366-day range remained sequential/parallel and about 281 ms. This is not representative of production or a release sign-off; no query budget or realistic data profile was supplied, and no index was retained. Named data/operations owners must define the latency budget and the integrator must approve a migration before any schema index is added.
- Named Finance/accounting/security/data-owner approval is still pending; the user asked to skip recording approvals. Code/deployment does not substitute for sign-off. Detail/history/export remain deferred.

## 8. Parallel work lanes and merge-conflict controls (planning record)

| Lane | Owns after approval | Avoids | Dependency |
|---|---|---|---|
| A — Policy/API contract | Decision record; approved V1 DTO/status/role/time/limits | Application source, migration files | Must finish before code tasks. |
| B — API/controller | Summary handler in `StaffWorkspaceController.java`; controller tests | Auth/security globals, financial writers, reconciliation matcher, unrelated controllers | Executed after requester contract selection; one serialized controller seam. |
| C — SQL mapper/service | Finance-owned summary DTO/service/mapper and tests | Writer classes and shared mapper/reconciliation SQL | Executed on role-owned files after contract freeze. |
| D — DB/performance | Read-only EXPLAIN and current index review; migration only after explicit need/approval | Migration renumbering and existing applied SQL | Starts after A range/scope and C query shape; DB integrator owns numbering/parity. |
| E — Security test reviewer | Direct role/cross-account HTTP tests and DB non-owner access assertions | Policy changes without owner sign-off | Review in parallel with B/C after contract freeze. |
| F — Frontend integrator/docs | Align adapter only after actual backend contract deployed; handoff/status updates | Editing frontend now or asserting backend availability | After backend tests and contract delivery. |

No shared file is edited concurrently. If another contributor owns a seam, provide exact patch and await integration rather than overwrite it. Re-run status before touching the route placeholder or any documentation.

## 9. Risks, release gates, rollback and final status

- **Named owner sign-off absent:** implementation exists but production release remains gated. Do not represent code presence as policy approval or deployed service.
- **Scope approval:** requester selected all-client aggregates without account identifiers, and code enforces that scope. Named Finance/security approval remains required before deployment; do not expand into transaction rows or account-identifying data.
- **Data-as-of:** implemented as PostgreSQL `transaction_timestamp()` for the repeatable-read transaction, not an ingestion watermark. Named data/reporting owner must approve this exact meaning before release.
- **Query budget failure:** stop and propose an isolated reporting source or additive index; no history scan in order booking.
- **Shared or core change needed:** stop source edits and produce detailed `SharedChangeProposal/README.MD`; current proposal already records the reporting gate but must be updated if new files/symbols/change impacts are identified.
- **Migration required:** allocate with integrator, test both fresh/retained paths, restricted grants and rollback; preserve data and all applied migration files.
- **Rollback:** deploy compatible app code while retaining additive schema and immutable evidence. No evidence deletion, compensation posting or volume reset.
- **Current status:** summary backend code is deployed in the currently running backend/frontend Compose services and authenticated through-Nginx smoke passed. Full backend suite passed 408 tests. No schema change or migration was needed. Named policy approval and representative query-plan/performance review remain pending. Detail/history/export remain deferred.

## 10. Remaining approvals and technical gates

The requester-selected implementation decisions are recorded in §5. The live Compose services were rebuilt and smoke-tested, but the requester selected “skip this step” for named approval recording; production approval therefore remains explicitly pending. Obtain owner sign-off on all-ledger scope/definitions and the meaning of transaction-time `dataAsOf`. Run isolated representative `EXPLAIN (ANALYZE, BUFFERS)` with an approved latency/scan budget and representative data. If that gate fails, stop and prepare the separate additive-index/reporting-projection proposal before editing SQL.
