# Advisor Local Implementation Plan and Status

Date: 2026-10-22
Source: `AdvisorRoleImplementationInstruction.MD`

## Current backend result — 2026-10-22

**Implemented and deployed:** all eleven Advisor/client-advisory APIs used by the frontend, session-derived object scoping, current-ledger portfolio reads, versioned guidance/NONE clears, not-held recommendation create/edit/archive, authorized catalog, client assignment/advisory reads, dedicated persistence and immutable attributed history. No frontend adapter change was necessary. The original unavailable-only frontend is now connected to real Spring/PostgreSQL data through existing nginx routing.

**Still deferred:** automatic onboarding assignment/production allocation policy, assignment event timestamps/history, legacy ID-based API authorization changes, a history-view API and frontend catalog navigation beyond its existing first 100 items. Historical assignedAt returns null rather than invented time. This is the initial connected backend slice, not completion of all original onboarding requirements.

### Changed files in the backend follow-up

New role-owned production files under `../../../backend/src/main/java/com/neueda/leap`:

- `controller/AdvisorWorkspaceController.java`, `controller/ClientAdvisoryController.java`, `controller/AdvisorWorkspaceExceptionHandler.java`
- `dto/AdvisorWorkspaceDtos.java`
- `mapper/AdvisorWorkspaceMapper.java`
- `service/AdvisorWorkspaceService.java`, `service/impl/AdvisorWorkspaceServiceImpl.java`

New tests under `../../../backend/src/test/java/com/neueda/leap`:

- `controller/AdvisorWorkspaceControllerTest.java`
- `service/impl/AdvisorWorkspaceServiceImplTest.java`
- `mapper/AdvisorWorkspaceMapperTest.java`
- `advisor/AdvisorWorkspacePostgresTest.java`

Additive database/deployment seams:

- New `../../../database/migrations/018_advisor_workspace.sql`.
- Matching Advisor DDL appended to `../../../database/fresh/001_schema.sql`; marker 13 in `database/fresh/002_reference.sql`.
- Targeted `../../../database/runtime-grants.sql` additions: projection SELECT/INSERT/UPDATE, no delete/truncate; history SELECT only, direct writes/helper execution revoked. Trusted security-definer snapshot trigger appends history with fixed search path.
- Narrow migration-list/count update in `../../../testing/src/test/java/com/neueda/leap/db/FreshTradingSchemaTest.java`.
- New `../../../scripts/smoke-advisor-workspace.py`, read-only by default, with explicit optional demo mutation verification.
- Updated database guide and the three Advisor handoff/proposal documents.

No existing Java service/controller/mapper, auth configuration/helper, onboarding, trading code, financial DTO, frontend, seed, nginx or Compose definition was edited in this backend follow-up. No files were staged/committed by the agent; pre-existing untracked documentation was retained.

### Requirement coverage and actual test results

| Feature | Current state | Source / acceptance evidence |
| --- | --- | --- |
| Assigned client pagination/search, safe reference, real timestamps | implemented | `AdvisorWorkspaceMapper.clients/clientCount`, service query validation; controller/service/PostgreSQL tests and live nginx list |
| Selected client exact current portfolio | implemented | `positions`: posted/reserved/posted-minus-reserved string decimals, including quarantined owned units; PostgreSQL fractional fully-reserved test |
| Advisor identity and object scope | implemented on new workspace APIs | verified user -> enabled persisted ADVISOR/advisor -> SQL assignment predicates; tests A vs B, guessed record IDs, missing link/wrong role and live demo.client2 404 |
| HOLD/SELL/NONE guidance, clear and history | implemented | client-first lock, expected-version CAS, retained logical NONE record; PostgreSQL clear/conflict/concurrent first-write tests and immutable snapshots |
| Not-held recommendation CRUD/archive | implemented | exact posted > 0, active uniqueness, bound 1–2000 trimmed, repeated version check, append-only history; PostgreSQL acquisition/trading-lock race/archive tests and live nginx CRUD |
| Client assignment/advisory | implemented | session-owned user queries, no browser client ID trust; controller/PostgreSQL tests, real client API reads |
| Automatic assignment and assignment timestamps | deferred policy/shared seam | no production allocation inferred; existing demo mapping is preserved, unknown assignedAt null |
| Fresh/upgrade parity and grants | implemented | migration013/fresh matching functions/indexes/constraints/triggers; parity test, non-owner runtime projection/history permission test; hardened owner must reapply grant script |
| Docker/nginx/browser connection | verified | normal migration runner, only backend rebuilt/recreated, unchanged nginx paths; real Advisor list/detail/reload/add/archive and client API visibility |

