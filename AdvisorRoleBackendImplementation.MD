# Advisor Role Backend Implementation Handoff

Date: 2026-10-22
Status: initial Advisor backend slice implemented/deployed; automatic onboarding assignment remains deferred

Follow-up 2026-10-22: the user separately authorized demo seed provisioning. `database/demo/role_workspaces.sql` now creates the hashed `demo.advisor` identity, ADVISOR role and linked advisor, assigns only unassigned `demo.client` and rejects a conflicting assignment. The checked-in seed was applied and login/role verified through nginx; disposable PostgreSQL tests pass in `scripts/test-advisor-seed.py`. Demo credential provisioning is therefore complete for this stack. The APIs, persistence/history, server ownership enforcement and production automatic assignment described below remain pending; no Spring implementation was added.

This is a standalone TDD task for the Spring/PostgreSQL owner. It is intentionally separate from the Angular implementation to minimize merge conflicts and to prevent the frontend from claiming authorization or persistence that does not exist.

## Current implementation and verification (backend follow-up)

The user confirmed **Advisor backend**, explicitly authorizing the role-owned APIs and necessary additive SQL integration. All eleven routes below now exist in Spring and match the frontend adapters. Earlier “proposed/pending” sections describe the original handoff; this current section and the implemented wire-contract section supersede them for this initial slice.

Production sources:

- `backend/src/main/java/com/neueda/leap/controller/AdvisorWorkspaceController.java`
- `backend/src/main/java/com/neueda/leap/controller/ClientAdvisoryController.java`
- `backend/src/main/java/com/neueda/leap/controller/AdvisorWorkspaceExceptionHandler.java`
- `backend/src/main/java/com/neueda/leap/dto/AdvisorWorkspaceDtos.java`
- `backend/src/main/java/com/neueda/leap/mapper/AdvisorWorkspaceMapper.java`
- `backend/src/main/java/com/neueda/leap/service/AdvisorWorkspaceService.java`
- `backend/src/main/java/com/neueda/leap/service/impl/AdvisorWorkspaceServiceImpl.java`
- `database/migrations/018_advisor_workspace.sql`, with matching fresh DDL/reference marker and targeted runtime-grant updates.

Identity derives from verified JWT -> persisted user -> advisor link and ADVISOR role. Every mapper scope rechecks enabled/role/assignment, and client-facing reads derive ownership from user identity rather than any requested client ID. No legacy ownership bypass, global auth refactor, widened CLIENT catalog guard or financial-service dependency was added. Grouped reads use repeatable read; writes lock the scoped client first, then recheck exact posted ownership and compare-and-set versions. Projection triggers atomically append attributed immutable history; runtime cannot insert or alter history directly. Acquisition filters active reads, not stored records/history; archive is allowed even after acquisition/deactivation.

The strict JSON integer parsers are local to these DTOs. Advisor-local exception adaptation returns safe message envelopes for malformed requests, integrity conflict and temporarily unavailable storage, without changing other roles' errors. Existing generic `HTTP_409` error codes remain; a new `ADVISORY_CONFLICT` code and rate limiter were not introduced.

**Actual checks:** 442 backend tests passed across 71 suites (including the existing self-directed trading suite), with no failures/errors/skips. New Advisor suite is 42 tests: controller/security 11, service 17, mapper parsing/scope 4, real PostgreSQL 10. FreshTradingSchemaTest passed fresh-versus-upgrade catalog parity through 013. Demo-seed isolated PostgreSQL tests passed. Frontend contract/role/component slice: 90/90 passed, unchanged frontend files. The normal Docker backend build passed and only the backend container was rebuilt/recreated; frontend/nginx/Compose definitions were not changed.

**Live verification:** migration 013 applied via the normal locked Compose runner and cleanly skipped on rerun. `scripts/smoke-advisor-workspace.py --verify-demo-mutations` passed real nginx login, assigned list/portfolio/guidance/recommendations/catalog, owned assignment/advisory, anonymous/wrong-role/unassigned-client negatives, strict integer validation, create/edit/stale-version/archive and JSON DELETE response. Tokens were kept in process memory. That test recommendation was archived with history retained.

