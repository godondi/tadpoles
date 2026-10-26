# Compliance and Operations implementation execution plan

Date: 2026-10-23

Source baseline: `9c4b959d720bfb4984b22a7371f803dfbd5b2b14`

Status: implementation and scoped acceptance complete on 2026-10-23. Final observed results and unrelated baseline exceptions are recorded in section 13; production rollout still requires independently approved policies/grants.

## 1. Scope, authority and resume instructions

This is an independent execution plan requested before implementation. It does not replace or modify [ComplianceOperationsRefactor.MD](ComplianceOperationsRefactor.MD) or [ComplianceOperationsRefactorPlan.MD](ComplianceOperationsRefactorPlan.MD). The supplied documents were already untracked at discovery; preserve them as user-provided inputs.

The user explicitly authorized implementation after the planning delivery. The earlier documentation-only ambiguity is resolved. Execute the application refactor while retaining the supplied documents as the original brief; this plan records implementation decisions and observed results.

Once application implementation is confirmed, execute the full target described here unless a narrower package is explicitly selected. Do not treat historical documentation completion checkboxes as application progress.

To resume with little prior context:

1. Read this plan and sections 5-13 of the main refactor document. Inspect current HEAD, worktree changes and migration filenames; this baseline may have moved.
2. Identify the selected work package and its owner. Read its source map, prerequisites, reserved files, acceptance criteria and the change-analysis records below.
3. Complete W0's contract decisions and F1 compatibility analysis before dependent schema/feature work. Do not infer production policy from demo fixtures.
4. Before any larger change, stop editing, complete the analysis template in section 4 and update the affected ownership/dependency entries here.
5. Add a failing behavioral regression before correcting each F1-F8 defect. Record actual results, not historical test totals.
6. Implement only the owned write set. Send shared-file integration requests to the designated owner rather than editing another package's files.
7. Rebase onto the published prerequisite commits, run focused checks, and merge in the integration order in section 10.

The original planning delivery created no implementation branches, deployments or database changes. The subsequent implementation follows the package ownership below; shared contracts/schema/routes were integrated centrally. No large agent workflow was used. Baseline observations and proposed contracts in earlier sections are historical analysis; current contracts are in `../../../docs/api.yaml` and observed completion is in section 13.

## 2. Source-grounded system context

### Architecture and reading map

All paths below exist at the reviewed baseline. Proposed paths later in this plan are explicitly identified and are not existing capabilities.

| Area | Authoritative source and relevant guidance | Implementation implication |
| --- | --- | --- |
| Application structure | [root README](../../../README.md), [frontend README](../../../frontend/README.md), [backend POM](../../../backend/pom.xml), [Angular configuration](../../../frontend/angular.json), [frontend manifest](../../../frontend/package.json) | Angular standalone components/signals/RxJS; Spring services and MyBatis; PostgreSQL. Container guides use Java 21, Node 22 and PostgreSQL 15; the backend POM currently targets Java 17. Do not mix a toolchain upgrade into this refactor. |
| Roles, routes, shell | [routes](../../../frontend/src/app/app.routes.ts), [workspace policy](../../../frontend/src/app/core/auth/workspace-policy.ts), [shell](../../../frontend/src/app/layout/authenticated-shell.component.ts) | Both roles already share an authenticated shell. Route registration alone is insufficient: `routeRole`, navigation and return-URL rules must also recognize new routes. |
| Existing managed Support | [managed support guide](../OperationsPageImplementation/OperationsRefactorCrashCourse.MD), [case service](../../../backend/src/main/java/com/neueda/leap/service/SupportCaseService.java), [case mapper](../../../backend/src/main/java/com/neueda/leap/mapper/SupportCaseMapper.java), [client controller](../../../backend/src/main/java/com/neueda/leap/controller/SupportCaseController.java), [staff controller](../../../backend/src/main/java/com/neueda/leap/controller/OperationsCaseController.java) | Existing public projection, expected versions, scoped SQL, immutable events, recovery receipts and audit transactions must survive the refactor. Support currently uses explicit allowlisted maps/`JsonNode`, not an existing `SupportDtos.java`. |
| Support browser state | [case page](../../../frontend/src/app/features/client/support-page.component.ts), [Support API](../../../frontend/src/app/core/api/support-api.service.ts), [updates component](../../../frontend/src/app/features/client/support-updates.component.ts), [page resource](../../../frontend/src/app/shared/workspace/page-resource.ts) | Client and Operations currently share one large route-driven component. Keep recovery keys actor-scoped, prose out of storage, public/private histories separate, and identity/denial clearing intact during extraction. |
| Client entry points | [account](../../../frontend/src/app/features/client/account-page.component.ts), [instrument](../../../frontend/src/app/features/client/instrument-page.component.ts), [order receipt](../../../frontend/src/app/features/client/order-detail.component.ts) | Order entry preserves `orderId`; category-only account/instrument links lose exception/instrument/side context. One integration owner must combine these changes. |
| Operations evidence | [access service](../../../backend/src/main/java/com/neueda/leap/service/OperationsAccessService.java), [order service](../../../backend/src/main/java/com/neueda/leap/service/OperationsOrderService.java), [reconciliation service](../../../backend/src/main/java/com/neueda/leap/service/OperationsReconciliationService.java), [migration reader](../../../backend/src/main/java/com/neueda/leap/mapper/OperationsReconciliationMapper.java), [export association](../../../backend/src/main/java/com/neueda/leap/service/SupportExportService.java) | Case access does not authorize evidence. Managed contexts already narrow to a real case account/optional order. Migration list currently returns reference/kind/time, not a complete historical diagnostic detail contract. |
| Compliance backend | [controller](../../../backend/src/main/java/com/neueda/leap/controller/ComplianceController.java), [DTOs](../../../backend/src/main/java/com/neueda/leap/dto/ComplianceDtos.java), [access](../../../backend/src/main/java/com/neueda/leap/service/ComplianceAccessService.java), [evidence](../../../backend/src/main/java/com/neueda/leap/service/ComplianceEvidenceService.java), [mapper](../../../backend/src/main/java/com/neueda/leap/mapper/ComplianceEvidenceMapper.java), [SQL](../../../backend/src/main/java/com/neueda/leap/mapper/ComplianceEvidenceSql.java), [exports](../../../backend/src/main/java/com/neueda/leap/service/ComplianceExportService.java) | Implemented order-evidence workspace, not a Support inbox. Independent grants, purpose policy, expiring actor contexts, grant-scope fingerprints, access auditing and server archives already exist. |
| Compliance browser | [adapter](../../../frontend/src/app/features/compliance/compliance-api.service.ts), [models](../../../frontend/src/app/features/compliance/compliance.models.ts), [resource](../../../frontend/src/app/features/compliance/compliance-resource.ts), [search](../../../frontend/src/app/features/compliance/investigations-page.component.ts), [detail](../../../frontend/src/app/features/compliance/investigation-detail.component.ts), [exports](../../../frontend/src/app/features/compliance/evidence-page.component.ts) | Fix existing search/export/race behavior before adding referral screens or rewriting the same templates. |
| Financial boundary | [trading service](../../../backend/src/main/java/com/neueda/leap/service/impl/TradingServiceImpl.java), [transactions](../../../backend/src/main/java/com/neueda/leap/trading/TradingTransactions.java), [rules](../../../backend/src/main/java/com/neueda/leap/trading/TradingRules.java), [banking](../../../backend/src/main/java/com/neueda/leap/service/impl/BankingServiceImpl.java), [policy editor service](../../../backend/src/main/java/com/neueda/leap/service/impl/InstrumentPolicyServiceImpl.java), [USD trading guide](../ClientPageImplementation/ClientRefactorCrashCourse.MD) | Provider quotes support internal USD booking. Support must not mutate orders, holds, jobs, fills, journals, balances, eligibility or bank records. Existing policy changes remain a separate deliberate audited action. |
| Schema and deployment | [database guide](../../../database/README.md), [fresh schema](../../../database/fresh/001_schema.sql), [reference markers](../../../database/fresh/002_reference.sql), [012](../../../database/migrations/012_managed_support.sql), [013](../../../database/migrations/013_compliance_workspace.sql), [014](../../../database/migrations/014_compliance_context_scope.sql), [runner](../../../database/migrate.sh), [runtime grants](../../../database/runtime-grants.sql), [local fixture seed](../../../database/demo/seed-role-workspaces.sh) | Highest migration is 014. Runner uses version markers, not checksums, under one advisory lock. Fresh schema itself inserts 13/14; reference SQL separately records 3-12. Fresh-only success cannot validate retained upgrades. |
| Presentation | [global CSS](../../../frontend/src/styles.css), [top navigation CSS](../../../frontend/src/app/shared/top-nav/top-nav.component.css), [Operations evidence table](../../../frontend/src/app/features/operations/evidence-table.component.ts), [style checker](../../../frontend/scripts/check-qof-styles.mjs) | Reuse established tokens, square controls and shared layout. Local Compliance layouts compete with global styles. Only measured shell defects justify shell changes. |
| Contracts and errors | [OpenAPI](../../../docs/api.yaml), [error handler](../../../backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java), [error record](../../../backend/src/main/java/com/neueda/leap/exception/ApiErrorResponse.java), [response header filter](../../../backend/src/main/java/com/neueda/leap/config/OperationsResponseHeaders.java) | Error envelope already has `code` and `correlationId`, but ordinary status exceptions become generic `HTTP_<status>`. Add typed reasons without breaking unrelated consumers. Compliance controller sets no-store for successful responses; error-path coverage needs explicit verification. |
| Operational guidance | [Operations guide](../OperationsPageImplementation/OperationsRoleCrashCourse.MD), [runbook](../../../docs/operations-runbook.md), [Compliance backend handoff](ComplianceRoleBackendImplementation.MD), [Compliance frontend guide](ComplianceRoleFrontend_CrashCourse.MD), [analytics guide](../../../analytics-service/README.md) | Source takes precedence over obsolete proposed/unavailable wording. Analytics remains a separate reporting boundary and is not a source of ticket evidence. |

This review concentrated on affected source, contracts, relevant documentation and test infrastructure. It is not an exhaustive audit of unrelated legacy CRUD, nor a runtime reproduction, browser review or production schema inventory.

### Invariants every package must preserve