TDD evidence:

- New runnable MVC route test failed HTTP 404 vs expected 200 before the API existed.
- New real PostgreSQL test failed expected `advisor_guidance`, actual null before schema implementation. A preliminary JUnit fixture compilation error was corrected and is not claimed as a behavioral red phase.
- Strict numeric JSON test failed HTTP 404 vs 400 because Jackson silently coerced 43.5 into 43; role-local integer parsers now reject floats/strings/booleans before database access.
- A runtime permission test initially used an invalid generated-identity INSERT and saw 428C9 instead of 42501; the test was corrected to a valid INSERT shape so it actually verifies permission denial. This was a test correction, not a production privilege relaxation.

Final execution:

- Full backend `mvn -f backend/pom.xml test -q`: **442 tests, 71 suites, 0 failures, 0 errors, 0 skips**. First full pass had the single privilege-test-shape failure above; final full rerun is green. Existing self-directed trading/PostgreSQL regressions passed without trading code changes.
- New Advisor tests within that total: **42** (11 controller/security, 17 service, 4 MyBatis scope/parsing, 10 PostgreSQL integration).
- `mvn -f testing/pom.xml -Dtest=FreshTradingSchemaTest test -q`: **passed** fresh/upgrade parity including 013 and repeated reference bookkeeping.
- `python3 scripts/test-advisor-seed.py`: **passed**, preserving seeded credentials/balances/assignments on rerun and rejecting conflicting demo.client assignment.
- Focused frontend contract/role/component includes: **90 passed across 10 files**. No frontend source changes in this follow-up. The earlier unrelated full-frontend analytics assertion/host build-output permission issues were not retested or fixed here.
- `docker-compose config --quiet`: passed. `database-migrate` normal service applied 013, then cleanly skipped it on rerun. `docker-compose build backend` passed; `up -d --no-deps backend` replaced only the backend container.
- Live `scripts/smoke-advisor-workspace.py --verify-demo-mutations`: passed real public-demo login, new API reads, anonymous/wrong-role/negative-client checks, invalid integer rejection, create/PATCH/stale-version/DELETE transport and archival through nginx. Tokens stayed in process memory. Archived smoke-test history is intentionally retained.
- Real integrated browser through localhost:8087: demo.advisor login -> Demo Client list -> detail -> reload; approved catalog and genuine empty holdings shown. Created an explicitly labelled browser-verification recommendation, independently observed it via demo.client's owned advisory API, then archived it using frontend confirmation. The active table is empty again; history remains. No synthetic financial holdings were inserted into the live stack. Live HOLD/SELL controls therefore were not demonstrated on populated holdings; PostgreSQL/fixture tests cover them.
- Checked Java/Python editor diagnostics: no errors. `git diff --check`: passed.

### Deployment, history and remaining handoff

Runtime owner must apply/reapply `../../../database/runtime-grants.sql` in hardened deployments before using the new APIs. Local Compose uses its existing database identity; no credentials/grants were changed live by ad hoc SQL. Source history is never physically deleted; no client/advisor history-browsing endpoint is exposed. Rollback should disable/revert the API release while retaining new tables/history and existing assignments; do not remove volumes or drop evidence. Any future schema change uses a new coordinated migration (next candidate 014).

Automatic assignment remains a separate product/integrator decision. Legacy advisor-ID and trade-suggestion routes are unchanged: the new workspace's assignment guarantees must not be claimed for those older APIs, and broader legacy hardening remains a production security review item. No new rate limiter was introduced. Frontend catalog is bounded to its existing first page.

Exact contract/architecture: `AdvisorRoleBackendImplementation.MD`. Frontend/live walkthrough: `AdvisorRoleFrontend_CrashCourse.MD`. Exception decisions/remaining policy: `SharedChangeProposal/README.MD`. The sections below retain the plan and earlier frontend/seed recovery history and are superseded by this current result where statuses differ.

