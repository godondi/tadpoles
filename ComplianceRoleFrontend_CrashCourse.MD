# Compliance role frontend crash course

## What is implemented

The Compliance role now has dedicated standalone Angular screens in
`frontend/src/app/features/compliance/`:

- `ComplianceInvestigationsPageComponent` at `/compliance/investigations`
- `ComplianceInvestigationDetailComponent` at
  `/compliance/investigations/:id`
- `ComplianceEvidencePageComponent` at `/compliance/evidence`

The existing workspace policy and inherited `workspaceGuard` remain the source of role
authorization. The route table only replaces the three generic `StaffPageComponent`
loaders; Operations, Analytics, Finance, and Client routes are not changed.

The screens emulate the established Operations layout without reusing Operations
authorization or shared evidence components:

- an access-purpose/context panel;
- workspace heading and explanatory copy;
- bounded filters and reset/apply actions;
- accessible horizontally scrollable tables with captions and scoped headers;
- explicit loading, empty, partial, denied, expired, unavailable, and retry states;
- independent detail evidence sections with server sequence and UTC timestamps;
- server-provided manifest metadata without “tamper-proof” claims.

## Purpose and scope flow

`ComplianceContextComponent` asks the proposed
`GET /api/compliance/access-policy` for configured approved purposes. A purpose and
8–200 character case reference are only input. The frontend requests a server-issued
context through proposed `POST /api/compliance/access-contexts`; it never fabricates a
context ID or scope.

The context is held in memory. It carries the opaque `X-Compliance-Context` header on
protected reads, expires on the server-provided time, and is cleared on:

- explicit “End context and clear evidence”;
- auth session change/logout;
- `401`, `403`, or `409` protected responses;
- local expiry timer.

Purpose policy failure keeps protected searches, detail reads, and export actions
disabled. The case reference is not stored in local/session storage or put into a URL.
Only non-sensitive search filters are carried to the detail back link.

## Search walkthrough

1. Sign in as a Compliance user.
2. Open **Investigations**.
3. Select a server-approved purpose and enter a case reference. In the current
   application the purpose endpoint is unavailable, so the context button remains
   disabled and a precise policy-unavailable message is shown.
4. Once a real backend issues a context, use the bounded filters:
   positive order ID, UTC start/end (maximum 31 days), safe client reference,
   instrument, and lifecycle status.
5. Apply filters. The adapter sends only defined fields with `page` and `pageSize=25`.
   Scope is expected to be applied by the backend before totals and pagination.
6. Review the table's order reference, safe client reference, exact quantity/unit,
   status, execution mode, record kind, nullable times, and completeness state.
   Missing acceptance/outcome times show “Not recorded”; a rejection is never rendered
   as a fill.
7. Open **Review evidence** to preserve the non-sensitive filters on the back link.

## Detail walkthrough

The detail page requests the existing proposed-compatible path
`GET /api/compliance/orders/{id}/evidence` only after a valid context exists and the
ID is a positive integer. It shows:

- original instruction and safe account reference;
- authenticated actor;
- record kind, execution mode and nullable UTC times;
- accepted policy versus current policy;
- server findings with `PASS`, `FAIL`, `NOT_EVALUATED`, and `UNKNOWN`;
- ordered evidence sections with coverage, source, as-of time, completeness, reference,
  sequence, actor, recorded time, and redacted facts;
- independently paged section requests through the proposed
  `/api/compliance/orders/{id}/evidence/{section}` path;
- explicit limitations for legacy or partial records;
- export entitlement state.

The browser does not infer causal order from timestamps, infer fill success from a
status, or turn displayed evidence into a local file.

## Evidence and export walkthrough

The evidence screen requests existing
`GET /api/compliance/evidence` only after context authorization. It renders scoped
history, request reference, actor when supplied, purpose reference, state, created/as-of
times, expiry, scope, and server manifest data:

- schema version;
- source snapshot;
- per-section counts;
- content digest;
- signature and independent-anchor status;
- verification state.

The current backend deliberately returns `503`, so the screen presents an unavailable
state rather than an empty success. There is no retention editor, audit mutator,
“tamper-proof” badge, guessed download URL, or client-generated CSV/Blob.

The detail screen contains a deliberate export request path for a future authorized
fixture. It requires `exportAllowed`, `EXPORT_CREATE`, a browser-generated idempotency
key, and a user click. A `409` is treated as an uncertain outcome requiring receipt
recovery rather than an automatic duplicate request. Separate download permission is
represented in the adapter but is not enabled by ordinary rendering.

## Adapter and resource behavior

`ComplianceApi` owns:

- exact API paths and payload types;
- context creation/expiry/revocation;
- no-store headers;
- opaque context header;
- local denial when context is absent/expired;
- protected response clearing on `401`, `403`, and `409`;
- export idempotency header and permission gates.

`ComplianceResource<T>` owns one cancellable request, clears protected data on context
changes, and maps local error states:

- `400`: invalid filters;
- `401`: expired session;
- `403`/`409`: denied or expired purpose context;
- `404`: non-disclosing record not found;
- `429`: retry later;
- `503`: source/policy unavailable;
- `0`: offline/transport failure.

There is no fallback to Operations APIs and no broad client-order fetch.

## Fixture reproduction

The deterministic populated, empty/partial, rejection, missing quote, manifest, and
unavailable states are defined in:

- `frontend/src/app/features/compliance/compliance-api.service.spec.ts`
- `frontend/src/app/features/compliance/compliance-screens.spec.ts`
- `frontend/src/app/features/compliance/compliance-feature.spec.ts`

The fixtures contain safe synthetic references only. They do not contain credentials,
bank details, identity data, or browser-persisted evidence.

## Actual versus proposed functionality

### Actual in this frontend change

- Compliance role URLs still exist and remain guard-protected.
- Dedicated search, detail, and evidence/export layouts are implemented.
- The adapter, typed models, context gating, cancellation, local error mapping, route
  integration, pagination controls, accessibility structure, and deterministic fixture
  tests are implemented.
- The current 503 state is presented truthfully.

### Proposed/backend-blocked

- Approved Compliance purpose policy and context issuance.
- Compliance-specific account/object grants and revocation.
- Successful scoped search/detail/section/evidence responses.
- Protected access auditing and fail-closed audit dependency behavior.
- Immutable export snapshots, receipt recovery, manifests, expiry, and authorized
  downloads.
- Database tables/migrations/runtime grants and real PostgreSQL evidence tests.

Frontend rendering of a populated test fixture is not production evidence capability.
The three current server paths remain unavailable until the backend handoff is
implemented and verified.

## Login and deployment asset recovery

The frontend trusts only the `X-Forwarded-For` and `X-Forwarded-Proto` headers sent
by the nginx reverse proxy. This removes Angular SSR proxy-header warnings without
trusting unrelated forwarded headers.

After a frontend image is rebuilt, an already-open browser can retain an older hashed
JavaScript entry point while the container has removed its matching lazy chunk. This
can make valid credentials appear to fail during the post-login navigation. The router
detects the known lazy-chunk failure, performs one cache-busting reload, and the login
screen reports an actionable refresh message if the asset is still unavailable.

Deploy frontend source changes with a rebuilt/recreated container, then verify the
browser loads the current hashed entry point:

```sh
docker compose build frontend
docker compose up -d frontend
```

If the Compose plugin is not installed, use `docker-compose` for the same commands.

The nginx proxy and frontend must be from the same build. Do not diagnose a missing
hashed chunk as a credential failure until the browser has been refreshed and the
frontend image has been rebuilt.

## Verification

The focused command was run in a Node 22 container because the host has no `node` or
`npm` executable:

```sh
docker run --rm -v /home/ec2-user/tadpoles/frontend:/app -w /app \
  node:22-bookworm-slim npm test -- --watch=false \
  --include='src/app/features/compliance/**/*.spec.ts'
```

Result: **12 tests passed in 3 files**.

The production build was also run with the same container command:

```sh
docker run --rm -v /home/ec2-user/tadpoles/frontend:/app -w /app \
  node:22-bookworm-slim npm run build
```

Result: **passed**. The existing analytics component emitted its pre-existing
4 kB component-style budget warning; the Compliance lazy chunks did not introduce a
build error.

The QoF style check was also run and passed:

```text
QoF style check passed: circular loading spinner, square corners elsewhere, and no CSS gradients.
```

The full frontend suite was run once. It produced **191 passing tests and one unrelated
pre-existing Analytics failure** in
`src/app/features/analytics/analytics-page.component.spec.ts`, where the existing
unavailable-report assertion rejected a rendered `0`; the run also logged the existing
jsdom `ResizeObserver is not defined` errors from shell tests. No Compliance test
failed. That unrelated failure and shared test-environment issue were left unchanged.

## Backend integration checklist

1. Implement and test the proposed policy/context endpoints with explicit COMPLIANCE
   authorization and account/object scopes.
2. Freeze the exact DTOs, enums, nullable fields, page bounds, no-store behavior,
   context transport, and error payloads against this handoff.
3. Implement scope-before-count search and repeatable-read detail/first-section
   snapshots using retained evidence only.
4. Add deterministic section pagination, completeness/limitation states, and protected
   access auditing that fails closed.
5. Add immutable, idempotent export snapshots, manifest/digest semantics, expiry,
   redaction, receipt recovery, and separate download authorization.
6. Add additive fresh/upgrade migrations, indexes, foreign keys, append-only guards,
   and least-privilege runtime grants without deleting or rewriting retained evidence.
7. Run real PostgreSQL tests for filled/rejected/partial history, scope denial,
   context expiry/revocation, count leaks, audit/source outage, pagination, export
   tamper/expiry, and permission loss.
8. Re-run the focused Compliance tests and the full frontend verification commands
   after the live contract is available. Do not report the workflow as complete while
   the backend remains 503.