Browser verification through `http://localhost:8087`: real demo.advisor login, populated assigned-client list, detail and reload, genuine empty holdings, real catalog, recommendation add and archive confirmation succeeded. The browser-created recommendation was also observed via demo.client's owned advisory API before archival. No mock data or fabricated position was used. demo.client currently has no live holdings, so live HOLD/SELL editing was not demonstrated; populated guidance and exact fractional/reserved behavior are verified in PostgreSQL tests. Existing bridge/browser tooling emitted some ERR_ABORTED events while forwarding, but the page responses and independently verified APIs completed successfully.

**Remaining:** automatic onboarding assignment, deterministic production allocation, assignment timestamp/history backfill and reassignment policy are not implemented. `assignedAt` is intentionally null for the existing schema. There is no history-view endpoint or new rate limiter. Legacy advisor-ID APIs are unchanged and do not inherit these new routes' guarantees. The frontend selector still shows its existing first catalog page of at most 100 items. Hardened runtime grants must be reapplied by the owner; local Compose uses its existing identity. No destructive rollback is provided.

## Existing APIs versus proposed workspace APIs

### Existing and not equivalent

The repository currently has:

- `GET /api/advisors`
- `POST /api/advisors`
- `GET /api/advisors/{advisorId}`
- `PATCH /api/advisors/{advisorId}`
- `GET /api/advisors/{advisorId}/clients`
- `POST /api/advisors/{advisorId}/clients/{clientId}/trade-suggestions`
- `GET /api/clients/{clientId}/trade-suggestions`
- `GET/PATCH /api/trade-suggestions/{suggestionId}`

These are not the requested workspace contract. The existing Advisor paths accept browser-supplied advisor IDs. Existing trade suggestions require `BUY` or `SELL`, positive quantity and client decision statuses such as `SUGGESTED` and `ACCEPTED`. Do not change those meanings to implement HOLD/NONE guidance.

`SecurityRoleSupport.requireClientOwnership()` permits a non-client Advisor through its client ownership helper. Role annotations alone therefore do not establish assigned-client authorization.

### Proposed session-scoped contract

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

Freeze the final JSON examples with the frontend owner before implementation. The frontend currently expects:

```json
{
  "items": [],
  "page": 0,
  "pageSize": 20,
  "total": 0
}
```

Portfolio positions must contain stable `instrumentId`, ticker, name, currency, exact decimal strings for owned/reserved/available, and ISO-8601 UTC timestamps for updated-at values. Guidance uses `NONE`, `HOLD` or `SELL`, nullable reasoning, author, version and updated-at. Recommendations contain stable recommendation ID, instrument ID, ticker, instrument name, reasoning, version and updated-at.

The guidance write body is:

```json
{
  "status": "SELL",
  "reasoning": "Review concentration.",
  "expectedVersion": 3
}
```

Recommendation creation is:

```json
{
  "instrumentId": 42,
  "reasoning": "Review diversification."
}
```

Recommendation update is:

```json
{
  "reasoning": "Updated review rationale.",
  "expectedVersion": 3
}
```

The frontend proposal already selects a JSON DELETE body `{ "expectedVersion": 3 }`, with a 200 JSON response `{ "recommendationId": 9, "version": 4 }`. This is archive, not physical deletion. Confirm this transport through Spring and nginx with the backend owner; do not silently switch to `If-Match` or 204 without coordinating the adapter. Missing versions are invalid.

### Implemented wire contract (initial backend slice)

All paths above are implemented in the current Advisor workspace controllers. These examples and field rules match the frontend models and `AdvisorWorkspaceDtos`. IDs are positive Java/SQL integer values; expected versions are positive safe JSON integers (upper boundary is rejected before an increment could exceed safe precision). Fractional/string JSON IDs/versions are rejected, not coerced. Quantity values are exact base-10 strings, never JSON floats. All timestamps are server-generated ISO-8601 UTC strings. Arrays are always present, including successful empty responses. No envelope is nullable. Only fields marked nullable below may be null. The optional frontend client-list `assignmentStatus` is always emitted as ASSIGNED by this backend; other fields are required.