## Backend implementation plan — authorized follow-up (2026-10-22)

The current request explicitly authorizes beginning the **Advisor backend** (confirmed after the initial “analyst” wording), connecting the already implemented frontend. This section supersedes the earlier backend-pending snapshot only where implementation and verification below establish completion.

### Project analysis and connection to Advisor requirements

This is a role-isolated portfolio/trading application: Angular standalone role workspaces call Spring thin controllers, service interfaces/transactional implementations and MyBatis/PostgreSQL; analytics is a separate reporting service. Authentication verifies JWT identity against live persisted user/session/roles. Financial correctness is enforced by exact-decimal current ledger projections, reservations, client-row locking and durable journals. Advisor guidance is a separate opinion/history feature, not another way to place trades or mutate financial state.

The frontend already calls the exact Advisor workspace routes in the backend handoff. The missing connection is Spring/data persistence, not Angular or nginx registration. Existing legacy advisor-ID and trade-suggestion APIs do not provide safe assigned-client scope or compatible HOLD/NONE semantics. Current holdings must use client_positions.posted/reserved, not dated client_holdings snapshots or the placeholder workspace-read portfolio. The seeded demo mapping is already present and must remain the only demo mapping.

### Bounded implementation sequence

1. Add role-owned Advisor DTOs, mapper, service/interface and separate Advisor/client-read controllers. Reuse verified identity resolution, existing error envelopes and current architectural conventions. No global security-helper or trading-service refactor.
2. Write new behavioral service/controller tests first, run meaningful failures, then implement all existing frontend request shapes: paginated/searchable assigned clients, identity-safe current portfolio, versioned guidance with NONE tombstones, recommendation create/edit/archive, authorized bounded catalog, owned client assignment/advisory reads.
3. Add migration 013 for dedicated guidance/recommendation projections and append-only attributed history. Lock the assigned client before mutation, matching existing trading lock order; use exact posted > 0 eligibility and version compare-and-set. SQL assignment predicates and session-derived advisor identity apply to all operations, including guessed recommendation IDs. History will be written by projection triggers in the same transaction, with immutable evidence guards.
4. Preserve assignedAt as null for historical assignments whose timestamp is unknown; do not fabricate metadata. No new assignment policy is inferred from the demo mapping.
5. Add the exact schema to the fresh snapshot, marker 13 to fresh reference and targeted runtime grants. Exercise real PostgreSQL fresh/upgrade parity, history/constraints and scope/eligibility tests. These are necessary additive shared integration seams, not permission to rewrite existing migrations.
6. Run backend unit/controller/mapper/PostgreSQL tests and existing self-directed trading regressions, then focused frontend specs. Apply through normal migration tooling, build/restart only the backend as necessary, and verify API positive/negative and JSON DELETE transport through nginx. Do not log JWTs/credentials or delete retained volumes.

### Shared-file exceptions, reasons and tradeoffs

- **Fresh snapshot/reference/runtime grants:** necessary to make new Advisor storage deployable on both fresh and retained databases. Pros: additive, repeatable, least-privilege deployment. Cons: shared-file merge seams and migration number coordination; keep edits append-only/narrow and allocate 013 only after checking current chain.
- **Existing onboarding/assignment:** deliberately deferred pending an approved production allocation/reassignment policy. A narrow transactional assignment-service hook is preferred (atomic and idempotent) but touches a contributor-owned onboarding seam; durable outbox alternative avoids direct coupling but adds eventual consistency/workers/races. Do not silently assign all real clients or demo.client2. Existing seed assignment is sufficient to connect and exercise this initial backend slice.
- **No nginx/Compose/frontend routing changes expected:** /api already proxies Spring and /advisor already reaches Angular. Reuse those routes rather than adding a new service/proxy or browser-side hostnames.
- **No legacy Advisor/security helper hardening in this slice:** new session-scoped routes do not use the legacy bypass. Wider legacy route policy changes require a separately approved caller impact analysis.