| Boundary | Required behavior |
| --- | --- |
| Identity and permission | Operations requires live `TRADING_OPS` and excludes `CLIENT`; Compliance remains `COMPLIANCE` with independent account grants. Assignment and knowledge of a reference never grant access. Services recheck identity and object scope. |
| Case identity | Preserve original owner, category, subject and order link. `guard_support_case` permits only public status/version/time changes; do not relax it for internal workflow. |
| Public history | States remain `OPEN`, `IN_REVIEW`, `AWAITING_CLIENT`, `RESOLVED`. Public version/time/feed/unread only advance through explicit public events. Private changes must not change public JSON, totals, ordering or unread state. |
| Existing transitions | OPEN allows review/awaiting/resolved; IN_REVIEW allows awaiting/resolved; AWAITING_CLIENT allows review/resolved; resolved clients explicitly reopen to IN_REVIEW. An awaiting-client reply returns to review. Staff notes do not reopen cases. |
| Command recovery | Authenticate and recheck current scope before returning a receipt. Under the appropriate lock, recover a same-key/same-payload receipt before comparing old expected versions. Changed payload conflicts. An unknown outcome or receipt lookup 404 does not justify replacing the key. |
| Financial truth | Exact decimals stay strings in browser contracts. Accepted/executing is not filled; internal fill is not external settlement; current quotes/controls are not historical evidence; unavailable evidence is not a passed check. |
| Evidence and exports | Independent role contexts/permissions remain required. Server archives, requester checks, expiry, byte digests and immutable associations remain authoritative. Do not manufacture exports from displayed pages. |
| Availability and audit | Fail closed on unavailable required access/audit storage; errors are not successful empty pages. Denial clears protected data. Transport-stale retention is allowed only within the same still-authorized identity/context/resource. |
| Compatibility | Old create payloads and existing cases remain readable/replyable. Do not invent intake, assignees, completed tasks, deadlines or historical evidence. Newly required intake is enabled only through a documented versioned rollout. |

## 3. Change inventory and verification gaps

These are source observations; the required regression reproductions have not been run in this planning delivery.

| ID | Evidence, exact expected change and owner | Required regression / release gate |
| --- | --- | --- |
| F1 | 013 declares non-null `scope_version` with a check; 014 unconditionally adds the column and another named check. W0-DB owns historical compatibility, fresh parity, migration markers and grants. | Actual runner: retained 012; historical 013 lacking column; current 013 containing column; already-applied 014; fresh/repeated run. Old null-snapshot contexts remain unusable. |
| F2 | `ComplianceEvidenceSql` orders events/attempts by nonexistent qualified `e.reference`. W1-B corrects to real deterministic columns/tie-breaks. | Real PostgreSQL mapper count/page queries for all eight sections, empty and multiple pages, then actual HTTP detail/export. |
| F3 | Search sends zone-less datetime-local values to `Instant.parse`; Operations concatenates `:00Z`. W1-U owns one validated UTC conversion helper and both consumers. | UTC semantics in non-UTC zones/DST, impossible dates, seconds precision, optional bounds, end-exclusive ordering, exactly 31 days and one unit beyond. |
| F4 | Compliance frontend/service omit actual self-directed `EXECUTING` and list unsupported current-engine states. W1-B/U align contract/validation/options with stored record kinds. | Actual ACCEPTED/EXECUTING/FILLED/REJECTED rows; explicitly supported LEGACY states. Do not rewrite records to fit a menu. |
| F5 | Search never restores detail back-link filters and paging rebuilds from draft controls. W1-U separates validated applied state from draft state. | Apply, edit without applying, next/back, refresh, malformed URL state, context/identity change. Preserve page and approved filters without exposing account references or purpose/prose in URLs. |
| F6 | Export adapter lacks receipt API; UI omits download/recovery, drops keys, clears all protected 409s, and creates a new key on busy re-entry. W1-B/U completes existing export journey and typed errors. | Busy double invocation makes one POST/key; lost response/reload replays original actor/order key; receipt then authorized download; conflict/limit errors do not revoke valid context. |
| F7 | Section/export subscriptions survive route/context changes and share global section busy/error state. W1-U cancels by resource generation and keeps independent per-section state. | Old order/context success and error arriving after replacement cannot change new data, clear new context, publish a receipt or trigger download. Different sections page independently. |
| F8 | Support offers IN_REVIEW while already in review and shows controls without per-case update entitlement. W3 publishes authorized actions; W4 renders them. | Every state/read-only case grant combination; no invalid transition offered; server still rejects crafted invalid/stale commands. |
| A1 | OpenAPI still calls the three Compliance paths availability-only and omits implemented context, section, export creation, receipt and download routes. W0 owns reconciliation before freezing new contracts. | Contract describes actual controller/DTO/header/status behavior, including unavailable/error paths, and distinguishes existing from proposed APIs. |
| A2 | `FreshTradingSchemaTest` upgrades only through 012 and expects 10 markers; fresh schema includes Compliance and markers 13/14. W0 owns updating parity coverage, not merely changing a count. | Compare columns/defaults/nullability/constraints/triggers/indexes/function bodies and the exact applied-version set across repaired supported upgrade paths. |
| A3 | `CompliancePostgresTest` starts its own container and only checks fresh DDL/immutability. Container-only role harness supplies an external DB but no Docker socket. W0/W6 coordinate test compatibility. | New real mapper/HTTP coverage must actually execute against PostgreSQL. Choose supported host Docker or adapt the dedicated isolated-test path without allowing retained DB targets. |
| A4 | Export authorization uses a shared metadata helper after operation-specific context checks; current unit tests do not establish the complete mixed-account permission matrix. W1-B must verify the exact operation on the export's account before exposing new controls. | Grant create/download on A and only read on B, then attempt B create/replay/download; cross-requester, revocation and expiry tests. If a bypass is reproduced, stop and extend the authorization change record before repairing. |
| A5 | Migration evidence reader exposes reference/kind/time, not source-record diagnostics; account and availability diagnostic staff contracts do not yet exist. W0/T2-T4 must design bounded read projections rather than calling client banking/profile APIs as staff. | Server ownership joins, independent permissions, redaction and unavailable reasons; no encrypted bank content, inferred legacy currency or financial writers. |

### F1 compatibility procedure: no shortcut migration

Before editing 013/014, W0-DB must inventory only approved read-only schema metadata: recorded versions, column type/nullability/default, constraint names/definitions, guard definitions and whether scope snapshots are null. Do not collect private case prose, grant approval text, credentials or production export bytes into this plan.

Reconstruct each supported historical variant in disposable PostgreSQL. Capture the runner's first failing statement. A later 015 cannot repair failure before 014 completes. Preserve applied version history and immutable context records; never mark a failed migration applied by hand or reset retained volumes.

The compatibility repair must explicitly specify which already-released script, if any, must change to allow previously blocked installations to progress. Adding `IF NOT EXISTS` alone does not establish matching type, nullability, constraints or fresh/upgrade parity. Do not assign today's grant fingerprint to old contexts: missing original scope must continue to fail authorization. If canonical normalization needs an additional migration, reserve its number centrally after the blocking-step strategy is approved. Already-marked versions will not rerun automatically.

## 4. Mandatory stop-and-analyze gate

A larger change includes schema/history edits, authorization widening, new diagnostics, new command/version semantics, workflow/referral lifecycle, public projection changes, component extraction, shared styling/shell changes, export recovery persistence, or cross-role integration. It is not an incidental follow-up edit.

When one is discovered, immediately stop related code edits. Read its callers, persistence guards, DTOs, grants, tests and deployment wiring. Add or amend a record here with:

```text
Change ID / package / owner / baseline:
Problem and source evidence (paths and symbols):
Current behavior and intended observable behavior:
Expected additions, modifications and explicitly untouched files:
API/DTO/action/error changes and public/private visibility:
Schema, constraints, indexes, grant and audit changes:
Concurrency, replay, lock order and unknown-outcome recovery:
Compatibility, existing-record behavior and policy decisions:
Dependencies, cross-owner integration patches and merge order:
Failing regression, focused checks and end-to-end acceptance:
Rollout, readiness, rollback and retained-history protection:
Decision status and evidence needed to resume:
```

Do not resume implementation until those entries are concrete and necessary approvals/inputs exist. If scope grows again, stop again; this is not a one-time approval for unrelated changes.

### Initial larger-change analysis records

| Record | Analysis and expected coordinated change set | Resume condition |
| --- | --- | --- |
| C1: retained schema repair | F1/A2 affect 013/014, fresh DDL/reference markers, structural parity tests, migration-runner rehearsal and database guide. Runtime grants must remain least-privileged. No table reset, invented context scope or financial data change. | W0-DB records variant inventory, canonical structure and tested compatibility strategy. |
| C2: existing Compliance correctness | F2-F7/A4 affect Compliance services/mappers/DTOs, adapter/resource/models/search/detail/exports/specs, plus the small Operations UTC consumer patch. W0 owns common error/OpenAPI changes. Export recovery stores only opaque actor/order/action/key metadata; route/context/identity generation gates every response and error. | W0 error/search/recovery contracts fixed; baseline behavioral failures captured. W1 merges before W5 touches these UI files. |
| C3: private workflow engine | New additive intake/workflow/task/resolution/event/receipt tables and bounded DTO/service/mapper/registry; thin hooks in existing case service/controllers/API; authorized actions and scoped queue filters. Public identity/history guard remains unchanged. Resolution must join existing public writes atomically rather than issue two HTTP commands. | W0 schema/permission/action/version contracts merged; lock order, all resolution entry points and legacy-case policy agreed. |
| C4: category diagnostics | T1 reads retained order evidence; T2 adds redacted account/movement diagnostics; T3 adds exception detail and retained disposition; T4 reads account/policy/session/provider/resource observations; T5 links authorized cases and triage referrals. Each reader must distinguish reported context/current observation/retained evidence and give a permission/unavailable reason. | W0 approves exact fields/permissions/ownership; W3/W4 extension interfaces published. No support-to-financial-writer coupling. |
| C5: tracked Compliance referral | New linked referral/investigation/event/export-association data and dedicated Compliance APIs/managed contexts/screens. Existing order-search URLs stay intact. Operations receives minimal acknowledgement/decision projection, never automatic private-note sharing; only explicit Operations publication reaches clients. | W0 defines transition/assignment/reassignment/read projection and grant policy; W3 exposes transactional referral and mandatory-resolution hooks. |
| C6: visual foundation/extraction | W2 owns global CSS and optional measured shell/top-nav changes; W4 owns shared Support extraction/Operations templates; W5 owns post-W1 Compliance composition. Data-only primitives cannot fetch or infer authorization. Route/sidebar/test integration stays with W0. | Baseline synthetic screenshots and failure-state reproduction captured, UI interfaces published, ownership exclusions enforced. |

### Execution decisions before code changes

Implementation owner is the integrating agent. A bounded W1 correctness package may be delegated with exclusive ownership of existing Compliance backend/frontend files and the UTC helper/Operations consumer; no delegated owner edits migrations, shared errors, routes, global CSS or this plan. Existing Compliance files are handed back before W5 context integration.