Client list `GET ?page=0&pageSize=20&search=Demo`: zero-based page, proposed bounds 1–100 for pageSize, search trimmed and at most 100 characters. Search and pagination remain assignment-scoped. Return total matching records, not global client counts.

```json
{"items":[{"clientId":7,"displayName":"Demo Client","clientReference":"CLIENT-7","assignmentStatus":"ASSIGNED","updatedAt":"2026-10-22T18:00:00Z"}],"page":0,"pageSize":20,"total":1}
```

Portfolio response:

```json
{"clientId":7,"clientName":"Demo Client","clientReference":"CLIENT-7","currentPositions":[{"instrumentId":42,"ticker":"TST","name":"Test Company","currency":"USD","owned":"0.125","reserved":"0.125","available":"0","updatedAt":"2026-10-22T18:00:00Z"}],"retrievedAt":"2026-10-22T18:00:00Z"}
```

Guidance GET response: `asOf` nullable if no source timestamp; record `reasoning` and `authorName` nullable. PUT returns a single confirmed record with this exact shape, not the GET envelope.

```json
{"items":[{"instrumentId":42,"status":"HOLD","reasoning":"Review concentration.","authorName":"Demo Advisor","version":3,"updatedAt":"2026-10-22T18:00:00Z"}],"asOf":"2026-10-22T18:00:00Z"}
```

Version/clear rules:

- `expectedVersion: null` is create-only when no logical guidance record exists, including no cleared tombstone. First write returns version 1. A concurrent first write returns 409.
- An update must provide the current positive integer version. Successful writes increment it once, atomically with history. Same-value writes follow the same compare-and-set rule; no unversioned upsert.
- PUT `NONE` clears active advice and reasoning without touching holdings. Preserve a versioned tombstone and append history. GET includes that logical record as `status: "NONE"`, `reasoning: null`, current author/version/timestamp. Do not omit a tombstone in a way that incorrectly makes the UI send a create-only null version.
- A first NONE write with null version creates a version-1 tombstone; repeated clears require its current version. This is a proposed explicit policy, not verified backend behavior.
- HOLD/SELL reasoning is optional/null; if present it must trim to 1–2000 characters. NONE always stores/returns null reasoning. Backend validates the bound even for a supplied NONE body before discarding its rationale.
- The frontend preserves failed drafts. A 409 refresh does not silently change their expected version; the advisor explicitly chooses “Use refreshed version with my draft” before another save.

Recommendations GET response: only `asOf` nullable. POST returns the single created item with version 1 and HTTP 201. PATCH returns the single confirmed updated item with incremented version and HTTP 200. Both use this item shape:

```json
{"items":[{"recommendationId":9,"instrumentId":43,"ticker":"NEW","instrumentName":"New Company","reasoning":"Review diversification.","version":3,"updatedAt":"2026-10-22T18:00:00Z"}],"asOf":"2026-10-22T18:00:00Z"}
```

POST has no expectedVersion; uniqueness and transactionally rechecked eligibility enforce creation. PATCH only edits reasoning, not instrument identity. DELETE uses the versioned JSON transport above. Active duplicate, changed ownership/eligibility, stale version or simultaneous archive returns 409. Archived history is not returned as an active recommendation. All writes require 1–2000 trimmed reasoning characters, except archive which has no reasoning input. The 2000-character limit is a **proposed contract choice**, to be approved and repeated server-side.

Catalog `GET ?page=0&pageSize=100&search=...`: same pagination bounds; no CLIENT-only catalog guard widening. Only expose approved safe catalog metadata:

```json
{"items":[{"instrumentId":43,"ticker":"NEW","instrumentName":"New Company","currency":"USD","isActive":true}],"page":0,"pageSize":100,"total":1}
```

