# Advisor Role Frontend Crash Course

Date: 2026-10-22
Status: frontend connected to the implemented Advisor backend; automatic onboarding assignment deferred

## Live backend integration follow-up (2026-10-22)

The user authorized the Advisor backend and all eleven frontend API paths now exist. Migration 018, dedicated guidance/recommendation storage and append-only attributed history are installed. Only the backend container was rebuilt/recreated; Angular adapters, shell, nginx and Compose definitions were not changed. Earlier “proposed/unavailable-only” statements below describe the initial frontend snapshot and are superseded by this section for the current workspace APIs.

Open `http://localhost:8087/login`, sign in as `demo.advisor` with the existing public demo password and open Assigned clients. **Demo Client now appears from live PostgreSQL.** View holdings opens current ledger positions and the authorized catalog. The running demo.client currently has no holdings: the empty state is genuine, not a fixture. No live HOLD/SELL action was demonstrated on nonexistent holdings.

Verified in the real browser: login, list, detail, reload, create a clearly marked demo-verification recommendation and archive it through the inline confirmation. Its persistence/read-only client visibility was independently checked via demo.client's owned advisory API before archiving. No active verification recommendation remains; attributable archived history is retained. Full financial behavior is unchanged.

New backend behavior matches the frontend: expectedVersion/null create-only guidance, versioned NONE tombstones, trimmed rationale bound, conflict-refresh/rebase, recommendation active uniqueness and archival, exact fractional/fully-reserved ownership, JSON DELETE body/200 result, non-disclosing 404, safe message errors and strict integer JSON. Unknown historical assignedAt remains null. Database service failures are unavailable responses, never empty fixtures. Existing HTTP error codes are retained; no new rate limiter was added.

Actual current checks: **442 backend tests passed**, fresh/upgrade parity through 013 passed, optional seed repeatability passed, **90 focused frontend tests passed**, backend Docker build passed, normal migration runner skip-on-rerun passed, live nginx positive/negative/CRUD/version-conflict smoke passed. Editor diagnostics and diff whitespace checks passed. Older unrelated whole-frontend analytics/build permission blockers are historical and were not rerun in this backend-only change.

Remaining: automatic production assignment/allocation and reassignment policy, assignment history/timestamps, a history-view API, broader legacy advisor-ID route hardening, and catalog navigation beyond the frontend's existing first 100 items. Hardened deployments must reapply reviewed runtime grants as owner. The working manual/demo-assigned Advisor workflow is connected; do not claim automatic onboarding is complete.

## Demo login follow-up (2026-10-22)

The user subsequently authorized the optional demo seed change. `demo.advisor` / `RoleDemo2026!` is now provisioned with ADVISOR role and a linked `Demo Advisor` record; only previously unassigned `demo.client` is assigned, with conflicting existing assignment rejected. Existing passwords/balances and `demo.client2` are preserved. This is demo-only provisioning, not automatic production assignment.

Real login and `/api/users/me` were verified through nginx. Open `http://localhost:8087/login` using VM port forwarding or an approved host, sign in and open `/advisor/clients`. A request with Host localhost returns HTTP 200 for the browser route; literal 127.0.0.1 is rejected by existing SSR host policy. Authenticated visual rendering was not browser-verified. If running a frontend image built before the Advisor changes, rebuild that image separately.

The new workspace backend endpoints are still absent, so the assigned-client page displays unavailable/error state rather than populated client data. Historical credential-blocked statements below describe the earlier recovery snapshot; the backend API/persistence/assignment blockers still apply. Isolated seed tests are in `scripts/test-advisor-seed.py` and passed against the actual fresh PostgreSQL schema.

## What exists

The Angular frontend now recognizes `ADVISOR` and opens `/advisor/clients`. The authenticated shell remains the only shell. The Advisor sidebar is generated from `WORKSPACES`; no user ID is hard-coded into navigation.

Implemented browser routes:

- `/advisor/clients`: assigned client list with search, bounded page size, empty state and detail links.
- `/advisor/clients/:id`: assigned client detail with current holdings, guidance editors and recommendations.
- `/app/overview`: client current positions now include an `Advisor's` column and a read-only recommendations section.
- `/profile`: client profile now refreshes assigned advisor state independently.

## Feature files