C1 compatibility decision: retain 013 unchanged; repair the blocking 014 to accept both supported 013 variants, verify the existing column is `varchar(64)`, normalize to nullable scope with one named `compliance_context_scope_version_check`, and preserve null historical snapshots as invalid contexts. Reserve 015 for the same normalization on installations that already recorded 014; reserve 016 for private workflow and 017 for referrals. Fresh DDL must have the same final shape and exact markers. No deployed database access is available; compatibility is established for source-derived disposable variants, not claimed as a production inventory.

C1 reproduction: the actual runner failed at 014 with `column "scope_version" ... already exists` on the current 013 source. Fresh/current upgrades passed after normalization. The older 013 adds the column at a different physical ordinal; parity compares columns by name and complete definition, not storage order. Rebuilding an immutable context table solely to change ordinal position would be destructive and unnecessary.

C2 contract decision: retain the existing error envelope. A typed status exception supplies a stable `code` through the shared handler; ordinary exceptions retain `HTTP_<status>`. Codes include `CONTEXT_INVALID`, `IDEMPOTENCY_CONFLICT`, `VERSION_CONFLICT`, `WORKFLOW_CONFLICT`, `EVIDENCE_LIMIT`, `ARCHIVE_LIMIT`, `WORKFLOW_INCOMPLETE`, and `EXPORT_EXPIRED`. Non-authorization conflicts must not revoke a valid context. Compliance exports require the operation's grant on the artifact's actual account, not merely a grant somewhere plus a different permission on that account. Browser recovery retains only actor/order/key/reference metadata in session storage.

C3/C4 contract decision: new workflow commands use `/api/operations/cases/{reference}/workflow/{assignment|classification|priority|next-action|tasks|resolution}` and `/workflow/requests/{key}`; reads use `/workbench`, `/assignees`, `/diagnostics` and a private paged `/exports` list. Require `expectedWorkflowVersion` and a stable key; resolution additionally requires `expectedVersion` and public explanation. Workflow is opt-in via `support.workflow-enabled` (default false outside the explicit local fixture). Old cases/payloads remain readable/replyable; enabled cases receive pending category tasks, not invented completion. New diagnostics permission is `ACCOUNT_DIAGNOSTICS`, never implied by case grants. Category handlers share one typed definition/registry and orchestrator. Intake schema v1 is optional for old payloads, rejects unknown fields and verifies owned order/movement/exception references.

C5 contract decision: new Compliance permissions are `INVESTIGATION_READ` and `INVESTIGATION_UPDATE`. Referral status transitions are QUEUED -> IN_REVIEW; IN_REVIEW -> NEEDS_INFORMATION or DECIDED; NEEDS_INFORMATION -> IN_REVIEW; DECIDED -> CLOSED or IN_REVIEW; CLOSED -> IN_REVIEW. Decisions require reviewer, reason and disposition; reopen/revise appends history. Referrals require an Operations owner and eligible independently scoped reviewer. All created Compliance referrals are mandatory until DECIDED/CLOSED with a recorded disposition. Assignment never grants scope; reassignment invalidates prior managed contexts by binding the reviewer/version. Operations receives only referral reference/status/next owner/disposition/client-safe proposal. Public publication remains a separate explicit Operations action.

C6 implementation boundary: first apply container-safe semantic layout classes and complete focus/table/action styling without changing shell offsets. Keep role adapters separate. Any shell/breakpoint change requires actual browser evidence. Category-specific components remain disjoint; shared category registration is integrated once.

### C7: workflow/referral transaction and diagnostic implementation analysis

Implementation owner: parent; existing Compliance files remain W1-owned until handoff.

The public Support service's package-private identity, audit and event helpers are authoritative. New `SupportWorkflowService` lives alongside it in `service/`, not the handler subpackage, to reuse that boundary without a circular dependency. `SupportCaseService` injects only the new mapper and category registry for intake/initialization/reopening. Public summaries remain an explicit unchanged allowlist. Private reads contain immutable intake, versioned checklist, authorized actions, eligibility, bounded private history/referrals/export metadata, and an explicit unconfigured response target. Original category remains public and immutable; classification changes only the effective category/revision.

Every writer locks client -> public case -> workflow -> investigation. Live CASE_UPDATE and CASE_READ are checked independently before receipt replay. Requests are actor-scoped, canonical-payload hashed, immutable, and replayed before stale-version rejection. Resolution is one transaction: check private/public versions, current eligible owner, category task/disposition requirements and pending mandatory referrals; append public event and private resolution snapshot; advance both projections; persist receipt and audit. Direct legacy RESOLVED commands are rejected only when rollout is enabled. Client reopening creates a fresh private checklist revision and event without inventing completions.

`SupportWorkflowMapper` owns safe projections. Account diagnostics expose identity/onboarding/bank-presence booleans, string decimal balances/positions and bounded pending-order/movement facts under ACCOUNT_DIAGNOSTICS, never profiles/bank snapshots. Historical reads select known provenance fields, never arbitrary source JSON, under RECONCILIATION_READ. Instrument policy/provider observations are current diagnostics only, never a quote test or proof of historical eligibility. Evidence-bearing tasks require separately authorized owned references; unavailable states retain reasons and cannot imply verified policy change or repaired financial outcomes.

Migration 017 adds independent account-scoped INVESTIGATION_READ/UPDATE, verified immutable source identity, append-only lifecycle/request/export records, projection guards, mandatory referral links and managed-context binding columns. Referral creation requires an eligible Operations owner and independently eligible Compliance reviewer. Both roles share only minimal handoff; proposed client prose remains private until an explicit public Support command. Managed contexts must revalidate reviewer and investigation/account/order binding across existing Compliance readers/exporters after W1 handoff. Account-only referrals do not fabricate an order export.

Expected integration edits: new mapper/service/controller and focused PostgreSQL tests; Support create/reopen/detail/legacy transitions; rollout config/readiness; fresh schema/upgrade markers/runtime grants; local opt-in fixture; typed frontend API/workbench and category intake. No changes to financial writers, historical facts, production grant assignments, public states or supplied task documents. Before enabling, verify real PostgreSQL HTTP creation/replay/version conflicts/private invariance/audit rollback and unchanged financial rows. The actual runner now passes all five 015 variants; complete-chain parity must be repeated after 016/017 snapshot/grant integration.

### C8: frontend composition and W1 handoff

W1 ownership returned with 21 passing Java21/PostgreSQL tests and 40 passing Node22 tests in America/New_York; exact commands and reproduction evidence are in the session `w1-handoff.txt`. Parent now owns the existing Compliance integration surfaces. Preserve W1's generation guards, per-section state, typed errors, UTC helper, safe applied filters and original-key recovery.

Use an additive data-driven category intake component and a separate Operations workbench component rather than rewriting the proven public conversation/recovery state machine. The intake policy comes from the authenticated server; optional versioned fields are sent only when explicitly enabled. Query-string entry hints are untrusted and server ownership-checked; they are not grants. Workbench forms use server-authorized actions and category templates. A shared private-command recovery helper persists only actor/resource/action/key and blocks replacement on an unknown outcome. Payload prose, diagnostic bodies and context tokens remain memory-only. All new component reads/writes cancel on route/identity/context replacement; denial clears protected data.

Register Review cases separately from order investigations in routes/navigation/return-url policy. Compliance case actions remain private; a managed context is an explicit distinct mode and is cleared when review version/reviewer changes. Shared CSS handles wrapping headings/actions, min-width:0 nested grids, long references/prose, labelled table scroll areas and visible textarea/summary focus; retain existing shell structure and offset behavior unless measured otherwise. No attachments, hidden policy edits, automated deadlines or financial mutation controls are introduced.

### C9: integrated scope/pagination regression observations

The managed-context test uses 25 genuine self-directed orders submitted through `TradingService` with deterministic injected quotes, provider-account fixtures and a saved synthetic bank; financial guards are never disabled. An attempted direct legacy seed correctly failed under `guard_legacy_financial_write` and was replaced by the real command path. The integrated search regression also exposed two coupled pagination defects beyond W1's small fixtures: absent page/pageSize had no defaults, and SQL limit/offset were not populated. Preserve explicit invalid-input rejection; default missing parameters to 0/25 and derive SQL bounds from validated applied values. The test asserts actual item count versus total, not only mapper invocation.

Managed context source/version/reviewer binds every order/count/section/finding and export history/receipt/replay/download path. Managed exports auto-create immutable review associations; another investigation's or external-context artifact cannot be accessed through that managed context. Reassignment or any retained review-version change invalidates old contexts, including a reassignment away and back.

Current observed gates: complete-chain five-variant actual runner and standalone fresh/upgrade parity passed through 017 before the final managed-version column addition; 38 existing affected frontend tests and 10 new intake/workbench/recovery tests passed; five initial workflow HTTP tests plus audit rollback pass. Repeat schema and integration gates after remaining wiring. This is not yet application completion or browser/visual proof.

### C10: queue visibility and disabled-rollout preservation

The original staff list intentionally used public summaries and creation order. The enabled workflow needs private ownership/priority/next action visibility without adding private keys to client summaries. Add a separate scoped SQL queue projection/count in `SupportWorkflowMapper` and route Operations list reads to it only when enabled. Filter live account scope and optional assignee/priority/effective category before counts/pages; order explicit priority, due time, creation/reference. The client endpoint and disabled legacy list keep their original projection/order. The shared case screen adds staff-only assignment/priority controls and columns; no client receives private metadata. Catalog refresh must preserve the same applied queue selectors.

Final queue integration also returns a staff-only `workflowEnabled` flag so disabled rollout hides private selectors/columns rather than inviting unavailable queries. Add the already-defined effective-category selector and a literal `overdueOnly` boolean: only non-RESOLVED cases whose explicit recorded due time is strictly before the request's server clock, not an inferred SLA. Existing public status selectors provide explicit OPEN/IN_REVIEW attention views. No timer/reminder policy is invented. Both list and count share the same scoped predicates; false/omitted preserves the default queue.

Disabling rollout must not permit the old direct RESOLVED route to bypass retained new intake, private commands or mandatory referrals. An existing legacy case with only initialization/reopen history remains compatible; a case with structured intake or workflow request history requires the gated atomic-resolution path even after disabling new controls. Persist all evidence and show a typed unavailable/incomplete outcome rather than silently downgrade its protection.

### C11: final evidence-library, unavailable-state and restricted-runtime integration

