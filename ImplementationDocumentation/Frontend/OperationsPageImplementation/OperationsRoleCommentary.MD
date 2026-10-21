# Operations implementation commentary

## Purpose and approach

Deliver a scoped, read-only Operations investigation workspace around the existing order processor and financial journals. Preserve the Angular standalone-component architecture, shared workspace styling, Java service/mapper boundaries, exact decimals, and transactional booking behavior.

The supported outcome is to find a permitted order, explain its captured decisions, trace internal financial effects, inspect related integrity findings, and prepare a controlled evidence package. Instrument editing keeps its existing authority and immediate-save behavior. No order repair, forced execution, client impersonation, account adjustment, or grant administration is added to Operations.

## What changed, why, and how

| Work | Why | Implementation |
| --- | --- | --- |
| Repository and lifecycle review | New staff reads must respect the existing authentication, booking and evidence boundaries. | Reviewed the application structure, role routing, live sessions, order admission/execution, policy controls, reconciliation, database migration/grants, and delivery tests before adding endpoints. |
| Account scope and purpose | Staff membership alone must not expose account evidence. | Explicit per-client grants, separately approved account references, bounded purpose contexts, live identity/session checks, and shared SQL scope predicates. No global scope or administrator bypass. |
| Durable access history | Sensitive reads must remain attributable, including denied access. | Successful reads commit an access event before responding. Denials use a separate transaction. Audit persistence failure prevents disclosure. Grant changes and contexts have database immutability protections. |
| Monitor and exception queues | Operators need bounded discovery rather than arbitrary account browsing. | Exact references, instrument/status/side/stage filters, bounded UTC submission ranges, signed scope-bound cursors, rejected/deadline/retry presets, and independent scoped evidence pages. |
| Order investigation | A status label alone does not explain a financial result. | Explicit projections for lifecycle, decisions, quotes, attempts, reservations, fills, signed journal effects and retained notification facts; relationship-based completeness findings and honest legacy gaps. |
| Prospective decision context | Overall rejection codes do not prove which checks ran or what resources were observed. | Versioned immutable context stored with each retained decision. Pricing, account, banking-presence, resource, deadline and current-control checks retain actual pass/fail/skipped outcomes. Accepted policy, observed controls, exact balances/reservations and final override outcome are captured under the existing client lock. |
| Readiness and health | Configuration flags and unrestricted reconciliation reports are unsuitable health summaries. | Safe technical readiness, current schema/object requirements, effective evidence-permission probes, passive bounded provider observations and process-local worker failures. External notification delivery remains explicitly unconfigured. |
| Scoped reconciliation | Raw report references can expose unrelated accounts. | Immutable finding projections resolve authoritative ownership during each run and for supported historical references. Unknown ownership remains redacted. Operations reads fixed-run pages; Finance retains run authority. |
| Evidence packages | A downloadable file must not outlive the access that authorized it. | Bounded repeatable-read snapshots, compression outside the read transaction, final scope recheck, idempotent artifact creation, immutable ZIP bytes, per-file and archive SHA-256, requester/context/expiry checks, and audited download authorization. |
| Policy history | Existing policy changes were retained but not visible to investigators. | Paginated allowlisted history beside the existing editor, preserving revision conflicts and immediate-save semantics. |
| Client-consistent presentation | Operations should feel like another workspace in the same application. | Shared authenticated shell, workspace headings, panels, form controls, square buttons, table wrappers, status labels, notices, pagination and request-state/loading components. Only token-based evidence detail styles were added. |
| Session and stale-data handling | Prior-user evidence must not remain after identity or purpose changes. | In-memory contexts, cancellation and clearing on session/context changes, rejection of late context responses, transient-only stale retention, and polling that preserves later evidence pages. |
| Deployment and handover | Fresh and upgraded installations need the same schema and safe defaults. | Migration 011, matching fresh schema/reference marker, restricted runtime grants, explicit disabled configuration in Compose/environment template, API documentation, usage guide, incident runbook and recovery verification in CI. |

## Verification results

Completed on October 18–19, 2026 against local disposable fixtures. No retained deployment was migrated or granted access.