Client assignment: `advisor` and `assignedAt` nullable; assigned advisor object has no optional properties. Pending is a successful response, not a simulated assignment:

```json
{"assignmentStatus":"ASSIGNED","advisor":{"advisorId":4,"name":"Demo Advisor"},"assignedAt":"2026-10-22T18:00:00Z"}
```

```json
{"assignmentStatus":"PENDING","advisor":null,"assignedAt":null}
```

The existing schema does not establish an assignment timestamp; add durable assignment metadata/history rather than fabricating `assignedAt` from the request time. Null is permitted where historical assignment time is unknown.

Client advisory: `asOf` and `source` nullable. Client guidance intentionally omits mutation `version`; client recommendation omits `version`. Both are read-only. Successful no-advice response is `guidance: []`, `recommendations: []`, not a missing/unavailable endpoint:

```json
{"guidance":[{"instrumentId":42,"status":"HOLD","reasoning":null,"authorName":"Demo Advisor","updatedAt":"2026-10-22T18:00:00Z"}],"recommendations":[{"recommendationId":9,"instrumentId":43,"ticker":"NEW","instrumentName":"New Company","reasoning":"Review diversification.","updatedAt":"2026-10-22T18:00:00Z"}],"asOf":"2026-10-22T18:00:00Z","source":"Committed advisory records"}
```

### Proposed status/error freeze

All errors use at least `{ "message": "Safe user-facing description" }`; optional stable `code` and safe `requestId` may be added. Do not disclose advisor/client ownership or prior advice in an error. Never use an empty success response for an outage.

| HTTP | Operation and behavior |
| --- | --- |
| 200 | Successful GET/PUT/PATCH/DELETE with the JSON specified above |
| 201 | Confirmed recommendation POST with one created item |
| 400 | Invalid IDs/query/status/reasoning/missing expectedVersion; no write |
| 401 | Missing/expired/invalid verified identity; protected state must clear |
| 403 | Wrong role or missing/disabled advisor link; no global fallback |
| 404 | Consistent non-disclosing response for unknown or unassigned client/instrument/recommendation; no identity distinction |
| 409 | Stale version, active duplicate or newly ineligible ownership; no write, refresh before explicit retry |
| 429 | Rate limited; include Retry-After, no automatic mutation retry |
| 503 | Missing/unavailable approved data source or unimplemented workspace functionality; not empty |
| network/offline | Browser status 0; preserve current draft, expose retry, never substitute fixtures |

Example conflict: `{ "message": "The advisory record or eligibility changed. Refresh before retrying.", "code": "ADVISORY_CONFLICT" }`. No existing draft should be overwritten from an error response. Optional structured validation errors may supplement `message`, not replace it with a FastAPI-only shape.

## Identity and authorization requirements

For every Advisor endpoint:

1. Require `ADVISOR` at the controller boundary.
2. Resolve the authenticated JWT to the application user using the same trusted identity resolution used elsewhere.
3. Resolve that user to `advisors.user_id` and obtain the advisor ID server-side.
4. For every client list, detail, portfolio, guidance and recommendation query, enforce `clients.advisor_id = authenticatedAdvisorId` in the SQL or in an equivalent transactionally enforced service query.
5. For recommendation and guidance mutations, enforce assignment in the same transaction as the write.
6. Never trust an advisor ID from the browser, body or URL for authorization.
7. Return a non-disclosing 404 or approved 403 for another advisor's client; use one consistent policy for guessed recommendation IDs.

For client endpoints:

1. Resolve the authenticated user to its own client ID.
2. Do not accept a browser-supplied client ID to establish ownership.
3. Return only that client's assignment, guidance and active recommendations.
4. Keep client recommendation access read-only.

Required negative tests:

- Advisor A cannot list or detail Advisor B's client.
- Advisor A cannot read, update or archive a recommendation belonging to Advisor B's client, even when the recommendation ID is guessed.
- `demo.client` cannot read another client's advisory data.
- A client cannot call Advisor mutation endpoints.
- A valid role with no linked advisor record cannot receive global Advisor data.