Owner: integrating agent after W1 handoff. The workbench currently has a protected metadata endpoint but no library consumer; the new diagnostics selector defaults to a permission that may not be available. Existing public controls hide buttons but still expose a composer to read-only staff. The Compliance detail still puts raw policy JSON before structured facts. These are incomplete integration surfaces, not permission changes.

Add a typed private export-metadata page to the workflow adapter/workbench, including source order, digest, requester, association/expiry and explicit metadata-only limitations. Do not manufacture a download grant or reuse Compliance export IDs in Operations. Retain role-specific existing protected download screens; library metadata alone cannot satisfy original-context/requester checks. Clear/cancel the library with every resource/session/context generation and expose loading/error/empty/page states. Select only diagnostics actually offered by the server; when none are available show the separate-permission reason and do not send a request. Gate the public composer and transition submission by server-authorized actions, preserving client reopen and original recovery. Present allowlisted policy facts before optional raw JSON and add an accessible evidence-section index without changing section paging/cancellation.

Extend the actual-runner rehearsal to apply the real psql runtime-grant script on each supported variant and verify SELECT/INSERT plus exact mutable-column privileges, immutable write denial, no grant provisioning and no schema creation. A workflow HTTP run may additionally use an explicitly selected unprivileged fixture login; fixture setup stays on its separately guarded owner connection. No retained database, global role or application credentials are modified. Keep fresh/upgrade exact marker and managed-version parity proof.

Expected files: workflow mapper/service metadata projection, workflow frontend adapter/component/specs, public Support component/specs, existing Compliance detail/shared facts presentation, actual-runner script and owned PostgreSQL tests, current API/runbooks/guides. New reads remain audited, no-store and bounded; no new schema, public projection, financial writer, export authorization, default SLA or production grant is needed. Unknown command outcomes retain original keys. Resume after this analysis with focused frontend and real-DB checks, then full affected gates and actual responsive browser journeys.

### C12: evidence-reference usability discovered before browser category journeys

Account and availability tasks correctly require independent ACCOUNT_DIAGNOSTICS plus an owned ACCOUNT reference, but the accepted reference was an internal client ID that the allowlisted workbench/diagnostics intentionally do not expose. Requiring an operator to discover that ID elsewhere makes the rendered playbook incomplete. Accept this exact authorized CASE reference as an additional ACCOUNT reference, resolve its account only from the already-authorized case and preserve existing numeric-reference compatibility. Assignment still grants nothing; both task write and resolution recheck ACCOUNT_DIAGNOSTICS and exact case identity. No schema, source evidence, financial writer or public DTO changes.

The workbench should populate its own reference when ACCOUNT is selected and give an explicit separately authorized policy-editor link for a verified positive intake instrument ID. Retain manual evidence references for other resource kinds. Update API/guide wording and real PostgreSQL category tests to use the opaque case reference, with foreign-case rejection. Re-run targeted backend/frontend and all-category browser paths before marking T2/T4 usable. This is a local integration correction, not a new entitlement or automatic policy change.

### C13: account-wide availability and retained diagnostic references

The real Chromium category journey passed intake/referral/decision and order/account/historical resolution/reopen, then reproduced HTTP 400 for an account-wide availability diagnostic with no instrument. `SupportWorkflowService.diagnostics` unconditionally parsed an instrument even though the intake/brief permit account-wide issues and the UI labels the evidence field optional. The same reader required retyping already-retained owned movement/exception/order IDs.

Before further edits, retain the existing authorize/audit and independent diagnostic grants. When evidence is omitted (not explicitly invalid), use only the immutable authorized case's order or allowlisted intake reference. Revalidate ownership/context as before. Availability with no instrument returns current redacted account/passive provider observations, null policy and an explicit NOT_APPLICABLE account-wide policy status; it does not guess a policy/session/quote. An explicit nonexistent instrument returns non-disclosing 404; an existing instrument lacking policy reports explicit UNAVAILABLE, not an invented success. No provider probe, financial write, schema or public projection change.

Expected changes: diagnostic service branch, accurate optional-reference UI/help/API/guide wording and real PostgreSQL tests for omitted retained references, account-wide observation shape and unknown instrument. Re-run restricted-runtime tests, rebuild/redeploy only the owned browser API and continue all-category journeys. Existing invalid explicit IDs, foreign ownership, purpose/context and quote UNKNOWN behavior remain fail-closed.

### C14: exact export-capacity acceptance

Final coverage review found that existing real archive/digest/recovery tests did not measure the specified 2000-row and 5,000,000-byte boundaries. Do not infer capacity from constants or mock successful generation. Extend the existing real-order PostgreSQL managed-export scenario with owner-seeded immutable quote observations: exactly 2000 must export, 2001 must reject without persisting another artifact. Do not disable financial guards or alter a runtime permission for this fixture.

Extract the unchanged archive byte-length guard into one named package-private static policy check so a focused unit test can pass actual 5,000,000/5,000,001-byte arrays through the exact production check. This avoids fabricating megabytes of financial source prose or mocking ZIP internals just to hit an exact compressed length. Archive generation/membership/digest/download remain covered by actual HTTP tests; this small extraction changes no limits, response codes, configuration or export authorization. Re-run the restricted-runtime scenario and export tests, then the complete backend gate.

### C15: disposable deployment-harness compatibility

The requested normal-startup rehearsal stopped before starting containers because the existing isolated Compose scripts still override a removed auth-stub service and validate unrelated Analytics blank ports/developer `.env` references. Do not change production Analytics or load a developer's settings to make these gates pass. Update only the two disposable startup harnesses to remove the obsolete service override and explicitly profile out/reset ports/env files for unused Analytics/nginx services. Keep each test's real backend/database/migration/demo-seed dependency chain.

The Operations harness must also use a project-specific backend image and temporary-directory build log, not overwrite a shared application tag/log. Correct its observed grant-count message to nine. Run both harnesses plus the existing standalone restart/pg_dump restore rehearsal; use only their named disposable projects and existing explicit synthetic provider, never retained Compose volumes.

### C16: consistent local-seed and hardened policy configuration

With the test harness isolated, actual standard local startup reproduced zero Operations grants: backend defaults enable local demo/access, but the separate seed container does not receive those defaults. `seed-role-workspaces.sh` correctly refuses grants without both flags. Pass the same explicit defaulted TRADING_LOCAL_DEMO_SETUP/OPERATIONS_ACCESS_ENABLED flags into the local seed service; do not weaken the seed guard or provision production grants.

Configuration review also found the hardened overlay inherits the base Compose Compliance demo policy unless explicitly overridden. Make Compliance access/purpose/policy/context/export defaults fail-closed in that overlay just as Operations already is, preserving explicit deployment overrides and the seed's excluded profile. Extend the read-only hardened config assertions. Expected files: both Compose definitions, existing hardened config check, deployment guide/ledger. Re-run normal startup, hardened config and the isolated client startup/restore gates; no schema/role seed content or runtime grants change.

### C17: explicit scoped queue attention views

Final task-to-code review found that C10's owner/priority/category/due filters did not yet implement the brief's Unassigned/My cases/Awaiting internal review attention views, or its default actionable versus explicit All distinction. Complete this bounded read-only surface before final acceptance. Add an optional allowlisted `view` selector: ACTIONABLE, ALL, UNASSIGNED, MINE, AWAITING_CLIENT, AWAITING_REVIEW, RESOLVED. Omission defaults to ACTIONABLE unless an explicit public status is supplied, in which case preserve that existing status query. ACTIONABLE excludes resolved cases; UNASSIGNED/MINE concern unresolved cases; AWAITING_REVIEW requires an unresolved case with an undecided linked investigation. Apply every view in the existing single shared SQL predicate before rows/counts; MINE uses the authenticated actor, never a typed identity. No per-view counts or new authority.

Expected edits: Operations controller/workflow service/mapper, typed Support adapter and staff-only queue selector, actual PostgreSQL queue regression and Angular queue regression, API/current guides. An explicit view while rollout is disabled is unavailable; initial frontend requests omit the selector until the server advertises workflowEnabled, preserving the legacy list and client projection. Changing the attention selector clears a contradictory status. Existing category/priority/owner/due filters remain composable. No migration, private write, lifecycle, public history or financial change. Re-run affected real DB/UI gates and complete backend/schema gates after this final integration.

## 5. W0 contract decisions to publish before parallel implementation

Record settled decisions in this plan and [docs/api.yaml](../../../docs/api.yaml), not in competing per-category contract files. The following names/shapes are proposals, not existing endpoints or approved permissions.

### Schema, versions and response separation

| Proposed structure / contract | Required fields and constraints to freeze |
| --- | --- |
| `support_case_intake` | Case identity, immutable original category, schema version, bounded typed category payload, reported/observed source and timestamps, verified owned object references. Explicit policy for subsequent clarification/intake revisions; never overwrite original intake. |
| `support_case_workflow` | One private projection per case: effective category, assignee, priority, next action, optional due time, checklist-template version, `workflow_version`, internal activity time. Safe migration initializes unassigned/unconfigured, not fictional progress. |
| `support_case_workflow_events` and private receipts | Attributable append-only command history: actor, case, command kind, old/new facts, reason, UTC time and normalized request digest. Unique actor/case/key receipts; private command namespace must not collide accidentally with existing public receipts. |
| `support_case_tasks` and resolution records | Category/template-specific task IDs, pending/completed/unavailable/not-applicable states, reasons, bounded authorized evidence references, actor/time and revision history. Retain prior template/checklist after reclassification. Resolution code and checklist snapshot remain attributable. |
| `support_case_referrals`, `compliance_investigations`, `compliance_investigation_events` | Immutable case/account/optional verified order relationship, typed reason, reviewer, private status/version, evidence references, findings/disposition and timestamps. Enforce account consistency and replay uniqueness in the database, not only UI. |
| Export associations / historical dispositions | Separate immutable foreign-key links to role-specific exports and exception reviews. A link cannot extend export lifetime, confer another role's access or rewrite the source exception. |
| Public DTOs | Keep allowlisted public summary/detail/feed independent of internal entities. Optional client-safe intake projection, if approved, must be explicit. No assignee, priority, private times, review status or private counts leak incidentally. |
| Staff workbench DTO | Original/effective category, `workflowVersion`, assignment/priority/next action, bounded task pages/history, evidence/referral/export summaries and server `authorizedActions`. Every denied/unavailable diagnostic has a reason without protected data. |

W0 decides migration allocation centrally; 015 is merely the next candidate at this baseline, not reserved by this plan. Separate logically reviewable migrations may be needed for repair normalization, workflow and referral structures. Each has matching fresh DDL, version bookkeeping, guards, runtime privileges and upgrade/parity tests.

### API and command contract