Detailed broader assignment exceptions remain in SharedChangeProposal/README.MD. Initial backend slice excludes automatic assignment and historical assignment backfill; these remain explicit completion gates, not hidden frontend behavior.

## Follow-up: demo seed authorized and applied (2026-10-22)

The user separately authorized adding the demo Advisor seed after the frontend recovery. This supersedes the historical credential-pending statements below **only for demo provisioning**, not the backend workspace APIs or automatic assignment policy.

- Updated `../../../database/demo/role_workspaces.sql`: `demo.advisor`, neutral `Demo Advisor`, ADVISOR role and linked advisor row. New user password is hashed using the existing public `RoleDemo2026!` convention; reruns preserve existing passwords and balances.
- Only unassigned `demo.client` is linked to this advisor. A conflicting pre-existing assignment aborts the transaction clearly; `demo.client2` and unrelated clients remain untouched.
- Added `../../../scripts/test-advisor-seed.py`: disposable PostgreSQL tests using the real fresh schema; red phase failed with “Advisor demo identity or assignment missing”, then passed creation/role/link, scoped assignment, repeatability, preserved password/cash and conflicting-assignment rejection. No retained volume is used by these tests.
- Applied successfully through the existing Compose demo-seed service using its checked-in SQL, not ad hoc provisioning. No Compose, Spring or nginx edits.
- Live `/auth/login` and `/api/users/me` verified `demo.advisor` and ADVISOR through nginx on port 8087. Tokens were not logged. `/advisor/clients` returns HTTP 200 with Host localhost; Host 127.0.0.1 is rejected with HTTP 400 by the existing SSR host policy. Use `http://localhost:8087` through the VM's forwarded port, or an already approved hostname.
- `docker-compose config --quiet` and `git diff --check` passed. This VM has standalone `docker-compose`, not the `docker compose` plugin.
- Assigned-client workspace data, guidance/recommendation persistence and automatic assignment remain unimplemented backend prerequisites. Login can expose the frontend unavailable state; it does not enable a populated live Advisor workflow.

## Scope decision

This implementation follows the requested merge-safe boundary:

- Implemented: Angular Advisor role recognition, routes, assigned-client list, client detail, holding guidance UI, recommendation UI, client read-only advisory presentation, profile assignment presentation, role-owned API contracts, focused tests, and handoff documentation.
- Preserved: existing authenticated shell, client financial DTOs/calculations, order ticket, polling infrastructure, operations/support behavior, global styles, nginx topology, and legacy trade-suggestion meanings.
- Pending backend prerequisite: session-scoped Spring APIs, advisory persistence/history, automatic assignment, demo Advisor provisioning, and server authorization hardening.

The browser can preview the feature with injected HTTP fixtures in tests. It cannot provide a live `demo.advisor` login or persistence until the backend prerequisite is integrated.

## Repository findings

- `../../../frontend/src/app/core/auth/workspace-policy.ts` was the controlling frontend role boundary. It recognized no `ADVISOR`, so Advisor roles were discarded and Advisor routes were denied.
- `../../../frontend/src/app/app.routes.ts` contains lazy routes below `AuthenticatedShellComponent`. Advisor routes now reuse that shell.
- `../../../frontend/src/app/features/client/account-page.component.ts` is the real `/app/overview` surface. Its portfolio, valuation, and orders resources remain independent from the new advisory resource.
- `../../../frontend/src/app/profile.ts` used cached profile completion to skip profile loading. Assignment is now a separate request and does not depend on that cache.
- Existing Spring `AdvisorController` and `TradeSuggestionController` are not the new workspace API. They accept path IDs and/or preserve BUY/SELL quantity semantics.
- `SecurityRoleSupport.requireClientOwnership()` allows non-client Advisor access and is not used as proof of assigned-client scope.
- `../../../nginx/nginx.conf` already sends `/api/` to `backend:8080` and all other paths to `frontend:4000`. No Advisor-specific nginx location is needed.
- `../../../docker-compose.yml` has no `demo.advisor`; live Docker walkthrough remains blocked until seed/provisioning work is coordinated.

## Requirement matrix

