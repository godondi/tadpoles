# Compliance role frontend crash course

## What is implemented

The Compliance role now has dedicated standalone Angular screens in
`frontend/src/app/features/compliance/`:

- `ComplianceInvestigationsPageComponent` at `/compliance/investigations`
- `ComplianceInvestigationDetailComponent` at
  `/compliance/investigations/:id`
- `ComplianceEvidencePageComponent` at `/compliance/evidence`
- `ReviewCasesComponent` at `/compliance/cases` and `/compliance/cases/:reference`

The existing workspace policy and inherited `workspaceGuard` remain the source of role
authorization. The route table only replaces the three generic `StaffPageComponent`
loaders; Operations, Analytics, Finance, and Client routes are not changed.

The screens use the established workspace layout and shared presentation primitives without
reusing Operations authorization:

- an access-purpose/context panel;
- workspace heading and explanatory copy;
- bounded filters and reset/apply actions;
- accessible horizontally scrollable tables with captions and scoped headers;
- explicit loading, empty, partial, denied, expired, unavailable, and retry states;
- independent detail evidence sections with server sequence and UTC timestamps;
- server-provided manifest metadata without “tamper-proof” claims.

## Purpose and scope flow

`ComplianceContextComponent` asks
`GET /api/compliance/access-policy` for configured approved purposes. A purpose and
8–200 character case reference are only input. The frontend requests a server-issued
context through `POST /api/compliance/access-contexts`; it never fabricates a
context ID or scope.

The context is held in memory. It carries the opaque `X-Compliance-Context` header on
protected reads, expires on the server-provided time, and is cleared on:

- explicit “End context and clear evidence”;
- auth session change/logout;
- `401`, `403`, or typed `CONTEXT_INVALID` protected responses;
- local expiry timer.

Purpose policy failure keeps protected searches, detail reads, and export actions
disabled. The case reference is not stored in local/session storage or put into a URL.
Only non-sensitive search filters are carried to the detail back link.

Choose EXTERNAL for an approved external reference or MANAGED for a verified review.
Managed review contexts bind account/optional order, current assigned reviewer and
review version. Any retained review update invalidates the context; reassignment never
grants account evidence. Private queue/decision requests use independent investigation
permissions, not the evidence-purpose token. Findings/proposed public replies never
publish to the client. Account-only reviews honestly have no order archive.

## Search walkthrough

1. Sign in as a Compliance user.
2. Open **Investigations**.
3. Select a server-approved purpose and enter the correct EXTERNAL or MANAGED reference.
   Missing configuration/grants/audit remain explicit unavailable or denied states.
4. Once the backend issues a context, use the bounded filters:
   positive order ID, UTC start/end (maximum 31 days), safe client reference,
   instrument, and lifecycle status. UTC-labelled date/time inputs are validated and
   serialized as UTC Instants; reopening applied filters restores the same UTC wall time.
5. Apply filters. The adapter sends only defined fields with `page` and `pageSize=25`.
   Scope is expected to be applied by the backend before totals and pagination.
6. Review the table's order reference, safe client reference, exact quantity/unit,
   status, execution mode, record kind, nullable times, and completeness state.
   Missing acceptance/outcome times show “Not recorded”; a rejection is never rendered
   as a fill.
7. Open **Review evidence** to preserve the non-sensitive filters on the back link.

## Detail walkthrough

The detail page requests
`GET /api/compliance/orders/{id}/evidence` only after a valid context exists and the
ID is a positive integer. It shows:

- original instruction and safe account reference;
- authenticated actor;
- record kind, execution mode and nullable UTC times;
- accepted policy versus current policy;
- server findings with `PASS`, `FAIL`, `NOT_EVALUATED`, and `UNKNOWN`;
- ordered evidence sections with coverage, source, as-of time, completeness, reference,
  sequence, actor, recorded time, and redacted facts;
- independently paged section requests through
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

Configured live sources return scoped, audited evidence; missing source/policy/audit
returns an unavailable state rather than an empty success. There is no retention editor, audit mutator,
“tamper-proof” badge, guessed download URL, or client-generated CSV/Blob.

The detail screen requires `exportAllowed`, EXPORT_CREATE, an original idempotency key
and a user click. Unknown outcomes retain opaque recovery metadata and the original
key; lookup 404 is not permission to replace it. IDEMPOTENCY_CONFLICT does not invalidate
a valid purpose context. Receipt/download actions recheck independent EXPORT_DOWNLOAD,
requester, exact Operations-account permission, expiry and stored digest. Only server
bytes become a download; displayed rows never become a fabricated archive.

## Adapter and resource behavior

`ComplianceApi` owns:

- exact API paths and payload types;
- context creation/expiry/revocation;
- no-store headers;
- opaque context header;
- local denial when context is absent/expired;
- protected response clearing on `401`, `403`, and `CONTEXT_INVALID`;
- export idempotency header and permission gates.

`ComplianceResource<T>` owns one cancellable request, clears protected data on context
changes, and maps local error states:

- `400`: invalid filters;
- `401`: expired session;
- `403`/`CONTEXT_INVALID`: denied or expired purpose context; other conflicts retain their own meaning;
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

## Implemented capability and deployment limits

### Implemented

- Compliance role URLs still exist and remain guard-protected.
- Dedicated search, detail, and evidence/export layouts are implemented.
- The adapter, typed models, context gating, cancellation, local error mapping, route
  integration, pagination controls, accessibility structure, and deterministic fixture
  tests are implemented.
- Approved purpose issuance, independent live grants, scoped backend evidence, protected
  audits, immutable snapshots, original-key recovery and authorized downloads.
- Separate private review assignment/information/decision/close/reopen lifecycle.

Deploy through schema 017 and reapply restricted runtime grants. Configuration does
not provision production account permissions, alerting, response-time commitments,
provider credentials or external settlement evidence. A populated browser fixture is
not production approval.

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

1. Apply the actual ordered migration chain through 017 and restricted runtime grants;
   verify readiness with the intended non-owner role before rollout.
2. Provision approved purpose configuration and independent account/evidence/export/
   investigation grants. Do not alias Operations grants or seed production demo users.
3. Exercise allowed and denied flows, live revocation, original-key recovery and context
   replacement using real PostgreSQL plus browser cancellation/UTC regressions.
4. Verify managed narrowing across search/count/sections/history/receipt/download,
   including unrelated external archives and reassignment away/back.
5. Preserve explicit partial/unavailable states and the private-public publication
   boundary; no deployment approval follows merely from fixture/unit success.