| Surface | Proposed command/read and required behavior |
| --- | --- |
| Compatible client creation | Existing `/api/support/cases` accepts optional `intakeSchemaVersion` and category-specific `intake` only after backend rollout. Reject unknown nested fields and foreign references. Canonical request hashing includes normalized intake but preserves replay compatibility for old payloads. |
| Staff queue | Extend `/api/operations/cases` with original/effective category, assignee, priority and actionable/overdue views. Freeze allowlisted filter values, default queue, deterministic sort and page bounds. Scope before joins/counts/pagination; public client lists retain public ordering. |
| Workbench | `GET /api/operations/cases/{reference}/workbench`; include available actions and bounded evidence links, not unrestricted object dumps. Approved eligible-assignee discovery also needs a scoped bounded contract. |
| Private commands | Explicit assignment, classification, priority/next-action and task routes under the case. Freeze exact paths before implementation. Require `expectedWorkflowVersion`, stable `Idempotency-Key`, typed payload/reason and actor/case-scoped recovery endpoint. |
| Resolution | An explicit resolution command takes public `expectedVersion`, private `expectedWorkflowVersion`, disposition code, task/evidence references and client-facing explanation. Existing `/transitions` with `RESOLVED` must use the same policy/orchestrator so it cannot bypass completion/referral checks. |
| Referral | Proposed `POST /api/operations/cases/{reference}/referrals` uses workflow version/key and verified target/reason. Return a minimal private handoff receipt. Replaying creates exactly one referral/investigation. |
| Compliance case workspace | Proposed `/api/compliance/cases` list/detail/actions with independently scoped investigation read/update authority. Keep `/api/compliance/investigations` and `/orders/{id}/evidence` as existing order-evidence contracts. |
| Managed Compliance contexts | Add explicit managed versus external mode. Resolve investigation/account/order server-side; intersect live grants, never union them. Freeze whether reviewer reassignment invalidates an existing context and test it on every protected read/export path. |
| Export recovery | Existing Compliance receipt route uses server export reference, not idempotency key. Before the reference is known, recover by replaying the original create key/order under current authorization. If a lookup-by-key route is needed, analyze/approve it explicitly; do not invent a URL in the browser. |
| Typed errors | Preserve current envelope and HTTP meanings; define stable codes for invalid input/context, key conflict, public/private version conflict, required task/referral incomplete, evidence/archive limit, unavailable dependency and expired export. Do not parse human prose or treat every 409 as context expiry. W0 owns shared handler integration. |

### Authorization and policy decisions

Existing Operations permissions are `CASE_READ`, `CASE_UPDATE`, `ORDER_READ`, `EVIDENCE_READ`, `RECONCILIATION_READ`, `EVIDENCE_EXPORT`; existing Compliance permissions are `EVIDENCE_READ`, `EXPORT_CREATE`, `EXPORT_DOWNLOAD`. New diagnostics and investigation read/update permission names are not yet approved: W0 must define them and extend schema checks, backend validation, DTOs, fixtures and documentation together.

Case read/update alone must not reveal balances, unrestricted profile or bank data. A handler may report unavailable diagnostics while ordinary permitted staff continue public conversation. Assignees must be live eligible staff with the required account authority; assignment does not bypass grant revocation. Freeze who may assign/reassign, which reasons mandate a Compliance disposition, and which evidence/disposition combinations allow resolution.

Do not invent SLAs, working hours or escalation destinations. Show age and "response target not configured" until a versioned service policy defines targets, pauses, reminders, deduplication and accountable escalation ownership. Optional due times require attribution, not an implied service promise.

### Transaction and recovery design

W3/W5 must agree a deterministic lock order consistent with existing Support client/case locks and shared referral state. New resolution/referral commands must validate current permissions, recover committed receipts, validate expected versions, update projections, append history and audit, and retain receipts in one transaction. Do not rely on self-invoked Spring transactional methods or two sequential public/private requests.

Verify concurrency for private/private and public/private updates, public reopen versus resolution, classification versus task completion, and referral decision versus resolution. Private version advances never require a fabricated public event. Existing notes remain supported without silently changing their command contract; a note-version migration requires its own compatibility decision.

After a definitive version conflict, refresh and require a deliberate new command. After an uncertain transport outcome, retain the original key/normalized request association. Persist opaque recovery metadata only, not message prose, evidence, context tokens or purpose references. Storage failure is explicit before submission; authorization loss clears protected browser state while server receipts remain authoritative.

## 6. Work-package ownership and parallel-development contract

Use proposed `codex/` branch names below only after checking the repository's current branch policy. No branch is created by this plan. One owner can hold several packages, but shared write sets are always serialized.

Path aliases: `B` = `../../../backend/src/main/java/com/neueda/leap`; `BT` = `backend/src/test/java/com/neueda/leap`; `F` = `frontend/src/app`; `DBT` = `testing/src/test/java/com/neueda/leap/db`. Bracketed additions are proposed paths, not existing files.

| Package / suggested branch | Exclusive write set | Prerequisites and handoff |
| --- | --- | --- |
| W0 / `codex/compliance-operations-contracts` | This plan, shared `../../../docs/api.yaml`; all `database/fresh/`, `database/migrations/`, `database/runtime-grants.sql`; approved `database/demo/` changes; `F/app.routes.ts`, `F/core/auth/workspace-policy.ts` and associated route-policy tests; `B/exception/` and shared response-header changes; existing broad backend/DB suites and shared scripts when necessary. | First owner. Publish API/schema/permission/action/UI extension decisions, F1 repair evidence, migration reservations and integration commits. Do not become a parallel bottleneck by allowing others to edit these files. |
| W1-B / `codex/compliance-correctness-backend` | Existing `B/service/Compliance*`, `B/mapper/Compliance*`, `B/controller/ComplianceController.java`, `B/dto/ComplianceDtos.java`; matching focused tests and new `[BT/compliance/ComplianceEvidenceQueryTest.java]`, `[BT/compliance/ComplianceHttpJourneyTest.java]`. | W0 existing-contract reconciliation and DB harness available; real endpoint acceptance depends on F1. Own F2/F4 backend, export operation-scope checks and local typed error production. |
| W1-U / `codex/compliance-correctness-ui` | Existing `F/features/compliance/` files/specs; new `[F/shared/workspace/utc-filter.ts]` and tests; small UTC integration in `F/features/operations/orders-page.component.ts` and focused tests. | W0 search/error/recovery contract and W1-B API behavior. Own F3-F7 browser work. W5 cannot edit this directory concurrently. W4 cannot edit the Operations UTC consumer until handoff. |
| W2 / `codex/workspace-presentation-foundation` | `../../../frontend/src/styles.css`; new `[F/shared/workspace/ui/]` data-only primitives/tests; measured fixes only in shell/top-nav and their focused tests. | W0 UI interface decisions; independent of new case schema. Publish class/component examples before W4/W5 consumers. Sole global CSS owner. |
| W3 / `codex/support-workflow-engine` | New `[B/dto/SupportWorkflowDtos.java]`, `[B/service/support/SupportWorkflowService.java]`, `[B/service/support/SupportCategoryRegistry.java]`, shared handler interfaces, `[B/mapper/SupportWorkflowMapper.java]`; existing Support service/mapper/controllers/export hooks; `F/core/api/support-api.service.ts` and proposed workflow adapter/types; dedicated `[BT/support/SupportWorkflow*Test.java]`. | W0 schema/contracts. Own common engine, authorized actions/F8 backend, atomic resolution and transactional referral hooks. Category owners never edit this orchestrator/registry. |
| W4 / `codex/support-workbench-ui` | New `[F/features/support/]` shell/list/detail/common components; existing `F/features/client/support-page.component.ts` extraction; client account/instrument/order entry files and dedicated tests; Operations templates including evidence tables and post-W1-U orders integration. | W0 routes, W2 primitives, W3 API. Own component registration and all shared entry-link patches. Preserve existing Support routes/recovery/updates. |
| W5-B / `codex/compliance-referral-backend` | New `[B/dto/ComplianceCaseDtos.java]`, `[B/controller/ComplianceCaseController.java]`, `[B/service/compliance/]` and `[B/mapper/ComplianceCaseMapper.java]`, dedicated `[BT/compliance/ComplianceReferral*Test.java]`; managed-context changes in existing Compliance access/evidence/export files only after W1-B handoff. | W0 referral schema/permissions and W3 transactional hooks. Existing Support edits submitted to W3; no Operations-controller role widening. |
| W5-U / `codex/compliance-review-workspace` | Post-W1-U `F/features/compliance/` composition; proposed `review-cases/` screens, role-specific case adapter/types and tests. | W1 merged, W2 primitives, W5-B contracts, W0 route integration. No shared CSS or Operations API injection. |
| T1 / `codex/support-order-playbook` | `[F/features/support/categories/order-question/]`, `[B/service/support/OrderQuestionHandler.java]`, disjoint new order-playbook tests. | W0 contracts, W3 handler interface, W4 intake/panel interface; retained evidence authorization. Send receipt-entry patch to W4. |
| T2 / `codex/support-account-playbook` | `[F/features/support/categories/account-review/]`, `[B/service/support/AccountReviewHandler.java]`, new reason-limited diagnostics DTO/service/mapper and account tests. | Same interfaces plus W0 diagnostics permission. No banking/trading writers or direct account-page edits. |
| T3 / `codex/support-historical-playbook` | `[F/features/support/categories/historical-record/]`, `[B/service/support/HistoricalRecordHandler.java]`, new bounded historical-detail/disposition reader and tests. | Same interfaces plus W0 review storage and historical projection. Submit account link patch to W4. |
| T4 / `codex/support-availability-playbook` | `[F/features/support/categories/trading-availability/]`, `[B/service/support/TradingAvailabilityHandler.java]`, new read-only diagnostics reader and tests. | Same interfaces plus approved safe policy/account/provider readers. Policy editor/navigation patches go through W4, shared readers through their owner. |
| T5 / `codex/support-general-triage` | `[F/features/support/categories/other/]`, `[B/service/support/OtherCaseHandler.java]`, disjoint triage tests. | W3 classification/task/referral commands and W4 panel. No separate classification engine or private case merger. |
| W6 / `codex/compliance-operations-verification` | New isolated journey/scenario tests with synthetic fixtures; current runbook and role/database guides after owner handoff; verification ledger here. | Integrates completed package commits. Shared scripts, broad suites, route contracts and schema remain W0-owned while active. |

Conflict controls:

1. Freeze exact DTOs, handler signatures, input/output events, authorized-action codes and category schema/template versions before category branches start.
2. W3 publishes the backend interface/registry skeleton and registers handlers once; W4 publishes the component interface and registers category components once. Consumers supply disjoint implementations, not competing union/registry edits.
3. Category intake must support an original category plus private effective classification without mutating public identity. Required fields after reclassification become explicit pending clarification, not fabricated original intake.
4. Only W4 edits shared client entry links; T2/T3/T4 submit desired fields and a small integration patch. Only W0 edits routes/OpenAPI/migrations/grant constraints.
5. W1-B and W5-B serialize existing Compliance backend files; W1-U and W5-U serialize existing Compliance UI files. W2 hands off presentation APIs, not ownership of global CSS.
6. W6 submits broad-suite/script changes to W0 while W0 is active. New package-specific tests live in separate files; avoid shared mutable fixture editors.
7. No broad formatting, generated-file churn, package/toolchain upgrades or lockfile edits without a recorded dependency requirement.
8. Each PR includes its baseline/dependency commits, owned paths, integration requests, failing regression/fix evidence, compatibility notes and unresolved policy gates. Resolve conflicts with the owner rather than overwriting concurrent changes.

## 7. Foundation package execution steps

### W0: integration, schema repair and contracts

1. Confirm implementation scope and inspect current changes/migration numbers. Keep user-provided documentation untouched unless explicitly assigned.
2. Reconcile executable Compliance endpoints into OpenAPI, documenting existing grants/context headers/DTOs/errors separately from proposed workflow/referral APIs.
3. Execute F1 inventory/reproduction and repair strategy; extend actual-runner upgrades and structural parity through the complete chain. Review test harnesses and hardcoded old version lists in shared suites/scripts.
4. Freeze section 5 decisions: data constraints, permissions, visibility, all public/private versions, replay namespace, eligibility, allowed actions, resolution/referral transitions, legacy behavior, URL state and error codes.
5. Publish additive workflow/referral schema and runtime-grant changes only after repair is safe. Grant projection updates narrowly; immutable input/history/receipts/artifact links remain append-only. Do not grant runtime provisioning of staff access.
6. Define readiness and rollout gates for each new surface, including a documented control/configuration path. Disabled/unready backend commands must fail explicitly; hiding a button alone is not a rollout control.
7. Publish stable contract commits to W1/W2/W3, then integrate category/route/fixture patches centrally. Local fictitious grants remain absent from hardened deployment.

Done: supported fresh/upgrade paths converge, old invalid contexts fail closed, shared contracts are executable/testable, and downstream developers have no open interface ambiguity.

### W1: make existing Compliance journeys real before redesign

1. Add real PostgreSQL query/HTTP regressions, including all eight sections in server order: events, decisions, quotes, attempts, reservations, fills, postings, notifications.
2. Correct F2 ordering and F4 filters; preserve legacy unknown timestamps and fixed projections. Test tie-breaking and scoped counts, not just generated SQL strings.
3. Implement validated UTC conversion and applied search state. Keep account-specific/order-specific filters in actor/context-scoped memory unless explicitly approved for URLs; allowlist only approved status/time/page state in URLs. Do not serialize purpose references.
4. Cancel and generation-gate detail/sections/exports, including error callbacks. Reset per-section state on identity/context/order changes, not only component destruction.
5. Add receipt/download controls honoring `authorizedActions`, ready/expired state and server reauthorization. Verify operation-specific permissions on the artifact account before making the journey available.
6. Persist original-key opaque recovery metadata before POST, prevent busy re-entry, and implement same-key replay for unknown outcomes. Download only returned server ZIP bytes; revoke temporary object URLs after use.
7. Distinguish context denial from key/size/version conflicts through typed error reasons. Preserve current context for non-authorization conflicts and explain the correct next action.
8. Remove stale "no backend contract" copy only where implemented behavior is verified. Hand off the merged directory to W5.

Done: real scoped search -> detail -> section pages -> export -> lost-response recovery -> receipt -> exact-byte download works and F2-F7 regressions are green.

### W2: data-only visual system

1. Reproduce representative Operations/Compliance layouts with synthetic long content and loading/denied/stale states before editing shared CSS.
2. Publish minimal page-heading/actions, status, filter toolbar, context summary, evidence-facts and pagination primitives/classes. They accept data/events only and cannot fetch, infer entitlement or share role adapters.
3. Add explicit action variants, complete focus treatment (textarea, summary, labelled scroll region), consistent form fields and container-safe grids. Wrap prose/references/digests; keep numeric/time columns compact and scrolling inside labelled tables.
4. Preserve measured header-height behavior; examine 699/700/701 and 767/768/769px before changing shell breakpoints. No speculative hard-coded offsets or global `!important`.
5. Check keyboard focus, 44px controls, contrast, 200% zoom, square-corner/no-gradient convention and overflow at required sizes.

Done: W4/W5 can compose pages from published primitives without copying CSS, and before/after evidence supports every shared layout change.

### W3 and W4: shared case engine and workbench

1. W3 implements typed bounded intake and handler dispatch behind one common orchestrator; no five copies of `SupportCaseService`.
2. Add private workflow versions/history/recovery, scope-safe assignment/classification/priority/tasks, queue filters and per-case authorized actions. Mutations revalidate assignee and linked evidence.
3. Implement atomic resolution and gate every legacy/new RESOLVED path with the approved workflow/referral policy. Keep public reply/reopen and existing-case compatibility.
4. W4 first extracts case UI without changing behavior, retaining client routes, request-key storage/recovery, public/private paging, updates acknowledgement and denial clearing.
5. Compose an Operations main reading column plus an approximately 18-22rem owner/next-action/checklist panel; stack narrowly. Public conversation and private composer labels/actions remain distinct.
6. Queue offers actionable/all, unassigned/my, awaiting client/internal review and resolved views; display public/internal activity separately. Do not invent SLA or dashboard metrics.
7. Integrate all category registrations and account/instrument/receipt context links once. Add private export-association listing with separate entitlement/expiry reasons; clients never receive association metadata or bytes.

Done: F8 is fixed, existing Support recovery/lifecycle survives extraction, private commands cannot affect the public projection, and every category can plug into the same engine/workbench.

## 8. Category implementation cards

Each category owner implements its intake component, private panel, handler/validator, exact resolution-code/checklist rules and dedicated tests in the disjoint paths in section 6. All must cover valid/missing/invalid intake, information requests, unavailable evidence, permitted resolution, reopening, foreign objects and insufficient grants.

### T1: ORDER_QUESTION

Input: issue reason pending/delayed, rejection, price/quantity/fee, apparent duplicate or posting discrepancy; optional verified owned order. Generic Support offers an owned order picker. "Cannot find my order" remains a triage branch; never require a fake ID or disturb an uncertain financial request key.

Evidence: retained instruction and submission/acceptance/state; rejection stage/code; deadlines/jobs/attempts/leases for delay; decision/release for rejection; retained side quote, quantity/unit/fees and accepted policy for pricing; distinct authorized order references for duplicates; linked fill/postings/permitted reconciliation for accounting. Passive current worker/provider observations are labelled with source/time.

Disposition codes: `EXPLAINED_ORDER_STATE`, `EXPLAINED_REJECTION`, `EXPLAINED_PRICING`, `DUPLICATE_NOT_CONFIRMED`, `REFERRED_FOR_CORRECTION`. Define code-specific required checks and explicit missing-evidence handling; no order-evidence conclusion without a verified target. Integrity/policy issues go to Compliance, dependency incidents to a named Operations owner, accounting to Finance's existing process.

Acceptance: accepted/executing, both rejection stages, filled, legacy, missing evidence and foreign orders. Public explanation cites retained evidence, not today's quote. Support/referral lifecycle changes none of orders/jobs/reservations/fills/journals/balance projections.

### T2: ACCOUNT_REVIEW

Input: eligibility, cash/available balance, position availability or setup; optional owned cash-movement reference. Do not solicit bank numbers or identity documents.

Evidence: new reason-limited read-only staff diagnostics under an independently approved permission: eligibility/hold flags, posted/reserved/available exact amounts and currency/as-of, approved pending relationships and movement reference/outcome. Reuse safe calculations/readers rather than expose client banking/profile APIs or decrypt bank data.

Checklist: establish issue -> distinguish posted/reserved/available -> inspect related pending orders/movements -> retain relevant observations -> identify authorized next owner/action. Client setup routes stay client-owned; internal review can be referred.

Disposition codes: `EXPLAINED_BALANCE`, `CLIENT_SETUP_COMPLETED`, `ELIGIBILITY_REVIEW_COMPLETED`, `REFERRED_FOR_CORRECTION`. Fresh relevant evidence or explicit unavailable disposition and responsible unresolved-dependency owner is required.

Acceptance: reserved is not missing; no sensitive bank payload; limited staff can still reply; hold/referral changes do not affect public time/count/version; Support cannot unlock accounts, change eligibility or release money.

### T3: HISTORICAL_RECORD

Input: optional owned exception reference plus original record kind/issue reason from account warning. W4 adds an entry action for the selected exception; general historical questions remain possible.

Evidence: approved bounded detail reader for original source reference, captured time, reason, known/unknown provenance and preserved migration facts. Existing reference/kind/time list is insufficient. Ownership checks precede reading; current account projections stay distinct.

Checklist: identify record -> verify owner/currency/unit provenance -> inspect preserved facts -> document unknowns -> obtain Compliance review when disputed -> record Finance disposition when accounting is needed. Retain a separate attributable review; never overwrite/delete the exception.

Disposition codes: `HISTORICAL_RECORD_EXPLAINED`, `INSUFFICIENT_RETAINED_EVIDENCE`, `REFERRED_FOR_ACCOUNTING_REVIEW`.

Acceptance: selected exception survives navigation; foreign references do not disclose; missing original times/currency remain unknown; repeated disposition/replay is safe; no quarantine release or historical balance reconstruction.

### T4: TRADING_AVAILABILITY

Input: optional instrument, attempted side, observation time and displayed reason. Account-wide failures require no instrument. W4 preserves these entry fields; client observations are not server evidence.

Evidence: safe ordered diagnostics for account prerequisites, instrument active/enabled/side controls, session, provider freshness/availability and relevant limits/resources, each with source/time/evaluation state. Report multiple relevant controls without flattening provider trouble to market closure. Do not place a new order to diagnose or promise execution.

Policy action: authorized navigation to existing editor with instrument selected; saving remains a separate reason/revision/audit operation. Retain the actual policy-change reference after deliberate verification, not an auto-save during resolution.

Disposition codes: `PREREQUISITE_EXPLAINED`, `EXPECTED_CONTROL_CONFIRMED`, `SERVICE_RECOVERED`, `POLICY_CHANGE_VERIFIED`; recovery claims require a fresh relevant check.