| Requirement | State | Source symbol/files | Acceptance test or evidence |
|---|---|---|---|
| Recognize ADVISOR | Implemented frontend | `WORKSPACES`, `normalizeRoles()` | `workspace-policy.spec.ts`: role normalizes and lands at `/advisor/clients` |
| Advisor home and detail routes | Implemented frontend | `routeRole()`, `routes` | Positive integer routes pass; zero, negative, decimal and text IDs are denied |
| Existing role priority | Preserved | `WORKSPACES` ordering | Existing 30 policy tests pass, including mixed-role behavior |
| Assigned client list | Implemented UI and proposed adapter | `AdvisorClientsPageComponent`, `AdvisorApi.clients()` | Populated and successful-empty list tests pass; 401/403/503 remain errors, not empty |
| Client detail | Implemented UI and proposed adapter | `AdvisorClientDetailPageComponent` | Independent portfolio/guidance/recommendation/catalog requests and holdings retention test pass |
| HOLD/SELL/N/A guidance | Implemented UI and proposed adapter | `GuidanceStatus`, `saveGuidance()` | Guidance status, rationale, expected version, unavailable state and failed draft preservation tested |
| Not-held recommendations | Implemented UI and proposed adapter | recommendation methods in `AdvisorApi` and detail page | Validation, duplicate/eligibility logic and archive flow are implemented; server enforcement remains pending |
| Client Advisor column | Implemented frontend | `AccountPageComponent.guidanceStatus()` | Advisory 503 does not suppress financial panels; account suite passes |
| Client recommendations table | Implemented frontend | `ClientRecommendationsComponent` | Read-only table, empty state, UTC as-of and retry state are present |
| Assigned advisor profile | Implemented frontend | `ProfileComponent.assignment` | Assigned, pending and unavailable states pass in `profile.spec.ts` |
| Server identity/object ownership | Backend prerequisite | Proposed workspace services/controllers | Must test Advisor A vs B, guessed IDs, and client session ownership before live integration |
| Persistence/history/versioning | Backend prerequisite | New migration and dedicated tables | Must provide active uniqueness, append-only history, archive and optimistic versions |
| Automatic assignment | Backend prerequisite | `ClientOnboardingServiceImpl` candidate seam | Must be transactional/idempotent and preserve existing assignment |
| `demo.advisor` login | Backend prerequisite | `../../../database/demo/role_workspaces.sql` or approved overlay | Must map only `demo.client`; `demo.client2` remains negative fixture |
| Docker/nginx routing | Existing infrastructure | `../../../nginx/nginx.conf`, Compose | No routing edit expected; verify through nginx after backend is available |
| Analytics and other roles | Deferred | Separate analytics/service areas | No changes made |

## Implemented frontend files

- `../../../frontend/src/app/core/auth/workspace-policy.ts`
- `../../../frontend/src/app/core/auth/workspace-policy.spec.ts`
- `../../../frontend/src/app/app.routes.ts`
- `../../../frontend/src/app/features/advisor/advisor-models.ts`
- `../../../frontend/src/app/features/advisor/advisor-api.service.ts`
- `../../../frontend/src/app/features/advisor/advisor-api.service.spec.ts`
- `../../../frontend/src/app/features/advisor/advisor-utils.ts`
- `../../../frontend/src/app/features/advisor/advisor-utils.spec.ts`
- `../../../frontend/src/app/features/advisor/advisor-clients-page.component.ts`
- `../../../frontend/src/app/features/advisor/advisor-clients-page.component.spec.ts`
- `../../../frontend/src/app/features/advisor/advisor-client-detail-page.component.ts`
- `../../../frontend/src/app/features/advisor/advisor-client-detail-page.component.spec.ts`
- `../../../frontend/src/app/features/client/advisory/client-advisory-models.ts`
- `../../../frontend/src/app/features/client/advisory/client-advisory-api.service.ts`
- `../../../frontend/src/app/features/client/advisory/client-advisory-api.service.spec.ts`
- `../../../frontend/src/app/features/client/advisory/client-recommendations.component.ts`
- `../../../frontend/src/app/features/client/account-page.component.ts`
- `../../../frontend/src/app/features/client/account-page.component.spec.ts`
- `../../../frontend/src/app/profile.ts`
- `../../../frontend/src/app/profile.spec.ts`

## Proposed API contract