Do not reuse the legacy non-client ownership bypass as the implementation mechanism.

## Persistence design

Add dedicated guidance and recommendation tables. Do not extend `trade_suggestions` because that table's constraints and statuses describe trade proposals.

Minimum guidance storage:

- guidance ID;
- client ID FK;
- instrument ID FK;
- status enum/check: `HOLD` or `SELL` for active guidance, with true absence representing never-created NONE and versioned cleared tombstones representing prior guidance;
- rationale text with a 1-2000-character trimmed bound when supplied;
- author user/advisor FK;
- version;
- created and updated timestamps with timezone;
- active/archive state if required by the history design.

Minimum recommendation storage:

- recommendation ID;
- client ID FK;
- instrument ID FK;
- rationale text with a 1-2000-character trimmed bound;
- author user/advisor FK;
- version;
- active/archive state;
- created, updated and archived timestamps with timezone.

Enforce one active guidance per client/instrument and one active recommendation per client/instrument with a database uniqueness rule or partial unique index. Add stable foreign keys and indexes for authenticated advisor/client filters.

History must be append-only or otherwise preserve every change. An archive/delete operation must deactivate the active record and retain its history. Document who can access history and how client privacy is maintained.

## Financial and eligibility rules

Recommendation writes must not update cash, holdings, reservations, orders, fills, journals or execution jobs. They must not enqueue execution.

For create and update eligibility:

- reread committed current ownership inside the transaction;
- treat any exact positive decimal owned quantity as held;
- treat fractional quantities as ownership;
- do not use available-to-sell quantity as the ownership test;
- an owned-but-fully-reserved position remains held and is not eligible;
- a zero position remains eligible;
- reject or archive a recommendation if the client acquired the instrument, according to the agreed API behavior;
- preserve recommendation history after acquisition;
- do not automatically create HOLD or SELL guidance after acquisition.

The frontend helper is only a user-interface check. The backend must repeat the exact decimal-safe rule.

## Automatic assignment

`ClientOnboardingServiceImpl.completeOnboarding()` currently creates or updates a client without advisor assignment. Add a narrow assignment service call only after the client exists and within the onboarding transaction, or use a durable idempotent post-onboarding mechanism.

The policy must explicitly define:

- eligible advisor criteria;
- whether enabled user and role are required;
- deterministic tie-breaking;
- concurrency protection;
- retry behavior;
- preservation of an existing assignment;
- no eligible advisor => pending;
- disabled advisor behavior;
- reassignment policy;
- whether manual reassignment is allowed and audited.

The demo mapping is not a production allocation policy.

Preferred option: an `AdvisorAssignmentService` called from onboarding in the existing transaction. Alternative: an outbox/job after onboarding. The alternative reduces direct onboarding coupling but adds eventual consistency and another durable workflow.

Required tests:

- first successful onboarding assigns only when an eligible advisor exists;
- retry preserves the existing advisor;
- no eligible advisor leaves `advisor_id` null and reports pending;
- concurrent onboarding does not assign incorrectly;
- existing unrelated assignments remain unchanged.

## Demo seed and deployment

Add a repeatable optional seed for:

- username `demo.advisor`;
- display name `Demo Advisor`;
- email consistent with local seed conventions;
- role `ADVISOR`;
- linked `advisors.user_id` row;
- assignment of only `demo.client`;
- existing password convention `RoleDemo2026!` hashed through PostgreSQL `crypt`.

Resolve IDs by username. Do not replace existing passwords, balances or assignments. Keep `demo.client2` unassigned to the demo advisor. If a conflicting assignment already exists, fail clearly or preserve it according to the approved policy; do not silently overwrite it.

Coordinate any edits to `database/demo/role_workspaces.sql`, `database/demo/seed-role-workspaces.sh` or Compose mounts. Hardened deployments do not receive demo credentials automatically. New advisory tables require review in `database/runtime-grants.sql`.

