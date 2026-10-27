# Shared-change proposal — deferred identity refresh and conditional reporting scale

**Proposal status (updated 23 October 2026):** the Finance summary-only endpoint is implemented in role-owned backend code and tested. No shared auth, global API, database, schema, grant, or reconciliation change was needed. This document is retained as a future impact proposal for (a) a separately approved live role-revocation freshness requirement, or (b) a separately approved reporting index/projection if representative query plans exceed the agreed budget. It does not authorize either change.
**Trigger:** a request to poll/revalidate Finance role permission while an authenticated page remains open, modify shared authentication/session behavior, add detail/history/export scope, or change the source/query architecture because measured all-ledger query performance misses an approved budget. The implemented summary accepts server-applied `from`/`to`; the running Compose backend was not rebuilt/restarted, and the endpoint is not confirmed deployed. Current client-side workspace navigation still uses the login/restoration identity snapshot, while direct HTTP remains server-authorized.

## Source evidence

- `backend/src/main/java/com/neueda/leap/controller/StaffWorkspaceController.java`, `financeSummary()`: implements `GET /api/finance/summary`, strict UTC date inputs and FINANCE-without-CLIENT authorization.
- `backend/src/main/java/com/neueda/leap/service/impl/FinanceSummaryServiceImpl.java` and `backend/src/main/java/com/neueda/leap/mapper/FinanceSummaryMapper.java`: repeatable-read aggregate over primary PostgreSQL facts; owner approval and query-plan performance review remain open.
- `frontend/src/app/features/finance/api/finance-api.service.ts`, `summary()`: feature-local adapter sends implemented `from`/`to`, validates the typed DTO, and preserves errors.
- `frontend/src/app/features/finance/overview/finance-overview.component.ts`, constructor/query handling: persists selected scope and renders summary/latest resources independently.
- `frontend/src/app/shared/workspace/page-resource.ts`, constructor/load: cancels request and clears data on `AuthService.sessionChanged`, but does not poll/revalidate authorization on a timer/focus or know when server-side permissions were revoked.
- `frontend/src/app/core/auth/auth.service.ts`, `bootstrap()`/`ensureInitialized()`: loads signed identity at login/restoration; no Finance-specific server role refresh lifecycle is exposed while the authenticated shell remains active.
- `frontend/src/app/core/auth/workspace-policy.ts` and `auth.guard.ts`: route/menu enforce the current client role set as UX, while direct Finance HTTP access is protected by the server. Mixed CLIENT+FINANCE can navigate to Finance but current Finance backend endpoints return 403.
- `backend/src/main/java/com/neueda/leap/controller/TradingOperationsController.java`: current reconciliation methods explicitly require FINANCE and exclude CLIENT; unchanged by the summary implementation.
- `backend/src/test/java/com/neueda/leap/controller/StaffWorkspaceControllerTest.java`, `backend/src/test/java/com/neueda/leap/service/impl/FinanceSummaryServiceImplTest.java`, and `backend/src/test/java/com/neueda/leap/trading/TradingPostgresTest.java`: summary role/contract/service/PostgreSQL coverage.
- `FinanceRoleBackendInstruction.MD`: actual summary contract and deferred detail/history/export proposals.

## Files and symbols potentially affected (proposal only)

### Backend reporting contract/source lane

- Completed current-slice files: `backend/src/main/java/com/neueda/leap/controller/StaffWorkspaceController.java#financeSummary`, `backend/src/main/java/com/neueda/leap/dto/FinanceSummaryResponseDto.java`, `backend/src/main/java/com/neueda/leap/service/FinanceSummaryService.java`, `backend/src/main/java/com/neueda/leap/service/impl/FinanceSummaryServiceImpl.java`, `backend/src/main/java/com/neueda/leap/mapper/FinanceSummaryMapper.java`, and their tests. Do not broaden these files in this proposal.
- Conditional future database/performance files only if measured evidence justifies: `database/fresh/001_schema.sql`, a new retained migration number coordinated with the database integrator, `database/fresh/002_reference.sql`, `database/runtime-grants.sql`, `database/README.md`, and PostgreSQL equivalence/performance tests. No database edits are currently proposed as necessary.
- Existing financial source records are `cash_journal`, `position_journal`, `cash_movements`, `order_fills`, `reconciliation_runs` and `financial_migration_exceptions`. These are not permission to introduce a second financial source of truth.