Acceptance: buy-only, sell-only, full halt, missing bank plus halt, stale quote, provider outage and closed session remain distinct; instrument/side survive navigation; unauthorized staff cannot change policy; existing accepted-order recovery remains intact.

### T5: OTHER

Input: affected area, expected/actual behavior, approximate occurrence time and safe reproduction steps; bounded short form, no attachments/secrets.

Workflow: audited effective classification and responsible owner; preserve original intake/category and prior evidence; instantiate the newly selected checklist with pending/unavailable tasks, not completion. Support information requests and attributable product/engineering referral references.

Disposition codes: `GENERAL_GUIDANCE_PROVIDED`, `ISSUE_NOT_REPRODUCED`, `DUPLICATE_CASE_LINKED`, `REFERRED_TO_PRODUCT_SUPPORT`. Duplicate target must be independently authorized; no history merge or other-client disclosure. A referral means tracked next action, not a fixed defect.

Acceptance: reclassification leaves public history/version unchanged; concurrent reassignment conflicts; foreign duplicates do not disclose; "not reproduced" records attempted steps and useful public explanation; unresolved unowned referrals remain visible rather than disappearing on closure.

## 9. W5: Compliance referral lifecycle and visual integration

1. Implement the immutable verified source-case/account/optional-order relationship plus private status/version/event records. Account-only cases need no dummy order.
2. W0 freezes a transition matrix over `QUEUED`, `IN_REVIEW`, `NEEDS_INFORMATION`, `DECIDED`, `CLOSED`. Decisions require eligible reviewer/reason/disposition; revisions/reopens create new attributable events. No silent rewrites.
3. Add scoped investigation read/update APIs and independently authorized reviewer assignment. Compliance receives only an approved referral projection, not every Operations note or client profile field.
4. W3 integrates referral creation and mandatory-disposition resolution checks in the existing case transaction. W5 does not widen `OperationsCaseController` or import Operations credentials into Compliance.
5. Implement managed Compliance context narrowing throughout search, counts, order detail, sections, findings, export creation/replay/history/receipt/download and associations. External-purpose mode stays explicit; typed text never becomes access scope.
6. Return minimal handoff status, requested information, outcome and next owner to authorized Operations. Compliance may propose a client-safe explanation; Operations deliberately publishes it. Private decisions alone never change client state/time/feed.
7. Link role-specific exports through immutable authorized associations without conflating resource types, requester, digest or expiry. Account-only review can refer to approved account evidence; it must not pretend the existing single-order export API supports account archives.
8. After W1 merges, align Compliance page hierarchy with W2 primitives: heading/actions -> compact active-context summary -> work area -> evidence -> exports. Add Review cases separately; preserve existing order-evidence URLs.
9. Group evidence with an accessible index/disclosures, keep completeness/order/coverage/failures visible, show structured facts before optional raw JSON, and expose explicit server-authorized receipt/download actions.

Done: replay creates one investigation, account-only works, grant/context/reviewer changes take effect, both queues expose permitted ownership/outcomes, mandatory referrals gate resolution, and client projections remain unchanged until deliberate public publication.

## 10. Integration order and independently shippable slices

| Stage | Work that may proceed in parallel | Gate before next stage |
| --- | --- | --- |
| 0 | W0 source/contract decisions and F1 reproduction; no feature branches editing shared contracts. | Scope confirmed, compatibility strategy and contract ownership fixed. |
| 1 | W1-B and W1-U on disjoint files; W2 on presentation foundation. W0 serializes schema/contracts. | F1/F2 real PostgreSQL evidence; W1 existing journeys verified; W2 APIs/classes published. |
| 2 | W3 backend/API and W4 behavior-preserving UI extraction after their interfaces are published. W5-B can build disjoint referral code after schema/hooks are fixed. | Shared case commands/workbench/handler interfaces merged, authorized-action behavior established. |
| 3 | T1-T5 implement disjoint category paths; W5-B finishes referral/context paths after W1-B handoff; W5-U uses merged W1-U and W2. | W3/W4 integrate registrations/entry links once; W0 integrates routes/permissions; category/referral tests green. |
| 4 | W6 integrates scenario/visual/deployment evidence and documentation with owners. | Full matrix observed, rollout gates/limits documented, no current-request work left open. |

Safe early release: W1 correctness plus W2 and role-specific presentation integration can ship before the new workflow engine. New workflow/referral controls remain disabled until schema, compatible backend, explicit approved grants and readiness gates are present. Do not expose half-wired category controls or call mocked data a release.

## 11. Verification plan and evidence requirements

### Existing tests to extend or preserve

| Suite | Current value and gap |
| --- | --- |
| [Support journey](../../../frontend/src/app/features/client/support-journey.spec.ts) | Route ordering, original-key recovery, inert prose, identity clearing, public updates acknowledgement and staff queue behavior. Preserve during extraction; add category/workbench tests separately. |
| [Trading PostgreSQL](../../../backend/src/test/java/com/neueda/leap/trading/TradingPostgresTest.java) | Real HTTP Support lifecycle, ownership, scoped counts, replay, concurrency, audit rollback, financial invariants and managed exports. Shared baseline upgrades currently stop at 012; W0 owns changes. |
| [Compliance PostgreSQL](../../../backend/src/test/java/com/neueda/leap/compliance/CompliancePostgresTest.java) | Fresh DDL and immutable guards only; not actual query/HTTP/export coverage. Has its own Testcontainers startup. |
| [Compliance service tests](../../../backend/src/test/java/com/neueda/leap/service/ComplianceEvidenceServiceTest.java), [export tests](../../../backend/src/test/java/com/neueda/leap/service/ComplianceExportServiceTest.java), [controller tests](../../../backend/src/test/java/com/neueda/leap/controller/ComplianceControllerTest.java) | Useful bounded behavior with mocks, not proof SQL runs or mixed-account download/create authority is correct. Retain and augment. |
| [Compliance screens](../../../frontend/src/app/features/compliance/compliance-screens.spec.ts), [adapter](../../../frontend/src/app/features/compliance/compliance-api.service.spec.ts), [feature](../../../frontend/src/app/features/compliance/compliance-feature.spec.ts) | Fixture render/route/path coverage. Add temporal races, original-key persistence, UTC/return state and real-contract shape assertions. Existing fixtures are not authoritative enum/section-name definitions. |
| [Fresh/upgrade parity](../../../testing/src/test/java/com/neueda/leap/db/FreshTradingSchemaTest.java), [provider cutover](../../../testing/src/test/java/com/neueda/leap/db/ProviderCutoverMigrationTest.java), [isolated DB harness](../../../testing/src/test/java/com/neueda/leap/db/AbstractPostgresIntegrationTest.java) | Fresh/upgrade structure comparison belongs in the first suite; the cutover suite protects historical quarantine. Extend through repaired Compliance/new migrations without weakening economic guards. |

### Required measurable matrix

| Area | Assertions |
| --- | --- |
| Category coverage | Each of the five cards: intake boundaries, info request, allowed resolution, reopen, unavailable evidence, insufficient permission and foreign object plus its specific scenarios. |
| Public/private isolation | Compare client detail/list/feed fields and bytes where stable before/after notes, assignment, effective classification, tasks, referral progress and decisions. Public timestamps/version/unread/order unchanged. |
| Transactions | Same-key same-payload replay; changed-payload conflict; stale public/private versions; concurrent assignment, resolution and referral; injected required-audit failure rolls back projection/history/receipt together. |
| Authorization | Role mixing, account scope before count/page, live revocation, disabled identity, invalid/expired context, reassignment policy, eligible-assignee checks, unrelated export requester, operation-specific mixed-account grants. |
| Financial invariants | Snapshot complete target orders/jobs/reservations/fills/cash and position journals/balance projections before and after Support/referral-only actions; compare values and row counts. Isolate workers or account for intentional normal background execution. |
| Existing Compliance | All eight real mapper sections/counts/pages, valid statuses/legacy facts, UTC limits, restored applied filters/page and invalid URL bounds, delayed old success/error, independent section state, audit/source failure. |
| Exports | Unknown response/reload recovery with same actor/order/key; no duplicate on busy re-entry; receipt/download action gating; exact returned ZIP members/digests; 2000-row section and 5,000,000-byte archive limits at and beyond boundaries; expiry/tampered bytes/revocation; no local evidence reconstruction. |
| Database | Fresh, retained 012, both 013 variants, applied 014, complete-chain parity, repeat runner, restricted runtime grants, immutable records and data preservation. Real runner exercise is separate from JDBC scripts. |
| Browser journeys | Owning client -> each intake -> permitted Operations triage/evidence/task -> Compliance when required -> explicit public explanation -> resolution -> client reopen. Also read-only staff, other client, session/context loss and unavailable dependencies. |
| Visual/accessibility | 320/390/700/768/1024/1440px plus 699/701/767/769px; 200% zoom; long references/prose/digests; keyboard-only focus/navigation/errors; captions/scoped headers/labelled scroll wrappers; stale/loading/empty/not-found/denied/unavailable states. No page-level overflow from nested panels. |

Run focused checks for each package first. New proposed test files are not executable until created; do not report absent selectors as passed. Examples using existing runners:

```sh
# Repository root, relevant existing backend unit/controller subset:
mvn -B -f backend/pom.xml \
  -Dtest=ComplianceEvidenceServiceTest,ComplianceExportServiceTest,ComplianceControllerTest test

# PostgreSQL integration requires supported Docker or approved disposable test DB:
mvn -B -f backend/pom.xml -Dtest=CompliancePostgresTest test
mvn -B -f testing/pom.xml -Dtest=FreshTradingSchemaTest,ProviderCutoverMigrationTest test

# frontend/; repeat --include for the affected existing/new spec files:
npm test -- --watch=false \
  --include='src/app/features/compliance/**/*.spec.ts' \
  --include='src/app/features/client/support-journey.spec.ts'
npm run test:qof-styles
npm run build
```

At final integration run the complete relevant gates:

```sh
# Repository root:
mvn -B -f backend/pom.xml test
mvn -B -f testing/pom.xml test
python3 scripts/test-hardened-config.py

# frontend/:
npm test -- --watch=false
npm run test:qof-styles
npm run build

# Repository root, after reviewing prerequisites and disposable resource isolation:
mvn -B -f backend/pom.xml -DskipTests package
python3 scripts/test-client-compose.py
python3 scripts/test-client-recovery.py
python3 scripts/test-operations-demo.py
```

Use existing dependencies. Install/restore only after a manifest change or missing-dependency/tool failure. `npm ci` is not an unconditional first step on a populated shared machine. Do not point destructive schema-reset tests at retained data; `ISOLATED_TEST_DB_URL` is only for the dedicated validated disposable database, and not all existing suites honor it.