| Verification | Result |
| --- | --- |
| Full backend suite | 381 tests passed; zero failures, errors or skips. Rerun after adding resource/control traces and evidence rollback assertions. |
| Additional provider-health tests | 2 tests passed: absent/stale observations, transport recovery, safe failure categories, counters and no provider-message disclosure. |
| Database integration suite | 19 tests passed; fresh/upgrade schema parity and migration behavior included. |
| Frontend suite | 158 tests across 26 files passed, including role routing, context expiry/revocation, authenticated downloads and rejection of late context responses after session changes. |
| Production frontend build | Passed, including the final default-filter correction found during browser review. |
| Shared-style check | Passed: circular loading spinner, square corners elsewhere, and no CSS gradients. |
| Fresh Compose startup and populated upgrade | Passed using an isolated project, including preserved orders/journals and no repeated demo credit or policy reset. |
| Hardened Compose contract | Passed, including explicit disabled Operations access/context/export defaults and separate runtime identity. |
| Restart and backup/restore rehearsal | Passed. Fixture dump/restore measured 1.295 seconds; no loss relative to the captured snapshot; evidence ZIP bytes identical after restore. This is a fixture measurement, not a production recovery commitment. |
| Restricted runtime rehearsal | Scoped reads, export generation and normal booking passed; protected evidence mutation attempts were denied. |
| Export verifier | A real generated package passed archive/per-file verification; an intentionally incorrect expected archive digest was rejected. |
| API document | YAML parsed, schema references resolved, and paths were unique. |
| Formatting and script checks | Java/TypeScript formatting applied; Python scripts compiled; Git whitespace check passed. |
| Browser review | Verified sign-in, overview, purpose creation, permitted order monitor, investigation details, readable rule outcomes and export-ready receipt. Shared layouts were visually inspected. Browser console showed no errors at that point. The subsequent browser download-event check timed out; completed browser file transfer is not claimed. Actual download bytes were verified through integration/recovery tests. |

The final small frontend correction initializes empty status/side/stage selectors to display their existing “All” options. It changes no server query semantics. The production build was rerun afterward.

### Reproduce the principal checks

```sh
mvn -B -f backend/pom.xml test
mvn -B -f testing/pom.xml test
cd frontend
npm test -- --watch=false
npm run test:qof-styles
npm run build
cd ..
mvn -B -f backend/pom.xml -DskipTests package
python3 scripts/test-client-compose.py
python3 scripts/test-hardened-config.py
python3 scripts/test-client-recovery.py
```

The local Docker daemon required `-Dapi.version=1.44 -DargLine=-Dapi.version=1.44` for the cached Testcontainers client. Those command-line compatibility options are environment-specific. Packaging used the existing cached dependency repository; no dependency upgrade was introduced.

Failures found during development were corrected before the passing runs: fresh-schema search-path initialization, explicit mapper parameter binding, UUID/byte-array result mapping, and policy-history HTTP expectations in editor tests. The browser review also found the blank filter labels noted above.

## Deployment boundaries and remaining decisions

Sensitive account reads and export generation remain disabled until an authorized deployment process configures approved purposes, duration/version policy and explicit account grants. Apply migration 011 and runtime grants before activation. The guide explains each setting and the grant/revocation mechanism without depending on source planning documents.

Existing historical evidence is not rewritten. Pre-admission malformed/transport-failed submissions do not become synthetic orders. Current balances are never presented as historical balances. Quote metadata and transaction details are limited to retained allowlisted facts.

Provider/worker telemetry is process-local. There is no active probe, cross-instance health aggregate, external alert delivery, independent analytics store, automatic retention purge, legal-hold workflow, independently anchored signature, policy approval workflow, or additional market/currency execution in this release. Missing-evidence detection is per investigation; no expensive all-history completeness scan is introduced as a queue. Production load/latency budgets, retention rules, staff authentication policy, quote reproduction rights and recovery objectives still require deployment decisions and validation.

## Handover

