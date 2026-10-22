# Compliance role backend implementation handoff

## Status and scope

This document is the backend/database handoff produced from the Compliance frontend
implementation. The Compliance backend is now implemented behind explicit
configuration, role, client-scope grants, purpose contexts, append-only audit records,
and immutable server-owned exports. The implementation does not change booking
transactions, Operations authorization, or shared security/CORS/error-handler policy.

Existing contexts issued before the scope-snapshot migration are intentionally rejected
and must be replaced; this prevents a scope change from widening an already-issued
context.

The frontend keeps Compliance separate from `AUDITOR`, `ADMIN`, and `TRADING_OPS`. A role
membership alone must never grant access to client accounts, evidence, or exports.

## Endpoint inventory

### Existing endpoints and current behavior

`StaffWorkspaceController` currently declares these role-protected paths:

| Method | Path | Current status | Frontend consumer |
| --- | --- | --- | --- |
| GET | `/api/compliance/investigations` | Implemented; `503` when policy, source, or required audit storage is unavailable | Investigation search |
| GET | `/api/compliance/orders/{id}/evidence` | Implemented; scoped and audited | Investigation summary and first evidence sections |
| GET | `/api/compliance/evidence` | Implemented; scoped export history | Scoped export history |

They are not interchangeable with Operations endpoints. The current `503` response must
remain an unavailable state until a genuine source, policy, and protected access audit
are configured.

### Proposed endpoints consumed by the frontend

All protected responses must be private and sent with `Cache-Control: no-store`. The
frontend sends the opaque server-issued `X-Compliance-Context` header on protected
requests. The application is currently same-origin; if deployment makes this
cross-origin, CORS and preflight policy must be coordinated before enabling the header.

#### GET `/api/compliance/access-policy`

Proposed purpose-policy discovery, not an authorization grant.

```json
{
  "configured": true,
  "purposes": ["ORDER_REVIEW", "REGULATORY_RESPONSE"],
  "scopeLabel": "Assigned accounts only",
  "policyVersion": "compliance-policy-1"
}
```

`purposes` is a non-null array of server-approved codes. `configured=false` or `503`
keeps protected UI actions disabled. The frontend never treats a typed purpose or a
selected option as authorization.

#### POST `/api/compliance/access-contexts`

Request:

```json
{
  "purposeCode": "ORDER_REVIEW",
  "caseReference": "CASE-12345678"
}
```

The request must reject unknown purpose codes, control characters, whitespace-only
references, references shorter than 8 or longer than 200 characters, and references
containing sensitive client prose. A successful response should be `201` and contain:

```json
{
  "contextId": "server-issued-opaque-id",
  "purposeCode": "ORDER_REVIEW",
  "caseReference": "CASE-12345678",
  "scopeLabel": "Assigned accounts only",
  "permissions": ["EVIDENCE_READ"],
  "expiresAt": "2026-10-22T18:30:00Z",
  "policyVersion": "compliance-policy-1"
}
```

`contextId` is opaque and must not encode client or order identifiers. Expiry and
revocation are checked on every protected request. A purpose-policy or grant change
invalidates the context; the server must not silently widen it.

#### GET `/api/compliance/investigations`

Query fields are bounded and exact:

| Field | Required/nullability | Type and bounds |
| --- | --- | --- |
| `orderId` | optional | positive integer |
| `from` | optional | ISO-8601 UTC instant |
| `to` | optional | ISO-8601 UTC instant, after `from`, range at most 31 days |
| `clientReference` | optional | permitted safe reference, max 80 characters |
| `instrument` | optional | defined instrument reference, max 80 characters |
| `status` | optional | `SUBMITTED`, `ACCEPTED`, `REJECTED`, `FILLED`, or `CANCELLED` |
| `page` | required | zero-based integer, `0..1000` |
| `pageSize` | required | integer `1..100`, frontend default `25` |

Scope and purpose checks must run before joins, counts, sorting, and page offsets. The
server must never return all client orders for the browser to filter. Response `200`:

```json
{
  "items": [{
    "orderId": 42,
    "clientReference": "safe-client-reference",
    "instrument": "SIM-USD",
    "side": "BUY",
    "quantity": "1.2500",
    "unit": "shares",
    "status": "REJECTED",
    "submittedAt": "2026-10-21T10:00:00Z",
    "acceptedAt": null,
    "outcomeAt": "2026-10-21T10:00:03Z",
    "executionMode": "PROVIDER_BACKED",
    "recordKind": "SELF_DIRECTED",
    "evidenceCompleteness": "PARTIAL"
  }],
  "page": 0,
  "pageSize": 25,
  "total": 1,
  "scope": "Assigned accounts only",
  "asOf": "2026-10-22T17:00:00Z"
}
```

`quantity` is an exact decimal string. Timestamps are nullable ISO-8601 UTC strings;
the server must not derive `outcomeAt` or fill success from a legacy `EXECUTED` label.
`evidenceCompleteness` is `PASS`, `FAIL`, `NOT_EVALUATED`, or `UNKNOWN`; missing
evidence is never `PASS`.

#### GET `/api/compliance/orders/{id}/evidence`

`id` is a positive integer. The response is `200` only after object scope and
`EVIDENCE_READ` authorization succeed:

```json
{
  "summary": "<same ComplianceOrderSummary shape>",
  "authenticatedActor": "compliance.actor",
  "accountReference": "safe-scoped-account-reference",
  "acceptedPolicy": {},
  "currentPolicy": {},
  "snapshotAt": "2026-10-21T10:01:00Z",
  "sections": ["<ComplianceEvidenceSection, in server order>"],
  "findings": [{
    "code": "QUOTE_MISSING",
    "explanation": "Provider quote was not retained.",
    "status": "UNKNOWN"
  }],
  "exportAllowed": false,
  "limitations": ["Historical record is partial."]
}
```

`acceptedPolicy` and `currentPolicy` are nullable objects. Current controls must never
be presented as the policy that governed a historical decision. `findings.status` and
each section's `completeness` use the four-state enum above. `sections` must preserve
the server's fixed order; the server must not let a user select arbitrary SQL
projections.

Each `ComplianceEvidenceSection` has:

```json
{
  "name": "quotes",
  "items": [{
    "reference": "quote-id",
    "sequence": null,
    "actor": null,
    "occurredAt": "2026-10-21T10:00:01Z",
    "values": {"stage": "ACCEPTANCE", "price": "101.25"}
  }],
  "page": 0,
  "pageSize": 25,
  "total": 1,
  "hasMore": false,
  "coverage": "Provider observations retained",
  "source": "order_quote_observations",
  "asOf": "2026-10-21T10:01:00Z",
  "completeness": "PASS"
}
```

`values` is a server-redacted map whose numbers representing financial quantities or
prices remain decimal strings. It must not contain credentials, bank details, raw
idempotency keys, unrestricted profile data, or unbounded JSON.

#### Proposed independently paged section endpoint

`GET /api/compliance/orders/{id}/evidence/{section}?page=0&pageSize=25` returns one
`ComplianceEvidenceSection` with the same bounds and statuses. The allowed section
enum is fixed by the backend and must be documented with tests; a user-supplied section
name must not become SQL. The frontend currently displays the retained evidence
sections needed for:

1. accepted instruction and identity-safe account reference;
2. acceptance validation, decisions, policy snapshot, and reservation;
3. chronological lifecycle events;
4. quote observations;
5. execution attempts and rejection stage;
6. fills and cash/position postings;
7. reconciliation findings and limitations;
8. access/export history, integrity, and completeness.

The existing detail path remains the summary/first-page contract. A new section path
must not change booking transactions.

#### GET `/api/compliance/evidence`

Response `200`:

```json
{
  "items": [{
    "requestReference": "export-42",
    "scope": "Assigned accounts only",
    "actor": "compliance.actor",
    "createdAt": "2026-10-21T11:00:00Z",
    "asOf": "2026-10-21T10:01:00Z",
    "purposeReference": "CASE-12345678",
    "state": "READY",
    "expiresAt": "2026-10-22T11:00:00Z",
    "manifest": "<ComplianceManifest or null>",
    "authorizedActions": ["DOWNLOAD"]
  }],
  "page": 0,
  "pageSize": 25,
  "total": 1,
  "scope": "Assigned accounts only",
  "asOf": "2026-10-22T17:00:00Z"
}
```

`state` is `REQUESTED`, `READY`, `FAILED`, `EXPIRED`, or `DENIED`. It must describe
the actual server lifecycle; do not introduce an asynchronous state if exports are
synchronous. `actor` and `expiresAt` are nullable where policy redacts or does not
apply them.

`manifest` contains `schemaVersion`, `sourceSnapshot`, `sectionCounts`,
`contentDigest`, `signatureStatus`, `anchorStatus`, and `verification`, where the
status values are explicit and `verification` is one of the four evidence statuses.
A digest proves bytes match a manifest; it does not prove legal admissibility,
completeness, or independently anchored immutability.

#### Proposed export actions

- `POST /api/compliance/evidence/exports` with `{ "orderId": 42 }`,
  `Idempotency-Key: <8..80 safe characters>`, and the context header.
- `GET /api/compliance/evidence/exports/{requestReference}` for receipt/manifest
  recovery using the original request key where required.
- `GET /api/compliance/evidence/exports/{requestReference}/download` for a separately
  authorized server-generated artifact.

Creation must recheck purpose, object scope, `EVIDENCE_READ`, export entitlement,
redaction policy, source snapshot, retention, and scope version. A lost response must
be recoverable by the same idempotency key and must not create a second archive.
Download must recheck permission and expiry. The server owns the bytes; a browser
must never build a CSV/Blob from displayed rows. No retention edit, legal deletion,
arbitrary audit mutation, or guessed export ID is supported.

## Authorization and context model

1. Authenticate the actor and require `COMPLIANCE` explicitly at the Compliance
   controller boundary.
2. Require a separate Compliance grant that maps the actor to one or more client
   account scopes. Do not alias an Operations grant, `AUDITOR`, `ADMIN`, or a generic
   non-client bypass.
3. Require a non-revoked, non-expired context whose purpose is approved by the current
   policy and whose actor matches the request.
4. Scope the base order query by grant before joins, totals, paging, evidence sections,
   reconciliation findings, and export generation.
5. Recheck object scope and permission on every detail, section, export, receipt, and
   download request. A known foreign ID returns non-disclosing `404`, not a scope leak.
6. Record protected access attempts, returned counts, filter digest, scope version,
   context, purpose, outcome, correlation ID, and export reference. If required audit
   persistence is unavailable, fail closed with `503`; do not return a successful
   empty page.

Context revocation, grant expiry, role removal, purpose-policy change, or permission
loss clears access. The backend must not auto-renew or expand scope. Access events and
exports are append-only and must be protected by the existing immutable-evidence
guard/grant pattern or a separately justified Compliance equivalent.

## Evidence lineage and limitations

| Frontend field/section | Retained source | Required treatment |
| --- | --- | --- |
| Summary instruction, status, quantity, record kind, execution mode, submitted/accepted times | `client_trades`, joined only after client scope; instrument/policy snapshots | Preserve exact decimals and nulls. Never infer fill/outcome from status. |
| Account reference and authenticated actor | scoped client/account mapping and authenticated user | Return safe references only; actor is the current authenticated reader, not an invented order actor. |
| Accepted instruction and reservation | `client_trades.order_type`, `policy_snapshot`, `order_reservations` | Show original accepted policy separately from current policy; missing records are partial. |
| Acceptance validation and decisions | `order_rule_decisions`, `order_decision_contexts` | Keep stage, policy version, pass/fail, code, explanation, quote link, and redacted facts. |
| Lifecycle chronology | `order_events` | Use recorded `sequence`, actor, `occurred_at`, state transition, reason and links. Equal timestamps do not establish causation. |
| Quote observations | `order_quote_observations.facts` | Whitelist stage, side, exact price/currency, source, provider/received times, stale decision, and fill link. |
| Execution attempts | `order_execution_attempts` | Preserve attempt number/outcome/reason and distinguish admission from execution. |
| Fills | `order_fills` | Use exact decimal strings, quote/execution references, booked/executed times, fee and posting group. |
| Cash/position postings | `cash_journal`, `position_journal` joined through `order_fills` | Show exact signed deltas and posting groups. These are internal journals, not external bank/custody settlement. |
| Notifications | `outbox_events` | Show retained notification facts and delivery limitations; do not claim external delivery. |
| Reconciliation | `reconciliation_runs`, `reconciliation_findings` | Include only separately permitted, explicitly linked findings. Absence is not certification and internal consistency is not custody reconciliation. |
| Access/export history | future Compliance access/export tables or explicitly authorized compatible extension | Do not reuse Operations URLs or role annotations without a separate authorization decision. |