Advisor-owned implementation is under `frontend/src/app/features/advisor/`:

- `advisor-models.ts` contains proposed wire contracts.
- `advisor-api.service.ts` contains proposed API calls and error normalization.
- `advisor-clients-page.component.ts` renders the assigned client list.
- `advisor-client-detail-page.component.ts` renders holdings, guidance and recommendation controls.
- `advisor-utils.ts` contains decimal-safe positive ownership and rationale validation helpers.

Client presentation is under `frontend/src/app/features/client/advisory/`:

- `client-advisory-api.service.ts` calls session-owned client endpoints.
- `client-advisory-models.ts` contains client read-only contracts.
- `client-recommendations.component.ts` renders the client table.

The shared changes are limited to route/policy registration, the account page, its tests, the profile component and its new test.

## State and financial separation

Advisory information is deliberately not part of the portfolio DTO or financial resource. The account page owns separate `PageResource` instances for portfolio, valuation, orders and advisory data. A failed advisory request therefore leaves cash, holdings, valuation and orders available.

Recommendations are not positions. They do not contribute to totals, do not change cash, do not change owned/reserved/available units and do not call order preview or order submission endpoints. A `SELL` guidance value is an opinion and never issues an order.

The Advisor detail page also keeps separate read and save state. A failed save leaves the current draft in the form. Guidance/recommendation edits update local rows only from the confirmed server response; create/archive refresh the server lists and portfolio. In-flight editors are disabled so confirmation cannot discard newer input. A conflict refreshes while preserving the draft and its old version; the advisor must explicitly choose “Use refreshed version with my draft” before retrying. Archive conflicts close confirmation and refresh before another archive.

Client switches cancel old reads/mutations and clear drafts. Session changes also clear every Advisor draft, pending confirmation, selected client, catalog and mutation state. Permission/identity/non-disclosing 404 failures clear protected Advisor detail content rather than retaining other successful protected resources. Independent 503 guidance failures still leave holdings useful. The client list clears sensitive search drafts on session changes. No core session/resource code was refactored.

## Guidance semantics

The initial status set is:

- `NONE`: no guidance recorded, displayed as N/A only after a successful guidance response;
- `HOLD`: retain the position according to the advisor's view;
- `SELL`: consider selling according to the advisor's view.

A guidance request failure displays `Unavailable`, not N/A. Legacy `BUY`, `SUGGESTED`, `ACCEPTED` and other trade-suggestion statuses are not mapped into this enum.

## Recommendation semantics

Recommendations are active entries for instruments the client does not currently hold. The UI:

- selects a stable `instrumentId` from the proposed Advisor catalog;
- trims reasoning and requires 1-2000 characters;
- blocks active duplicate instrument IDs in the local state;
- excludes positive owned quantities;
- allows zero quantities as eligible;
- treats fractional quantities as ownership;
- does not use available-to-sell as the ownership predicate;
- requests confirmation before archive;
- retains the row until archive success.

Eligibility is fail-closed while portfolio, recommendation or catalog data is missing/loading/failed, and is rechecked at submit. Both Advisor and client recommendation tables omit instruments with exact positive owned units after a portfolio refresh. Server rules must repeat the check transactionally. Guidance shows author and server UTC timestamp. Separate guidance and instrument-catalog retry controls do not require a full page reload.

Archive confirmation is intentionally an **inline nonmodal group**, not a modal dialog that falsely claims focus containment. It names the instrument, receives keyboard focus, supports Cancel/Escape, restores focus to the triggering row button when cancelled, keeps the row until success and shows archive failure inside the confirmation. Ordinary Tab navigation remains available. Focus and text escaping are tested in jsdom; no live visual/browser audit is claimed.

The server remains authoritative for eligibility, duplicates, assignment and history. The local checks are UI feedback and fixture behavior, not an authorization boundary.

## Identity and API behavior

The proposed Advisor endpoints derive Advisor identity from the authenticated session. The browser does not send an advisor ID to establish scope. Client advisory endpoints derive client identity from the client session and do not accept a client ID.

The adapter uses relative `/api` paths so the current nginx path works in Docker. It adapts both Spring-style `message` and FastAPI-style `detail` errors into the shared Angular error shape. It does not silently fall back to fixture data.

The current endpoints are proposed, not verified live:

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