Check `../../../scripts/test-role-workspaces.sh` compatibility before using it as a substitute: its Maven container has no Docker socket while the dedicated Compliance test always starts Testcontainers. Do not mount a host socket casually to hide that mismatch. Missing infrastructure is blocked/unrun, not a pass. A frontend build/style check is not visual evidence, and mocked HTTP is not actual PostgreSQL proof.

For each executed check record command, environment/tool versions, commit, date, result, fixture scope and log/screenshot location with sensitive data excluded. Store temporary evidence in the session artifacts directory unless a maintained repository artifact is explicitly required.

## 12. Rollout, rollback and documentation

1. Rehearse backup/restore and F1 repair on disposable copies representing supported variants. Production deployment approval remains separate.
2. Apply compatible additive schema through the actual ordered owner-run runner; verify exact markers/objects/parity. Reapply reviewed non-owner runtime privileges before enabling backend commands.
3. Deploy compatible backend with new commands disabled until approved grants/policies/readiness exist. Old payloads, case reads/replies/recovery and immutable history continue to work.
4. Provision explicit approved account-scoped application grants; never auto-expand existing staff grants or use assignment as entitlement. Local demo fixtures are deliberate and remain excluded from hardened deployments.
5. Enable feature surfaces only after their full backend path and diagnostics/referral dependencies are ready. Show unavailable reasons and unconfigured service targets honestly.
6. Rollback disables new commands/UI and redeploys compatible application code; retain new intake/history/referrals/decisions/exports. Do not drop tables, reverse version markers or delete financial evidence to roll back.
7. W6 updates the current runbook, database guide/diagram/counts, managed Support guide, both Compliance guides and relevant Operations documentation only after observed implementation. Correct touched historical `impDocs/` links to current paths; avoid unrelated documentation rewrites.
8. Update main-document current-state wording through its assigned integration owner only when behavior is delivered. Never repurpose the supplied original documentation completion ledger as application verification.

Out of scope unless separately analyzed and approved: financial repair/corrections, new settlement/banking workflows, automated account unlocking, attachments/identity-document uploads, external client messaging/regulatory submissions, grant-management UI, retention/purge administration, automated risk scoring, opaque urgency inference and analytics live-case reads.

## 13. Execution ledger and completion gates

Planning delivery:

- [x] Read supplied task documents and relevant implementation/deployment guidance.
- [x] Trace affected public/private Support, Compliance, evidence/export, schema, role, UI and financial boundaries.
- [x] Create an independent implementation plan before application edits.
- [x] Record larger-change analyses, exact ownership, dependencies, missing contracts and verification gates.
- [x] Validate linked source paths, task coverage, Markdown structure and whitespace; verify documentation-only worktree scope.
- [x] Confirm whether application implementation, rather than planning only, is requested.

Application implementation:

- [x] W0: freeze contracts, resolve F1, extend upgrade/parity/runtime-grant gates.
- [x] W1: fix F2-F7 and verify actual Compliance search/detail/export recovery/download.
- [x] W2: publish measured shared presentation foundation.
- [x] W3: private workflow/authorization/actions/atomic resolution engine.
- [x] W4: preserve client Support while integrating Operations workbench and F8.
- [x] T1: order playbook and evidence-based dispositions.
- [x] T2: account diagnostics/playbook.
- [x] T3: historical evidence/disposition playbook.
- [x] T4: availability diagnostics/playbook.
- [x] T5: general triage/reclassification playbook.
- [x] W5: independent Compliance referral lifecycle, managed contexts and UI parity.
- [x] W6: integrated behavioral/schema/browser/visual/deployment evidence and current documentation, with the unrelated baseline exceptions below retained rather than disguised as passes.

The full application refactor is complete only when every category has usable intake, scoped evidence/checklist, attributable disposition and understandable public explanation; permitted staff can track referral ownership/outcomes; Compliance's real search/export journey works; shared presentation is responsive/accessible; and all applicable behavioral/schema/browser gates have observed results. Explicit policy-dependent limitations must remain visible.

### Final observed verification

Source baseline remains `9c4b959d720bfb4984b22a7371f803dfbd5b2b14`; implementation is an uncommitted worktree, not a release commit. All results below were observed on 2026-10-23 in disposable synthetic fixtures, not production. Backend/database commands used Java 21 (`maven:3.9-eclipse-temurin-21`) and PostgreSQL 15; frontend commands used `node:22-bookworm` with `TZ=America/New_York`. No dependency manifest/toolchain upgrade was introduced.

Evidence directory: `/home/one/.copilot/session-state/50145d2f-45c3-4364-a0ac-3aaedeaa0665/files/`. The four explicitly named `tadpoles-refactor-50145-*` browser/API/provider/database containers and their network were removed after final acceptance; no unrelated resources were stopped. Logs, synthetic archive and screenshots are retained here rather than committed.

| Gate | Observed result | Evidence |
| --- | --- | --- |
| `mvn -q -f backend/pom.xml test` with isolated DB and dedicated W1 query DB | 430 tests in 74 suites; zero failures, errors or skips. Full rerun after C17. | `final-full-backend.log`; backend Surefire reports |
| `mvn -q -f testing/pom.xml test`, serially after backend schema-reset tests | 19 tests in 7 suites; zero failures, errors or skips; final backend package built afterward. | `final-full-backend.log`; testing Surefire reports |
| `SupportWorkflowPostgresTest` with `WORKFLOW_RUNTIME_ROLE=workflow_runtime` | All 11 actual HTTP/PostgreSQL scenarios passed under restricted runtime, including final queue views. Fixture setup alone used the guarded owner connection. | `final-restricted-workflow.log` |
| Actual `../../../database/migrate.sh` rehearsal | Fresh, retained 012, old 013, current 013 and applied 014 all reached complete-chain parity through 017; repeated runner, preserved contexts/data and restricted runtime privilege/immutability assertions passed. | `final-migrations.log`; `../../../scripts/test-compliance-migrations.py` |
| Affected Angular tests, style gate and production build | 76 tests in 15 files passed; QoF styles and production build passed. | `final-focused-ui.log` |
| Complete Angular suite | 235 passed, one unchanged Analytics baseline test failed; not reported as a full pass. | `final-full-ui.log` |
| Real Chromium journeys | 17 recorded journeys: all five intake/checklist/public-resolution/reopen paths; private referral/information request/decision; actual order ticket and order-linked managed review; one-order scoped search/eight sections/export receipt/download/history/recovery; context clearing; foreign-client 404; My cases view; unavailable private dependency; live read-only revocation. Zero browser script errors. | `browser-journey.cjs`, `browser-journey.log`, `browser-results.json` |
| Responsive/keyboard measurements | Six screens at 320/390/699/700/701/767/768/769/1024/1440px plus CSS 200% zoom: 66 measurements, no page-level overflow. Tab focus verified; 390px Operations and 1440px Compliance screenshots visually inspected. CSS zoom is not claimed to be native browser UI zoom or a full assistive-technology audit. | `browser-results.json`; `operations-workbench-390.png`, `compliance-review-1440.png`, other screenshots |
| Actual archive/capacity checks | Real managed export accepted 2000 retained rows and rejected 2001 without another artifact. Exact production byte guard accepted 5,000,000-byte input and rejected 5,000,001. Browser-downloaded ZIP matched retained archive digest, had exactly `evidence.json`, `manifest.json`, `limitations.txt`, and its evidence bytes matched the manifest content digest. | `export-boundary-runtime.log`; final backend tests; `browser-authorized-export.zip` |
| Standard local startup | Nine repeatable scoped Operations fixture grants; both clients' five categories, scoped real order/export and preserved revocations passed with final package. | `final-operations-demo.log` |
| Client fresh/retained Compose startup | Final package passed fresh build/startup, 14 fixture policies, funding/spend/restart without refill/policy reset, and retained-volume migration/quarantine. | `final-client-compose.log` |
| Restart and backup/restore | Durable cases/private history/receipts/unread/grants; identical export bytes after restore; runtime booking and repeated migrations passed. Measured synthetic dump/restore was 3.335 seconds, not a production RTO. | `final-client-recovery.log` |
| Hardened configuration | Demo seed excluded, non-owner runtime identity selected, workflow disabled and both role policies fail closed without approved configuration. | `../../../scripts/test-hardened-config.py` |
| API and maintained harness checks | OpenAPI is already 3.1.0; all 106 non-Analytics paths and components validated, including conditional intake schemas and actual handler reason enums. Python AST, maintained shell syntax and `git diff --check` passed. | `../../../docs/api.yaml`; validation commands recorded in session |

Reproduction constraints: backend/schema tests reset their configured disposable public schema, so run them serially. `ISOLATED_TEST_DB_URL` must name `tadpoles_isolated_test`; W1 queries use a separate `COMPLIANCE_W1_JDBC_URL` with its explicit fixture DB user/password. The restricted workflow run additionally supplies `WORKFLOW_RUNTIME_ROLE`. Use the maintained `../../../scripts/test-role-workspaces.sh`/migration/deployment harnesses or equally isolated resources, not a retained developer database. Maven cache was mounted separately; no host Docker socket was mounted into Maven. Browser HTTP requests were confined to the owned local app and the backend used the explicitly selected synthetic HTTP market provider, never a live-provider fallback.

### Retained baseline exceptions and policy-dependent limits

The unchanged Analytics test `keeps filters visible and labels an unavailable report without invented values` rejects any `0` in the entire page even though existing static copy includes "Indexed to 100" and period labels. Its component also retains the existing 372-byte CSS budget warning. Neither unrelated file was modified to hide the failure.

Complete OpenAPI validation is blocked by the same ten undefined Analytics component names present at HEAD (reused across its paths). Missing-reference sets were compared with HEAD and are unchanged; excluding only those untouched Analytics paths allows the entire affected contract/components to validate. This is not a claim that the baseline global document is fully valid.

No SLA/automatic deadlines, financial correction writer, account unlock, diagnostic test trade, provider probe, guessed quote eligibility, invented historical settlement or fabricated account-only order archive was added. Current/account-wide diagnostics expose missing observations and policy NOT_APPLICABLE/UNAVAILABLE explicitly. Assignment is never an entitlement; purpose policy, live account grants, reviewer eligibility and source/context/export authority remain independent. Private workflow/referral changes never publish a client response; deliberate checked resolution does. Unknown outcomes retain original keys, including an unconfirmed receipt lookup.

Production grant/purpose approval, deployment execution, production backup/RTO approval and a full assistive-technology/contrast audit remain separate operational responsibilities, not claims made by these synthetic acceptance results.
