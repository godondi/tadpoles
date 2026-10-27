# Functional Review and Requirement Coverage

## Scope and approach

I reviewed the application code, the materials under `..`, the database and API documentation, and the available test and Compose workflows. There is no `Docs/` directory in this checkout. I exercised the role flows through Angular, service, controller, PostgreSQL, migration, and isolated Compose tests. This is a code and test review, not a production deployment or live brokerage certification.

The assessed roles are Client, Compliance, Operations, Analyst, Finance, and Advisor. The review excludes requested market and cash categories that are outside the agreed scope.

## Role workflow findings

| Role or journey | Implemented behavior | Result |
|---|---|---|
| Client sign-up and onboarding | Public client registration, sign-in, required onboarding profile, completion gating, and retryable validation/save states. Onboarding gates trading until the server confirms completion. | Pass in frontend and backend tests. |
| Client account and trading | Account-scoped cash and positions, market catalog, quote and candle views, pre-trade preview, buy/sell submission, idempotency recovery, order history, and detailed outcome evidence. The preview labels estimated cash/quantity impact; the server rechecks eligibility and quote state before booking. | Pass for internal simulated booking. No real exchange/broker execution is present. |
| Compliance | Purpose-scoped investigation and review flows, evidence timelines, access checks, paginated evidence, controlled export manifests, and private decisions. | Pass in service, HTTP, database, and Angular tests. |
| Operations | Order search and evidence, case triage, scoped evidence exports, instrument policy controls, quote/chart inspection, and authorization grants with revocation. | Pass in backend and isolated Operations Compose journey. |
| Analyst | Separate reporting API and database, analyst authorization, date/filter/measure views, freshness and definition labels, and explicit unavailable/demo states. | Partial: service and UI are present and fail closed, but the reporting database has no committed business-event ingestion path. Current data is demonstration-only or unavailable, so authoritative historical insight is not delivered. |
| Finance | Summary, reconciliation, exception handling, and controlled posting/reconciliation flows with date scope, source metadata, and guarded writes. | Pass in frontend and backend tests for implemented internal ledger flows. Accounting definitions and external settlement are outside the demonstrated behavior. |
| Advisor | Assigned-client list and detail, portfolio context, versioned guidance and recommendations, conflict handling, archiving, and eligibility checks. Advisor actions do not place client orders. | Pass in Advisor service, PostgreSQL, API, and Angular tests. This is an additional role capability, not a substitute for client trading. |

## Requirement coverage

| Requirement area | Status | Evidence and notes |
|---|---|---|
| Registration, sign-in, and account ownership | Met for the local application flow | Registration and onboarding tests cover valid, invalid, duplicate, and retry cases. Protected APIs derive the client from the authenticated identity; role and cross-account authorization checks are exercised. Production-grade external identity provisioning is not demonstrated. |
| Buy/sell intake and rule checks | Met for internal simulation | Tests cover preview, instrument/session/account eligibility, available cash or units, rejection, and idempotent request recovery. |
| Durable order intent and outcome | Met for internal booking | PostgreSQL integration tests cover lifecycle outcomes, quote capture, atomic cash/position/fill booking, duplicate handling, and evidence retention. A booked internal fill is not an exchange settlement. |
| Current client portfolio and chronological order history | Met for the implemented ledger | Client views read server-backed account state and order evidence. The account-opening/demo setup is controlled and repeatable; client funding is a modeled internal workflow. |
| Market data and indicative pricing | Met for configured synthetic/provider-backed catalog behavior | Quote and candle services, freshness/error handling, the instrument catalog, and policy controls are tested. The project does not demonstrate a live broker or exchange execution connection. |
| Permanent evidence and lifecycle reconstruction | Met for internal records | Compliance and Operations journeys verify scoped timelines, quote and booking evidence, integrity metadata, and exports. Some legacy records can be explicitly partial where original facts do not exist. |
| Reporting isolation and business insight | Partially met | The Analytics service is separately deployed and queries a separate reporting database. The user interface and API expose freshness, definitions, filters, and unavailable states. There is no production event/ETL loader from committed trades, so totals cannot yet be reconciled to live business activity. |
| Pre-trade impact capability | Met for the current scope | The ticket previews available and projected cash/quantity, fee estimate, quote expiry, and eligibility before submission. Values are estimates and the execution path remains authoritative. |
| Role separation | Met for implemented workspaces | Route guards and server-side role checks cover the six reviewed roles. Client identity and data scope are enforced in service/controller tests, not just hidden navigation. |

## Fixes made during this review

1. **Analyst freshness layout:** added visible separators between freshness fields, left-aligned values beneath their labels, and retained a two-column layout at narrower widths. This addresses the alignment and border problem shown in the supplied screenshot.
2. **Trading PostgreSQL test schema:** updated the older integration setup to apply migrations 018 and 019. It had stopped at migration 017, so the updated instrument catalog query referenced a missing `exchange` column. The database fixture now follows the current migration chain.
3. **Isolated Compose test overrides:** excluded `analytics-demo-seed` whenever the reporting database is excluded in both the Client and Operations Compose journeys. Previously Compose rejected these test projects because the seed job still depended on the disabled database service.

I did not remove any API endpoints: the review did not establish that a remaining endpoint is unused or deprecated, and removing an uncertain contract would risk breaking clients. No production reporting pipeline was added because event semantics, approved metric definitions, and freshness policy are not supplied; returning fabricated or unverified historical totals would be misleading.

## Verification results

| Check | Result |
|---|---|
| Frontend suite | 356 tests across 54 files passed. |
| Backend suite | 486 tests passed, including Advisor, Compliance, Finance, onboarding, authorization, and trading PostgreSQL journeys. |
| Database integration suite | 19 tests passed, covering migrations, schema constraints, and business-flow queries. |
| Analytics API suite | 8 tests passed in an isolated Python container. |
| Operations Compose journey | Passed: isolated startup, scoped cases and order evidence, policy changes, export, and grant revocation. |
| Client Compose journey | Passed: isolated startup, account and cash flow, restart idempotency, and populated database upgrade. |
| Frontend style check and production build | Passed. The build reports an Analytics component-style budget warning (4.50 kB vs 4.00 kB); compilation succeeds. |

The first backend integration run exposed the stale migration fixture described above; after updating it, the full backend and database suites passed. The first attempts at the two Compose journeys exposed the test-override dependency issue; both journeys passed after correcting the overrides.

## Remaining work

The main unmet behavior is authoritative Analyst reporting. Add a transactional outbox or equivalent committed-event source, an idempotent reporting loader, approved metric definitions, reconciliation checks, and freshness policy before treating Analytics results as business reporting. Until then, the existing unavailable/demo labeling is the correct behavior. Live execution, production identity operations, and financial settlement also need deployment and business decisions beyond this repository’s demonstrated local flows.