The backend handoff now contains complete candidate JSON envelopes, nullability, timestamps, decimal strings, first-write and NONE tombstone semantics and 400/401/403/404/409/429/503 behavior. The existing adapter selects a JSON DELETE body `{ "expectedVersion": 3 }` and expects a 200 `{ "recommendationId": 9, "version": 4 }`. Backend owner approval and implementation are still required before calling this integration live.

The current selector loads a bounded first catalog page (100 records); catalog search/page controls are not implemented. If the approved catalog exceeds this page, add role-owned catalog navigation before claiming complete instrument coverage. This does not widen a CLIENT-only catalog guard.

## Walkthroughs

### Advisor preview

The colocated component specs use `provideHttpClientTesting` and synthetic HTTP responses; `advisor-routing.spec.ts` injects a role-owned API double while exercising the actual lazy route entries and the existing guards. Run the Advisor specs to preview populated/error/empty and mutation states in the test harness. These fixtures use stable synthetic IDs and the neutral `Demo Advisor` display name, not a browser login or hard-coded runtime user mapping.

Open a client detail fixture and confirm holdings remain visible while guidance is unavailable. Select HOLD or SELL, enter rationale and save. A failed save keeps the draft. A 409 refresh requires explicit draft rebase. Add an eligible not-held recommendation, edit it, then archive it through inline confirmation. No order or trade-suggestion endpoint is called by these interactions.

This is a test-harness preview only while `demo.advisor` is not provisioned. It must not be described as live persistence.

### Client preview

Use the client account fixture to return guidance and recommendations. Confirm the `Advisor's` column joins by `instrumentId`, and the table below positions is read-only. Return a 503 advisory response and confirm the cash, holdings, valuation and orders still render.

On profile, return `ASSIGNED` with `Demo Advisor`, `PENDING` with no advisor, or a 503. Confirm each state is distinct.

## Docker/nginx behavior

No nginx edit is required. Existing nginx rules send:

- browser routes to `frontend:4000`;
- `/api/` requests to `backend:8080`.

Once the backend exists, verify the feature through the nginx public port and not only through direct container ports. Do not delete retained database volumes or change `.env` to make the feature appear available.

## Tests and status

The implemented focused tests cover:

- Advisor role normalization, landing and route safety;
- adapter request shapes, expected versions and error mapping;
- decimal-safe eligibility and rationale bounds;
- client list populated and empty states;
- detail holdings retention during advisory outage;
- failed guidance draft preservation and no order endpoint use;
- client account advisory outage isolation;
- assigned, pending and unavailable profile assignment.

### Recovery verification, actually executed 2026-10-22

- Restored baseline Advisor specs: 14 passed. New regression assertions were run failing before scoped production fixes; a compile-only fixture mistake was corrected before claiming the client behavioral red phase.
- Final Advisor include: **50 passed, 5 files** (adapter 11, utility 7, list 10, detail 15, routing 7).
- Client advisory/account/profile/policy includes: **40 passed, 5 files**; combined focused coverage **90 passing tests**.
- Full frontend suite, once: **243 passed, 1 failed**. Untouched analytics spec incorrectly rejects any `0` in the entire page, while static copy includes “Indexed to 100”. No analytics fix was made.
- Style check, once: **passed**.
- Normal production build, once: compilation/bundles completed, then **EACCES** unlinking the existing root-owned `frontend/dist/pole/3rdpartylicenses.txt`. The output directories are also root-owned. No successful artifact build is claimed; VM owner must correct output permissions. Existing analytics CSS budget warning also remains.
- `git diff --check`: passed. Angular focused compilation and checked account/profile editor diagnostics reported no relevant errors.

No backend tests/migrations, real browser visual walkthrough or live Docker Advisor login were run. Backend persistence, automatic assignment, server ownership enforcement, `demo.advisor` provisioning and live API data remain **pending independently**. Only `demo.client` may be assigned in the optional future seed; `demo.client2` remains a negative fixture. No credential/auth bypass or runtime fixture fallback was added.

The detailed recovered status and command evidence are in `AdvisorLocalImplPlan.MD`; the backend task is `AdvisorRoleBackendImplementation.MD`; shared onboarding/seed exceptions, alternatives and approval gates are in `SharedChangeProposal/README.MD`.