The following paths are used by the role-owned frontend adapter but remain proposed until the backend handoff freezes the JSON and status behavior:

```text
GET    /api/workspace/advisor/clients
GET    /api/workspace/advisor/clients/{clientId}/portfolio
GET    /api/workspace/advisor/clients/{clientId}/guidance
PUT    /api/workspace/advisor/clients/{clientId}/guidance/{instrumentId}
GET    /api/workspace/advisor/clients/{clientId}/recommendations
POST   /api/workspace/advisor/clients/{clientId}/recommendations
PATCH  /api/workspace/advisor/clients/{clientId}/recommendations/{recommendationId}
DELETE /api/workspace/advisor/clients/{clientId}/recommendations/{recommendationId}
GET    /api/workspace/advisor/instruments
GET    /api/workspace/client/advisor
GET    /api/workspace/client/advisory
```

The adapter uses:

- zero-based client pagination;
- positive integer path IDs;
- decimal values as strings;
- `status: NONE | HOLD | SELL` for guidance;
- `reasoning` trimmed to 1-2000 characters;
- `expectedVersion` for optimistic updates;
- normalized `message` handling for Spring `message` and FastAPI `detail` responses;
- no fallback fixture data in normal operation.

The frontend proposal selects a JSON DELETE body `{ "expectedVersion": 3 }` and expects a 200 `{ "recommendationId": 9, "version": 4 }`. Complete candidate JSON, nullable fields, versioned NONE tombstones, 409/429 behavior and non-disclosing 404 policy are now documented in `AdvisorRoleBackendImplementation.MD`. Backend approval and implementation remain pending; this is a candidate wire freeze, not evidence of a running API.

## Backend exception decisions

### Automatic assignment

The existing onboarding method creates or updates a client but does not assign an advisor. A narrow call to a new assignment service is the preferred exception.

Pros:

- assignment can be transactional with successful onboarding;
- retries can preserve an existing assignment;
- no extra worker or event infrastructure is required.

Cons:

- `ClientOnboardingServiceImpl` is a shared merge seam;
- eligibility, tie-breaking, concurrency, reassignment and disabled-advisor rules need explicit policy;
- onboarding tests gain assignment dependencies.

An asynchronous durable assignment job is an alternative, but it adds eventual consistency, pending-state duration, retry infrastructure and more race cases. It should not be selected only to avoid a small merge conflict.

### Demo provisioning

The current seed creates six demo accounts and no Advisor. Additive seed work is required for `demo.advisor`, role `ADVISOR`, a linked `advisors` row and only the `demo.client` assignment. Resolve IDs by username, hash `RoleDemo2026!`, preserve all existing passwords/balances/assignments, and keep `demo.client2` unassigned.

Do not overwrite a contributor-owned seed or Compose mount without coordination. If the seed path cannot be changed safely, create `../../../SharedChangeProposal/README.MD` before editing it.

## Docker and nginx plan

No nginx change is planned. The existing routing is sufficient:

- `/advisor/clients` and `/advisor/clients/{id}` go to Angular through `location /`.
- `/api/workspace/advisor/...` and `/api/workspace/client/advisory` go to Spring through `location /api/`.
- browser requests remain relative and use the existing bearer interceptor.

After backend integration:

```text
docker compose config --quiet
docker compose up --build -d
GET /health
POST /auth/login as demo.advisor
GET /api/users/me
GET /api/workspace/advisor/clients
GET /api/workspace/advisor/clients/{demo.client-id}/portfolio
GET /api/workspace/advisor/clients/{demo.client2-id}/portfolio -> deny
```

Verify deep links through the nginx public port. Do not delete retained volumes, overwrite `.env`, or add browser-side container hostnames.

## VM recovery assessment and continuation (2026-10-22)

### Recovered working tree

`git status --short` initially showed six modified tracked frontend files: routes, workspace policy/spec, account page/spec and profile. Untracked files were this plan, the instruction, both handoffs, the Advisor feature directory, client advisory directory and profile spec. There were no backend, SQL, Compose, nginx or environment changes. No applicable AGENTS.md or repository instruction file was found by the targeted instruction inventory. All recovered changes were preserved; nothing was staged, committed, reset or provisioned.