### Identity refresh/permission invalidation lane (only if required by a separately approved threat model)

- Potential shared files: `frontend/src/app/core/auth/auth.service.ts` (role refresh and generation/session event), `frontend/src/app/core/auth/auth.guard.ts` (revalidation policy), `frontend/src/app/shared/workspace/page-resource.ts` (new explicit permission-revocation integration only if its contract requires it), and shared auth/state tests.
- These are outside Finance feature ownership and not necessary to complete UI with current 401/403 handling; current proposal does not justify modifying them absent an explicit revocation/freshness SLA.

### Current route seams already integrated within assignment permissions

- `frontend/src/app/app.routes.ts` has only the Finance overview and reconciliation loaders delegated to feature-owned components. The shared app route/workspace policy remains as-is. No additional route integration is included in this proposal.

## Expected changes and rationale

1. Requester-selected summary policies (settled movements, booked fills, positive gross BUY/SELL, omitted fees, opening/reservation/history exclusion, UTC timestamps, 366-day maximum, FINANCE-without-CLIENT) are implemented and tested. Named Finance/accounting/security/data owners still need to approve the all-client aggregate permission and versioned definitions before production release.
2. The summary uses a repeatable-read primary PostgreSQL snapshot and separates transaction `dataAsOf` from response `generatedAt`. Run representative-volume `EXPLAIN (ANALYZE, BUFFERS)` and latency review. Existing movement/fill indexes may not support all-client date predicates; do not add a migration absent measured evidence and database-integrator approval.
3. Preserve current GET/POST reconciliation endpoints. Detail/history/export remain separate designs requiring their own field list, pagination, purpose, account policy, redaction and retention approval.
4. If live permission refresh is approved, define identity version/revocation mechanism, polling/focus/logout semantics, cancellation/generation protection, retry behavior and cache headers. Do not infer browser menu hiding is an API security boundary.
5. Future detail or export work must derive server scope and audit/purpose, add direct HTTP cross-account non-disclosure tests, preserve immutability, and define retention/redaction/download authorization first.

## Alternatives considered

- **Keep filters UI-only and summary unavailable** (prior frontend-only state): avoids misrepresenting date scope. Superseded in backend source by the implemented UTC-filtered aggregate contract; a running backend may still show prior behavior until redeployed.
- **Pretend client filters apply or compute totals from current page/browser state** (rejected): would produce incomplete/wrong totals, bypass source watermark and could treat partial data as financial totals.
- **Reuse Operations evidence/export APIs** (rejected): those APIs are purpose/account scoped for Operations and not Finance authorization contracts; no links or privilege widening.
- **Return zeros on source 503** (rejected): turns unavailable into false valid-empty data; implementation returns 503 on source failure.
- **Modify global authentication or shared PageResource now** (deferred): current feature state cancels and clears on session event and handles API 401/403. No approved role-revocation freshness requirement currently warrants broad shared edits.
- **Add a new financial ledger or rewrite reconciliation** (rejected): duplicates source of truth or broadens financial behavior beyond front-end assignment.

## Dependency order and impacted callers

1. Named Finance/accounting/security/data owners review the implemented summary scope, definitions, `dataAsOf` meaning and redaction.
2. Backend/database owners evaluate representative query performance and existing least-privilege SELECT grants; an index/migration is separate and only if justified.
3. Rebuild/restart the backend artifact in the approved environment and verify DTO, range/role matrix, unavailable and valid-empty semantics. This was not done in this session.
4. Keep reconciliation GET/POST and financial writer/matcher behavior unchanged.
5. Frontend adapter matches the implemented DTO; verify it against the deployed backend. It remains fail-closed when the source is unavailable.
6. Optional permission-refresh work requires its own implementation plan and owner approval before changing `AuthService`, guards or `PageResource`.

