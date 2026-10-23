# Compliance and Operations documentation plan

Date: 2026-10-23. Baseline commit: `9c4b959d720bfb4984b22a7371f803dfbd5b2b14`.

## Requested deliverables and scope

Create `ComplianceOperationsRefactor.MD` at the repository root. Document the current behavior of every support category, Operations workflows, and Compliance investigations; explain the gaps; provide implementable instructions for each ticket type and for making Compliance feel consistent with Operations while correcting Operations styling. This task produces instructions only, as clarified by the user. No application, schema, configuration, or runtime changes are part of this delivery.

This separate plan is created before drafting the requested refactor document. The working tree was clean at discovery. No applicable repository `AGENTS.md` was found.

## Review and drafting sequence

1. Inventory the entire repository, including uppercase `.MD` documentation, frontend, backend, schema/migrations, analytics, tests, fixtures, and deployment scripts. Read architecture and historical documents, then reconcile their assertions against current executable source. Generated files and dependencies are not authoritative application behavior.
2. Trace every support category from client entry point through DTO/controller/service/SQL, public/private history, transitions, evidence access, export, updates, and tests. Distinguish support categories, trading orders, and Compliance investigations.
3. Trace Operations and Compliance route guards, scoped permissions, purpose contexts, evidence/search/export contracts, and failure recovery. Identify implemented features versus configuration-dependent availability and historical documentation drift.
4. Inspect both roles' templates, styles, shared shell, navigation, loading/error states, responsiveness, and CSS checks. Record source-confirmed defects separately from visual issues requiring browser reproduction.
5. Before recommending any larger change, record its analysis and expected affected files/contracts here. Define dependencies, migration requirements, security invariants, rollback implications, and acceptance criteria in the main document before anyone implements it.
6. Draft the main document with source links, current-state tables, one actionable playbook per category, UI specifications, isolated work packages, shared-file ownership, integration order, and verification commands.
7. Verify source paths, category/status completeness, Markdown links, whitespace, and final diff. Report documentation-only validation separately from application tests, which are not rerun for this documentation task.

## Parallel development contract for subsequent implementation

Parallel work is a property of the proposed task breakdown; no implementation agents are being dispatched for this documentation-only request. Define one owner for shared contracts, migrations, `docs/api.yaml`, routing, shared CSS, and common tests. Feature branches start after the contracts they consume are merged. Each task must specify inputs, owned files, prohibited overlapping edits, dependencies, observable outcomes, and relevant tests. Migration numbers must be reserved centrally before parallel database work.

## Change analysis ledger

### A. Existing Compliance contract and deployment defects

Source analysis found a duplicate `scope_version` column addition between migrations 013 and 014, invalid table-qualified `e.reference` ordering in the Compliance events/attempts SQL, UTC input serialization mismatch, status filters missing the persisted `EXECUTING` state, filter-return state that is not restored, and incomplete export recovery/download UI. These are prerequisites to an honest usable workspace. Expected files: Compliance mapper/service/controller tests, both migration paths and fresh-schema/parity tests, Compliance API/models/pages/specs, and the API documentation. A migration owner must first establish which historical migration variants have been deployed; a later migration cannot repair an earlier failing step. Do not silently rewrite applied migration history or claim that fresh-schema tests validate upgrades.

### B. Category-specific case handling

All five categories currently use the same create payload and transition rules. Only an optional owned order is structurally linked. Case identity/category/subject are immutable; private activity must not affect public version or unread state. Adding structured intake, classification corrections, assignment, priority, tasks, deadlines and resolution records therefore requires an additive private workflow projection/history, explicit public DTO allowlists, separately versioned private commands, stable replay keys and database grants. Expected files: new support workflow DTO/service/mapper classes, a centrally owned migration and fresh/runtime-grant updates, thin integration in existing support controllers/services/API, isolated category components, and new tests. Preserve original category and add an effective classification rather than weakening `guard_support_case`.

### C. Compliance referral and investigation lifecycle

Compliance currently searches orders; it has no managed support-case lifecycle, case binding or referral queue. A typed case reference is purpose metadata, not a support-case relationship. Expected changes: new referral/investigation records and append-only events, dedicated Compliance permissions and scoped endpoints, purpose-context binding, internal disposition, an Operations-visible handoff receipt, new Compliance screens, and route/policy integration. Do not add COMPLIANCE to Operations case controllers or reuse Operations credentials/contexts. Account-only cases need a real case target rather than a fabricated order ID. Internal findings remain separate from public client replies.

### D. Shared visual system and role-specific layouts

The shared shell already exists. Compliance overrides global filter/detail/pagination layouts, while global table `white-space: nowrap`, fixed evidence minimum widths, inconsistent form wrappers/action groups, missing textarea focus styling, and large nested case panels need review. Expected changes: one owner for shared CSS/presentational primitives and shell breakpoints; separate owners for Operations templates and Compliance templates; category components consume the merged primitives. Keep role adapters and authorization independent. Browser reproduction at narrow, tablet, wide, zoomed, long-content, denied, stale and loading states is required before declaring visual defects fixed.

### E. Verification and integration

Existing Support coverage includes PostgreSQL HTTP lifecycle/security tests in `TradingPostgresTest`; Compliance's dedicated PostgreSQL test checks fresh DDL/immutability, not a complete live endpoint journey. Expand real mapper/API and fresh/upgrade coverage rather than relying on mock render fixtures. Future feature owners add tests in separate new files; one integrator owns existing broad suites, shared docs, migration numbering, route registration and regression evidence. The current task runs only documentation validation.

## Completion ledger

- [x] Inventory repository and existing documentation; establish baseline and documentation-only scope.
- [x] Complete repository-wide inventory/documentation survey and detailed source review of the affected workflows and their dependencies.
- [x] Record larger-change analyses and expected changes.
- [x] Write `ComplianceOperationsRefactor.MD`.
- [x] Verify documentation and final diff.

## Delivery verification

The main document has a dedicated current-state row and implementation playbook for every category in the source `CASE_CATEGORIES` array. Local Markdown links resolve; code fences are balanced; whitespace checks pass. Final working-tree scope contains only these two new Markdown files. No application tests, browser sessions, migrations or deployment commands were run. Existing test results cited by historical documents were not represented as new verification.

Future implementation starts with main-document W0 and F1/F2, then follows the dependency/ownership table. The source-confirmed defects are instructions for reproduction and repair, not fixes delivered by this documentation task.