## Migration and fresh/upgrade parity

Migration 018 is implemented for this slice; 019 is the next candidate. Future assignment work still needs a new approved numbered migration with:

- preconditions;
- transaction boundaries;
- schema migration marker;
- non-destructive behavior;
- indexes, constraints and grants;
- no edits to already-recorded migrations.

Update `database/fresh/001_schema.sql` and `database/fresh/002_reference.sql` to match the final schema. Add fresh-versus-upgrade tests and PostgreSQL tests for constraints, active uniqueness, version conflicts, archive history and decimal eligibility. Review runtime grants for all new tables and sequences.

Do not create an ad hoc browser or SQL workaround against a retained volume.

## Spring implementation shape

Use the existing architecture:

- thin workspace controllers;
- service interfaces and implementations for policy/transactions;
- DTO records for request/response contracts;
- dedicated MyBatis mappers with assignment predicates in SQL;
- central error mapping for validation, forbidden, not-found, conflict, unavailable and rate-limit responses.

Likely new role-owned files should be kept separate from the legacy files:

```text
backend/src/main/java/.../controller/AdvisorWorkspaceController.java
backend/src/main/java/.../service/AdvisorWorkspaceService.java
backend/src/main/java/.../service/impl/AdvisorWorkspaceServiceImpl.java
backend/src/main/java/.../mapper/AdvisorWorkspaceMapper.java
backend/src/main/java/.../dto/AdvisorWorkspace*.java
backend/src/main/java/.../domain/AdvisorGuidance.java
backend/src/main/java/.../domain/AdvisorRecommendation.java
```

Use the repository's actual package path and existing naming conventions. Extend `ClientOnboardingServiceImpl` only for the narrow assignment call after ownership is agreed.

Do not change `WorkspaceClientController`, financial calculations or the meaning of existing trade suggestions merely to reduce code duplication.

## TDD sequence

1. Write controller security tests for authenticated Advisor resolution and role denial.
2. Write mapper tests proving advisor predicates are present in list/detail/read/write queries.
3. Write service tests for client assignment, positive ID validation, status/rationale validation, duplicate prevention and expected-version conflicts.
4. Write PostgreSQL tests for exact decimal eligibility, one-active uniqueness and archive history.
5. Write guidance tests proving save/clear does not touch financial tables or execution jobs.
6. Write recommendation tests for zero quantity, fractional quantity, reserved quantity, acquisition conflict and retained history.
7. Write client-owned endpoint tests for assigned advisor, pending, read-only advisory and no client ID trust.
8. Write onboarding assignment tests and seed repeatability/negative assignment tests.
9. Run unchanged self-directed order and existing role regression tests.
10. Only then perform the Docker/nginx login walkthrough.

## Consumer files

Frontend consumers implemented in this repository are:

- `frontend/src/app/features/advisor/advisor-api.service.ts`
- `frontend/src/app/features/advisor/advisor-clients-page.component.ts`
- `frontend/src/app/features/advisor/advisor-client-detail-page.component.ts`
- `frontend/src/app/features/client/advisory/client-advisory-api.service.ts`
- `frontend/src/app/features/client/advisory/client-recommendations.component.ts`
- `frontend/src/app/features/client/account-page.component.ts`
- `frontend/src/app/profile.ts`

The frontend currently treats these endpoints as proposed and will display unavailable/error states when they are absent. It does not provide a permissive fake-data fallback.

## Completion gate

Backend work is complete only when:

- session-derived Advisor and client ownership is enforced in SQL/service tests;
- guidance and recommendation persistence/history/versioning exists;
- onboarding assignment is transactional or durably idempotent;
- `demo.advisor` is repeatably provisioned without damaging existing demo data;
- fresh and upgrade migrations match and runtime grants are reviewed;
- self-directed trading tests remain green;
- Docker/nginx deep-link and API scope checks pass.

Until then, report frontend completion and backend integration as separate statuses.