The prior implementation had the main surfaces but only 14 passing Advisor tests. Its recorded earlier results were treated as historical notes, not proof of current behavior. Direct source review confirmed the existing Spring advisor-ID APIs were not session-scoped workspace APIs, the trade suggestion schema was incompatible with HOLD/NONE, onboarding had no assignment call, and the seed had no demo Advisor. Existing nginx routing remains sufficient.

### Recovery implementation plan and completed work

1. **Existing:** keep Angular standalone/lazy routes, role priority, shared shell, PageResource and separate financial resources. Audit only the assignment files and immediate consumers.
2. **Missing frontend, now implemented:** add behavioral regressions before fixes for draft/session isolation, permission loss, client-switch cancellation, unsafe IDs, fail-closed eligibility, repeated saves, metadata, acquired instruments and archive focus. Capture actual red assertions, then patch only role-owned files and the allowed account seam.
3. **Missing frontend, now implemented:** preserve drafts through failures/conflicts, explicitly rebase against the refreshed version, use confirmed save results, lock in-flight editors, provide independent guidance/catalog retry, and distinguish NONE/N/A from unavailable.
4. **Existing/proposed contract:** keep the session-scoped adapter without a fixture fallback; specify exact JSON, decimal strings, zero-based pages, DELETE body and clear/tombstone semantics in the backend handoff. No legacy endpoint substitution.
5. **Backend prerequisite/policy decision:** leave persistence, server ownership, assignment, eligible-advisor policy and seed provisioning to the backend owner. Shared onboarding/seed exceptions are analyzed in `../../../SharedChangeProposal/README.MD`; no exception is implemented here.
6. **Verification:** run Advisor specs first, client/profile/policy specs second, then full tests/style/build once. Record unrelated or environment failures rather than widening scope.

### Additional and revised files on recovery

Revised Advisor detail implementation/spec, Advisor API implementation/spec, client list implementation/spec, advisory model comments, client recommendations component, client account component/spec, and all three handoffs/plan. Added:

- `../../../frontend/src/app/features/advisor/advisor-routing.spec.ts`
- `../../../frontend/src/app/features/client/advisory/client-recommendations.component.spec.ts`
- `../../../SharedChangeProposal/README.MD`

No backend/source schema, shared auth/session/resource, global style, shell, financial calculation, polling, seed or nginx edits were made. Existing role registration patches remain preserved.

### Final requirement/test coverage

| Requirement | Recovery state | Source symbol | Actual acceptance evidence |
| --- | --- | --- | --- |
| Advisor role, home, direct links, role denial | existing, verified | `WORKSPACES`, `workspaceGuard`, actual lazy `routes` | 30 policy tests plus 7 Advisor route integration tests; valid/invalid deep links, denied client role, logout/relogin |
| Client list populated/empty/error/search | existing plus completed frontend | `AdvisorClientsPageComponent` | 10 tests, including 401/403/404/429/503/offline, obsolete search cancellation and session-cleared search |
| Adapter requests/errors/IDs | existing plus completed frontend | `AdvisorApi` | 11 tests; expectedVersion request, safe positive integer validation, message/detail mapping and distinct status defaults |
| Eligibility/rationale | existing, verified and strengthened | `hasPositiveDecimal`, `validReasoning`, `eligibleInstruments`, `canRecommend` | 7 utility tests plus detail regressions; fully reserved fractional units excluded, zero eligible, missing prerequisites fail closed, submit rechecks |
| Guidance and recommendation interactions | completed frontend | `AdvisorClientDetailPageComponent` | 15 tests; save failure, duplicate prevention, conflict/rebase/NONE clear, CRUD, confirmed versions, acquisition filtering, permission/session cleanup, old request cancellation, archive focus/Escape/restoration |
| Client advisory isolation/display | completed frontend | `guidanceStatus`, `ClientRecommendationsComponent` | 6 account tests, 1 read-only presentation test, 1 client adapter test; explicit NONE => N/A, escaped rationale, acquired positions filtered, outage does not suppress financial panels |
| Profile assignment | existing, verified | `ProfileComponent.assignment` | 2 tests; assignment refresh despite cached profile, assigned/pending/unavailable |
| Backend identity/persistence/history | backend prerequisite | proposed workspace services/mappers | not implemented or tested in this frontend recovery |
| Automatic assignment/allocation | backend prerequisite and policy decision | `ClientOnboardingServiceImpl.completeOnboarding` | proposal/handoff only; existing assignments preserved by future policy |
| Demo Advisor credential | backend prerequisite | optional coordinated seed | not provisioned; `demo.client2` remains negative fixture |
| Unrelated analytics, other roles and multi-market execution | deferred | unrelated feature areas | no edits; unrelated analytics test failure reported below |