- `OperationsRoleCrashCourse.MD`: features, workflows, configuration, exact evidence meaning, debugging map and technical flow.
- `docs/operations-runbook.md`: operational incident and recovery procedures.
- `docs/api.yaml`: request/response contracts and authorization requirements.
- `scripts/verify-operations-export.py`: offline fixed-entry ZIP integrity verification.

The temporary browser-review containers were removed after verification. Test scripts cleaned up their own isolated resources.

## Local demo follow-up — October 19, 2026

A running demo reported unavailable investigation access, an empty catalog and an apparently inactive symbol-test button. Read-only inspection found an incomplete purpose policy (no purposes/version and zero lifetimes), no account grants, and failed provider checks for all fourteen startup symbols. An authenticated SPY validation returned 503. The public documented provider host was reachable without credentials, while the application's quote path failed; the exact upstream cause was not established.

Added `docker-compose.operations-demo.yml` with explicit local policy settings and a separately ordered demo grant seed. It grants only the identified demo client, is repeatable, and respects revoked fixture grants. Added a separately selected `docker-compose.market-fixture.yml` for clearly identified synthetic quote/chart data. Existing deployments keep their original provider and access defaults. These overlays do not reset the database or refill spent demo cash.

Moved symbol-test progress/errors next to the symbol button and made the empty catalog explain its startup-provider dependency. A regression test covers the formerly hidden error and cancellation clearing. The crash course now includes complete Compose commands, sign-ins, ticker expectations, order creation, purpose capture, export and reconciliation steps, plus the real-provider alternative.

Automatic approval review rejected an external diagnostic that would have extracted the configured provider key. Continued using the existing authenticated application API and credential-free DNS/HTTP checks; no key was exposed. Live provider validation remains unresolved. The fixture workflow is an explicit local test option.

Follow-up verification passed: 159 frontend tests across 26 files, production frontend build, shared-style check, hardened Compose contract and the new isolated `scripts/test-operations-demo.py` rehearsal. The rehearsal loaded fourteen policies through the explicitly selected market fixture, validated SPY quote/chart responses, verified four idempotent grants scoped to the demo client, created a purpose context, investigated a filled order, generated an export and confirmed that rerunning the seed respected a revoked permission. It removed its own containers/network/volume. The retained running demo was inspected read-only and was not switched to synthetic market data or provisioned with grants by this work.

## Documentation and standard startup follow-up — October 19, 2026

Expanded the crash course with a component-by-component message dictionary: exact message/status, plain-language meaning and recovery steps. It distinguishes normal purpose guidance from a missing policy, provider outage from an unsupported symbol, stale data from access denial, and stored reconciliation outcomes from HTTP failures. Added concrete values and detailed explanations for all five scoped Operations environment attributes, including code format, seconds/ranges, policy-version changes, expiry versus retention, and account grants versus purpose capture.

Updated the standard local Compose configuration and environment template to provide usable demo purpose settings. The existing demo seed now runs a small readable shell entry point that creates roles first and conditionally applies the existing scoped Operations fixture. Local startup uses ordinary build/up without an Operations overlay or manual SQL. The backend now receives SERVER_PORT explicitly. The optional Operations overlay remains compatible; the market fixture remains opt-in and the real provider remains standard. Hardened startup still excludes the seed and chooses explicit deployment policy values.

Updated only the five requested settings in the retained local .env to the documented example values without exposing or changing provider credentials. The disposable standard-startup test passed with separate build/up commands, automatic account grants, fourteen fixture-validated tickers, SPY validation, purpose context, filled-order investigation/export and preserved revocation. The shared test provider was selected explicitly to verify behavior independently of the external provider. The hardened Compose contract and shell/Python syntax checks passed.

Final verification: the standard fresh/repeated-startup and populated-volume upgrade rehearsal passed, preserving account balances, orders, journals and operator policy changes. Ran the user's current project with ordinary `docker compose build` and `docker compose up -d`; both completed. Verified the current local demo through its existing authenticated API: configured `ORDER_REVIEW` policy, successful purpose creation and HTTP 200 scoped order monitor. The seed added only the intended local demo permissions. No database reset or provider replacement occurred. The live provider remains an independent dependency; startup does not synthesize successful quotes when it fails. The frontend image also rebuilt successfully as part of the standard project build.