Legacy rows can lack software version, rule inputs, correlations, quotes, or exact
timestamps. Return `UNKNOWN`, `NOT_EVALUATED`, or a partial completeness finding.
Do not edit booking transactions to manufacture missing evidence.

## Transaction, chronology, and auditing requirements

- Read the summary and first sections in one `REPEATABLE READ` snapshot with a server
  `snapshotAt`/watermark.
- Use fixed section names and deterministic ordering: sequence where present, then
  recorded time and immutable reference. Page offsets/cursors must be bounded and
  stable for the snapshot.
- Keep later pages tied to the original snapshot/version where practical; otherwise
  return a changed-snapshot conflict rather than silently mixing histories.
- Validate that section totals and returned counts were computed after scope.
- Audit both successful and denied sensitive reads. If audit insertion/commit fails,
  fail the read closed.

## Backend candidates and database work

Role-owned candidates are `ComplianceController`, `ComplianceAccessContextService`,
`ComplianceInvestigationService`, `ComplianceEvidenceMapper`, DTOs matching the
frontend models, and `ComplianceExportService`. Reuse existing fixed projection and
decimal-redaction ideas only through an explicitly authorized compatible extension.
Do not widen `OperationsAccessService` or add `hasRole('COMPLIANCE')` to an Operations
route as a shortcut.

If separate persistence is justified, add only additive objects:

- Compliance grants with actor, client/account scope, approval reference, validity and
  one-way revocation;
- access contexts with purpose, actor, policy version, expiry and revocation state;
- append-only protected access events;
- immutable export request/manifest/archive metadata with unique
  `(requester, idempotency_key)` and scope/snapshot/digest fields.

Add foreign keys to users, clients, orders and contexts; indexes for actor/validity,
scope/order/time, context/time, and requester/idempotency; and constraints for positive
IDs, bounded strings, expiry ordering, allowed permissions/states, and exact digest
formats. Keep append-only triggers and least-privilege runtime grants. Update
`database/fresh/`, ordered upgrade migrations, migration ownership, and runtime grant
documentation in lockstep. Rollback is additive application rollback plus forward
compatibility; never delete retained evidence or pretend a destructive schema rollback
restores it.

## Required backend verification

Add controller, service, mapper, and real PostgreSQL/Testcontainers tests for:

- filled, rejected, and legacy/partial reconstruction;
- missing quote, missing decision context, and missing timestamp states;
- denial across client/account scopes and non-disclosing foreign IDs;
- expired, revoked, wrong-actor, and policy-invalid contexts;
- scope-before-count/page checks and bounded filters;
- deterministic section pagination and changed-snapshot behavior;
- audit-store failure returning `503` and not returning data;
- export idempotency/recovery, tamper/digest mismatch, expiry and separate download
  permission;
- permission loss between summary, section, export, receipt, and download;
- source unavailability remaining `503`, never an empty success.

## Mandatory stop-and-proposal boundary

Stop and create `SharedChangeProposal/README.MD` before editing if implementation
requires a shared security/guard change, Operations scope policy change, core evidence
production/booking change, global error mapping, CORS/header policy, or transaction
schema mechanism. This frontend handoff intentionally leaves those decisions open.