## Actual final validation

Environment: Node v24.22.0, npm 11.19.1. `npm test -- --help` confirmed Angular supports repeated `--include` selectors and `--watch=false`.

- Recovered baseline: Advisor **14/14 passed**, 4 files.
- TDD red evidence: missing author rendered, mutation not cancelled on session change, protected client remained after 403, duplicate saves, fail-open eligibility and missing archive focus produced assertion failures. Later client regressions specifically showed `NONE` instead of N/A and an acquired recommendation still rendered. A fixture type mismatch was corrected before treating the client run as a behavioral red phase. Follow-up safety tests failed for unsafe IDs, uncleared search and generic HTTP defaults, then passed after scoped fixes.
- Final Advisor command: `npm test -- --watch=false --include='src/app/features/advisor/**/*.spec.ts'`: **50/50 passed, 5 files**.
- Final client/advisory/profile/policy command (repeated includes for those specs plus account): **40/40 passed, 5 files**. Combined focused slice: **90 passing tests**.
- Full `npm test -- --watch=false`, run once after final changes: **243 passed, 1 failed, 244 total; 38 files passed, 1 failed**. Only failure: unchanged `../../../frontend/src/app/features/analytics/analytics-page.component.spec.ts`, test “keeps filters visible and labels an unavailable report without invented values”. It asserts the whole text excludes `0`, but the unchanged page includes “Indexed to 100”. Analytics implementation/spec have no working-tree diff. No out-of-scope fix was made.
- `npm run test:qof-styles`, run once: **passed**.
- `npm run build`, run once: browser/server compilation and bundle generation completed, then artifact cleanup failed **EACCES unlink frontend/dist/pole/3rdpartylicenses.txt**. `../../../frontend/dist`, `frontend/dist/pole` and the file are root-owned (directories 755; file 644), while the agent is uid 1000 `ec2-user`. Terminal reported build exit 127. The build also warned about the untouched analytics component's 4.37 kB stylesheet exceeding a 4 kB budget by 372 bytes. This is **not a successful production artifact build**. Output ownership must be corrected by the VM owner before rerunning; no sudo, deletion or ownership changes were attempted.
- `git diff --check`: **passed**. Editor diagnostics: no errors in checked touched account/profile files; focused compilation validates the role-owned files.

Not run: backend tests/migrations, live Docker/nginx Advisor login, live persistence or automatic assignment. No live credential or secure workspace API exists yet. Component/router HTTP fixtures are a test-harness preview only; no visual browser walkthrough or interactive login was claimed.

## Exact handoff and remaining gates

- Backend standalone task and complete proposed DTO/API contract: `AdvisorRoleBackendImplementation.MD`.
- Frontend architecture, fixtures, walkthrough and limitations: `AdvisorRoleFrontend_CrashCourse.MD`.
- Shared onboarding/provisioning impact analysis and approval gate: `../../../SharedChangeProposal/README.MD`.
- VM owner: correct root-owned build output permissions, then rerun the normal production build.
- Analytics owner: narrow the unrelated zero-value assertion to report values rather than static explanatory copy.
- Backend owner/integrator: approve wire semantics and allocation policy, implement session-scoped ownership and persistence/history, coordinate migration/grants and optional seed, provision only `demo.client` for `demo.advisor`, then run the Docker/nginx positive/negative walkthrough.

Frontend surfaces and focused behavior are implemented/tested. Whole-repository verification is blocked by the reported analytics test and build-output permissions. Demo credential, automatic assignment, backend ownership enforcement and persistence are **pending separately**; the Advisor workflow is not end-to-end complete.