Affected callers include Finance overview and adapter only for the summary contract; reconciliation callers remain unchanged. Operations reconciliation remains read-only and purpose-scoped. Order admission, booking worker, movement writer, client pages and financial writer callers must remain unaffected. Any proposed change to these callers triggers a further proposal and approval.

## Security and data implications

- Server derives identity/account scope; query parameters never carry trusted client/account IDs.
- Explicit FINANCE without CLIENT is the current reconciliation role rule; no implicit ADMIN bypass. Cross-account/foreign UUID lookups are non-disclosing 404 where object detail is introduced.
- Approved purpose, access audit, redaction, retention and export download authorization are gates; do not expose bank account numbers, user identity or raw exception `facts`.
- Summary numbers must derive from approved immutable source facts and report version; no browser aggregation from paginated data.
- Current reconciliation remains a repeatable-read matcher; no write repair, journal mutation, hold release or exception deletion.
- Runtime database login remains non-owner and loses UPDATE/DELETE/TRUNCATE on evidence. New projection data must not become a second spendable balance or source of truth.
- Reporting outages, delayed watermarks, invalid DTO, rate limiting and permission loss remain textually distinct from valid-empty.

## Test plan

- Controller direct HTTP role matrix currently passes for no credentials, CLIENT, mixed CLIENT+FINANCE, ANALYST, ADVISOR, COMPLIANCE, TRADING_OPS, ADMIN-alone and FINANCE-only; denied calls do not invoke the summary service.
- Service tests cover the selected range/metric rules, exact decimals, valid-empty vs unavailable, and failure behavior.
- Disposable PostgreSQL test verifies source aggregation, settled vs rejected, fill count, UTC event-time window, no client identifier, and source-backed empty state.
- Full backend regression is 408 passed, 0 failures/errors/skips. Reconciliation/financial writer tests remain passing.
- Existing reconciliation regression tests still assert GET latest/no-run, POST persisted report, exact mismatch references, repeatable-read and no financial mutation.
- Migration tests compare fresh install to retained upgrade and preserve old evidence; rehearse backup/restore and rollback without dropping data.
- Finance Angular specs verify query contracts, typed response, status normalization, valid-empty vs 503, no fake amounts, session cancellation and the backend authorization result.
- If shared auth refresh is authorized: delayed old-user responses after refresh/revoke/login, browser storage identity event, polling/focus cancellation, role revocation clear, API failure fail-closed and no protected stale rendering.

## Deployment, migration, rollback

The packaged JAR contains the summary classes, but it was not deployed/restarted. No migration/grant work was needed or performed.

- Obtain named policy sign-off and representative query-plan/latency review before production.
- Rebuild/restart the backend service and verify HTTP responses under approved roles; do not touch a retained database for tests.
- If a future additive migration is justified, rehearse on disposable restored data, coordinate migration numbering, preserve fresh/retained parity and grants; never rewrite applied migrations.
- Keep source failure as 503; never seed fabricated data to populate totals.
- Review export retention/redaction before enabling future downloads. Application rollback preserves immutable schema/data; do not delete evidence or reverse postings.
- For optional permission-refresh UI, deploy identity-version/revocation contract before the UI requires it.

## Owners and unresolved decisions

**Proposed owners:** Finance product/accounting (definition/scope sign-off); security/privacy (aggregate permission and any audit requirement); data/reporting (snapshot meaning and query budget); backend service (summary source/tests); database integrator (conditional indexes/migration/grants); frontend Finance owner (deployment integration); operations owner (restart/release check).

Remaining production/release gates:

- Named owner review of the implemented all-client aggregate permission and exact definition version.
- Representative query-plan/latency budget and confirmation whether existing indexes suffice.
- Deployment/restart and browser-to-running-backend verification.
- Separate authorization/purpose/audit/redaction/retention design for future detail/history/export endpoints.
- Whether a distinct permission-revocation freshness SLA is required and how to implement without disrupting other workspaces.

**Approval request:** record named owner acceptance of the implemented summary policy and authorize query-plan review/deployment. Until these gates pass, treat source implementation as complete but production integration as pending; detail/history/export remain disabled.
