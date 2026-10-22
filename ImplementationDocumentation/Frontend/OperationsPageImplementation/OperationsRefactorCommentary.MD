# Managed support integration: engineering record

## Starting point and read gate

Work began from commit `06384c92da7e6ecd282616c743068dfb08df8647` with substantial existing modified and untracked Operations implementation. That work is preserved. No repository or ancestor agent instructions were found. The complete first-party inventory was read before implementation: application source, templates/styles, tests, migrations/fresh schema, fixtures, deployment and verification scripts, API contract and documentation, including both frontend implementation directories and the complete business requirements baseline. Dependencies, compiled output, generated binary artifacts and secrets were excluded. Configuration loading and names were inspected without publishing secret values. The original session retained the read inventory and 84 reading batches under `/tmp/refactor-read-inventory.txt` and `/tmp/operator-read/`.

Baseline command: `bash scripts/test-role-workspaces.sh`, using disposable PostgreSQL 15, Java 21 and Node 22 containers. Observed: backend 383 tests, database 19 tests, frontend 159 tests in 26 files; no failures or skipped tests; style and production build passed. Diagnostic log: `/tmp/operator-refactor-baseline.log`. These are this run's observations, not historical documentation totals.

Corrections to earlier assessments: Operations evidence, scoped search, export and reconciliation reads already exist. External purpose references are legitimate metadata. No case aggregate, owned conversation, case permission, public update watermark or managed-reference binding exists. Client quote data currently passes through a broad JSON projection; event detail stops at 100 entries; polling silently exhausts. Instrument availability omits side-specific controls even though admission enforces them. These observations require behavioral tests before changes.

## Necessity and chosen contracts

Browser storage and immutable expiring purpose contexts cannot represent a durable owned conversation. Trading outbox facts cannot represent support delivery or recipient read state. Focused PostgreSQL persistence, MyBatis queries and transactional Spring services are therefore necessary. They will not write trading balances, reservations, jobs, fills or financial evidence. Migration 012 is the next unused version; migrations 003–011 remain unchanged. Fresh DDL, runtime grants, readiness and parity/recovery checks must follow the added contract.

Input contract: categories `ORDER_QUESTION`, `ACCOUNT_REVIEW`, `HISTORICAL_RECORD`, `TRADING_AVAILABILITY`, `OTHER`; subject 5–120 and description/message 10–4000 characters after trimming. Reject ISO control characters except newline and tab in prose; reject all controls in subjects. Render as escaped plain text. Reject unknown request fields rather than accepting client-supplied staff or ownership metadata. Public references use `CASE-` plus a random UUID. Request keys are 16–128 characters from letters, digits, hyphen and underscore.

The case owns an optional order through a composite owner/order relationship. Public history is append-only and drives public status, version and updated time. Private notes use separate storage and do not change public version, counts, timestamps or notifications. An unassigned queue means all currently authorized cases; assignment is deferred because it is unnecessary for this handoff. `CASE_READ` and `CASE_UPDATE` are independent account permissions; neither grants evidence access.

Creation locks the owner before idempotency lookup and atomically retains the normalized request hash, case, first public event and audit. Client prose must not be persisted in browser storage. Recovery may retain an owner-scoped opaque key only; a missing lookup does not authorize silently replacing that key. Public writes serialize on the case and require expected public version; replay lookup precedes version validation. Private-note concurrency does not expose an internal version to clients.

Managed and external contexts must be explicitly distinguished. Managed contexts resolve a retained case and constrain every evidence read to its account and optional order, with live case and evidence grants checked independently. Existing contexts remain external; no cases are fabricated during upgrade. Export associations retain identity/digest without changing bytes, expiry or download permissions.

## Requirements and delivery ledger

| Capability | Business purpose | Implementation and evidence | Status |
| --- | --- | --- | --- |
| C1 | Explicit enhancement; BR-02/11/14/15 | Owned durable creation, atomic replay, PostgreSQL ownership/concurrency tests | Implemented and verified; limits below |
| C2 | Explicit handoff; BR-02/03 | Owned and scoped lists/detail, bounded stable paging, guards/navigation | Implemented and verified; limits below |
| C3 | Explicit workflow; BR-02/14/15 | Separate private notes/public replies, transitions, locking and replay | Implemented and verified; limits below |
| C4 | BR-02/11/14/15 | Explicit managed binding, independent live grants, export association | Implemented and verified; limits below |
| C5 | BR-05/08–11/13–15 | Allowlisted retained evidence and complete event paging | Implemented and verified; limits below |
| C6 | BR-03/06/07/09–11 | Visible polling health/reconnect and preserved request recovery | Implemented and verified; limits below |
| C7 | Explicit follow-through; BR-02/03 | Durable owned public updates and idempotent read watermark | Implemented and verified; limits below |
| C8 | Explicit integration; BR-02/05/10/14/15 | Account-help links and tested public side controls | Implemented and verified; limits below |

The baseline distinguishes source requirements, subsequent decisions and proposed designs. This support enhancement does not complete external execution, settlement, notifications, additional currencies/markets, analytics isolation or a general helpdesk. Resolution changes support state only.

## Verification ledger

The entries retain the actual sequence of RED/GREEN observations. Intermediate pending statements describe that point in the work; final dispositions and observed checks are recorded at the end. No unrun check is counted as passed.

### Foundation and conversation iterations

- Creation RED: three PostgreSQL HTTP behaviors failed with HTTP 404, before any case implementation (`/tmp/support-red.log`). After the schema/service/controller slice, all three passed (`/tmp/support-green.log`), including concurrent same-key creation, conflict replay and cross-client denial.
- Queue RED: scoped staff queue returned 404; atomic audit rollback already passed. Five support tests then passed (`/tmp/support-queue-green.log`). The queue uses live `CASE_READ` account scope before count/limit, without assignment or a purpose-context prerequisite. Evidence investigation still requires the separate approved purpose policy.
- Fresh/upgrade RED: adding migration 012 to the parity test exposed missing canonical fresh objects. After adding fresh DDL, parity passed (`/tmp/support-schema-green.log`). Migration 012 remains an unpublished development migration and has only been applied to disposable test databases in this work.
- Frontend RED: missing routes and return policy caused three behavioral failures while the existing 159 tests passed (`/tmp/support-ui-red.log`). A subsequent compile error from the wrong existing API class name was corrected; it is not counted as RED. Creation/recovery and inert rendering/session clearing then passed. The receipt-to-case link had a separate failing assertion before implementation (`/tmp/support-ui-link-red.log`).
- Conversation RED: public/private actions returned 404 (`/tmp/support-lifecycle-red.log`). Implementation retains messages, transitions and actor-scoped replay receipts atomically. A replay assertion initially compared JSON property order; it was corrected to compare parsed JSON because PostgreSQL JSONB does not preserve property order. The semantic receipt must remain identical.
- Public reply UI and explicit reference-type requests each had behavioral RED assertions. The resulting frontend suite passed all 163 tests in 27 files (`/tmp/support-conversation-ui-green.log`).
- Managed binding RED: an unrelated same-account order incorrectly returned 200 rather than 404. Update-feed RED: endpoint returned 404 (`/tmp/support-binding-red.log`). Fixes add managed account/order SQL predicates and live case grants, plus a public-only cursor and recipient watermark. A missing MyBatis UUID parameter annotation was found during verification and corrected; verification continues.

Public writes acquire the client lock before the case lock. This serializes event allocation and commit for one recipient, avoiding an update cursor advancing beyond an earlier uncommitted event. Private notes use separate rows, do not advance public version and never enter the update sequence. Successful read audits commit before responses; denial auditing uses the existing independent transaction. Case writes require both `CASE_UPDATE` and `CASE_READ`; assignment is deliberately omitted. No support grants have been added to retained accounts.


### Evidence, updates and control iterations

- Evidence/export recovery RED: three behaviors exposed a misleading pending evidence label and absent export association/write-recovery endpoints. The focused implementation added safe client quote/check projections, actor-scoped retained action receipts and authorized export associations. A price assertion was corrected from fixed-scale `50.0000` to the existing canonical exact-decimal string `50`; this does not introduce floating-point arithmetic.
- Update-health and cache RED: absent health/update behaviors and retained protected content after a 403 failed their tests. Health signals, bounded reconnect, public update polling and non-transient authorization-failure clearing then passed. Transient errors keep only same-identity authorized data, visibly stale.
- Evidence/help/control UI RED: missing retained quote/help rendering and a disabled-side assertion failed. The native radio binding was corrected so the prohibited side actually remains disabled. The resulting full run passed 396 backend, 19 database and 168 frontend tests, styles and production build. Those were the observations before the additional projection/read-state guards and final validation refinements.
- Projection RED: directly advancing a case version without a retained event succeeded when the test required rejection. Deferred database consistency triggers now require the case projection to match retained public history; recipient watermarks also enforce ownership and monotonicity. Runtime grants revoke table-wide updates before granting only projection/read-state columns.

The temporary `/tmp` logs listed above were available in the original session but did not survive the subsequent environment restart. Their outcomes are recorded as contemporaneous observations, not currently downloadable evidence. Fresh verification logs are being generated for the final tree. No retained application database was reset or upgraded during this work.

### Final verification in progress

On 20 October 2026 the current full backend run passed 397 tests with no failures, errors or skips, including the projection guard. The corresponding database/frontend stages continue in `/tmp/support-final-regression.log`. The hardened Compose contract passed (`/tmp/support-hardened.log`), checking separately provisioned runtime credentials, no inherited owner environment and no implicit demo/auth-stub startup.

A further focused UI RED (`/tmp/support-validation-ui-red.log`) exposed that a definitive 400 validation rejection retained an unsendable original draft. The correction clears rejected action metadata and permits corrected content; transport uncertainty and ambiguous/conflicting replay still preserve the original key. Explicitly rejected lifecycle actions refresh the current conversation. This is distinct from a recovery lookup returning 404, which never silently replaces the uncertain key.

### Delivered file and contract map

| Capability | Principal files/contracts | Behavioral coverage |
| --- | --- | --- |
| C1 | Support controllers/service/mapper; migration 012; support API/form | Owned optional-order creation, normalized same-key replay/conflict, concurrent creation, rollback on required audit failure, opaque-key recovery |
| C2 | Support routes/workspace policy/shell; shared support page; scoped mapper queries | Owned search/list/detail, SQL scope before counts/paging, live grant revocation, client/staff guards and session clearing |
| C3 | Separate events/notes/requests; expected-version actions | Public/private byte-equivalent client projection, explicit transitions, reopen, actor replay recovery and concurrent conflict |
| C4 | Operations context DTO/access predicates; support export service/link table | Managed case resolution, unrelated-order denial, live case/evidence grant checks, forged external type denial, export digest/permission checks |
| C5 | TradingMapper client evidence queries; TradingServiceImpl allowlist; client order detail | Pending evidence and exact quote projection, bounded retained checks, timeline paging and historical warnings |
| C6 | OrderStatusService; OrderUpdateHealthComponent; PageResource; existing order ticket | Failure/exhaustion/resume, protected-cache clearing, stable financial request recovery under policy changes |
| C7 | Public event feed/read state; SupportUpdatesComponent | Explicit owned/idempotent read acknowledgement, private activity invisibility, durable cursor/watermark, recovery harness |
| C8 | Account/instrument/order support links; availability flags; order ticket | Account/historical/trading help paths, prohibited-side disabling, live instrument refresh and authoritative server revalidation |

Public/private separation is structural, not merely a hidden UI field. Private notes and case-export associations do not alter any client-visible public count, timestamp or version. Case grants are new independently approved permissions, not inferred from a role or an existing evidence grant. Existing external contexts and financial evidence remain intact. No new financial writer or automatic grant widening was introduced.

Two boundary tests subsequently failed behaviorally: raw leading/trailing control characters were accepted after trimming, and a disabled instrument still exposed allowed sides when a missing bank reference supplied the first availability reason (`/tmp/support-boundary-red-confirmed.log`). Both now pass (`/tmp/support-boundary-green.log`). The first boundary run also contained an invalid bank fixture (deactivating a default reference without clearing its default flag); that setup error was corrected before counting the availability assertion as RED. Client/server controls now examine raw input, and instrument/account eligibility constrains side guidance independently of the first displayed prerequisite.

The full intermediate run completed with 397 backend, 19 database and 169 frontend tests passing, plus style checks and production build (`/tmp/support-final-regression.log`). Later boundary fixes and Java formatting are undergoing a final regression. An additional financial snapshot regression compares all order, cash, position, journal, reservation, job, event, quote and decision rows before/after complete support lifecycles for pending, filled and rejected receipts; it is additional acceptance coverage, not claimed as a test-first implementation history.

### Final retained observations

The full backend reports now contain **400 tests, zero failures/errors/skips**, including 50 PostgreSQL trading/support integration tests and the new raw-input test. Database reports contain **19 tests, zero failures/errors/skips**. These XML reports survived the session restart in `backend/target/surefire-reports/` and `testing/target/surefire-reports/` and were inspected again on 20 October 2026.

The extended recovery rehearsal passed before the restart. Observed dump/restore time was 4.115 seconds for the isolated fixture, with zero loss relative to that captured snapshot and byte-identical exports. It verified durable cases, separate public/private history, action receipt recovery, unread watermarks, managed export associations, existing external contexts, explicit grants, restricted-runtime case writes and immutable-history protections, as well as the existing financial restart/replay/reconciliation checks. The timing is fixture evidence, not a production objective.

A concurrent-response authorization test then failed because a 403 from the update endpoint did not cancel a pending case-list response (`support-late-denial-red.log`, temporary original-session log). Support requests now share a denial cancellation signal; the list, drafts, notes and update feed clear together. This closes the late-response repopulation path, in addition to session-change cancellation. The final frontend suite is being rerun with its log retained under `backend/target/operator-verification/`.

The final frontend run passed **170 tests in 28 files**, style checks and production build. This includes the late-denial cancellation regression, which now passes. Evidence: `backend/target/operator-verification/frontend.log`. A machine-readable summary of retained backend/database reports is in `backend/target/operator-verification/test-summary.json`. These build artifacts are ignored by Git; the engineering record retains the observed results.


## Final disposition and handover

C1–C8 are implemented. The final verification completed with the following observations:

| Check | Observed result | Evidence |
| --- | --- | --- |
| Backend | 400 passed; zero failures/errors/skips | Retained Surefire XML; `backend/target/operator-verification/test-summary.json` |
| Database | 19 passed; zero failures/errors/skips, including fresh/upgrade parity | Database Surefire XML and test summary |
| Frontend | 170 passed across 28 files | `backend/target/operator-verification/frontend.log` |
| Style and production build | Passed | Same frontend log |
| Hardened configuration | Passed; separately provisioned runtime and no implicit demo/stub startup | Original run recorded above |
| Restart/dump/restore and export verification | Passed, including restricted runtime and unchanged exported bytes | Original recovery observation above; executable `scripts/test-client-recovery.py` |
| Isolated Compose fresh/retained upgrade | Passed, including account case recovery after restart and schema version 12 | `backend/target/operator-verification/compose.log` |
| Existing Operations demo | Passed; four unchanged repeatable evidence grants, external purpose, scoped export and revocation | `backend/target/operator-verification/operations-demo.log` |
| Browser | Order-to-case creation, reload, staff queue, managed investigation, private note, public reply, awaiting-client/client follow-up, resolution/reopen and explicit unread acknowledgement passed | Browser observations below and ignored screenshot artifacts |
| Formatting/whitespace/API syntax | Checked on touched files; no whitespace errors | Final checks at handover |

### Browser observations

The browser used a uniquely named disposable PostgreSQL/API/UI/HTTP-provider fixture. One synthetic order was created through the normal admission API; no retained user environment was used. Six explicit fixture-only permissions were granted for one client account, without changing ordinary demo or retained grants.

The Client followed the owned filled receipt's support link, entered a plain-text question using keyboard Tab/Enter navigation and received a generated reference. The same reference and conversation persisted after reload. Operations found the permitted unassigned case, retained a separate private note, created the verified managed context, opened only its linked order, generated an authorized export and retained its association. The UI confirmed that original export permission/expiry rules still applied.

Operations sent a public explanation and requested information. The Client saw three public unread events (creation, reply and transition), explicitly acknowledged them to zero, read the reply without the private-note text, and responded. The case moved from AWAITING_CLIENT to IN_REVIEW. Operations resolved it with a public explanation; the Client saw that explanation and reopened it with a reason, producing another attributable public event. The underlying receipt remained filled.

A 390-by-844 viewport showed readable wrapping of the long case reference, usable controls and the shared mobile workspace navigation. The temporary viewport override was reset. Screenshot evidence is retained at `backend/target/operator-verification/support-mobile.jpg` and `support-result.jpg`. The second Client then saw zero unread updates and “No matching cases”; direct navigation to the first Client's reference showed “This record could not be found” without conversation content. Returning to the owner recovered the reopened case.

The first review fixture mistakenly set a context lifetime above the existing 3600-second policy maximum; the UI correctly withheld investigation access. Correcting the fixture configuration and restarting only its app containers preserved the case and enabled the verified purpose flow. This was a setup correction, not a relaxed production access rule.

### Deployment dependencies and honest limits

Apply migration 012, reapply restricted runtime grants and provision approved account-specific CASE_READ/CASE_UPDATE permissions before deploying the case journey. Existing grants are deliberately unchanged. New purpose requests require explicit referenceType. No retained database, production deployment or real-provider account was modified in this implementation.

Verification used PostgreSQL 15, Java 21, Node 22 and an explicit HTTP market fixture. It establishes retained internal behavior and scoped access, not live-provider reliability, external settlement, external notification delivery, production recovery objectives or an exhaustive browser/accessibility certification. The browser check covered keyboard submission and one narrow viewport; automated tests cover failure/replay, version conflicts, paging, expiry/revocation and concurrent responses. The measured restore time is fixture-specific.

The case detail uses explicit refresh; the Support list/feed polls for a bounded period and offers resume. No attachments, assignments, SLA guarantees, external helpdesk sync, external notification channel, financial repair or new financial writer is included. There are no known remaining implementation blockers for this integration. Existing wider platform gaps remain outside this refactor.

The task-created browser review containers/network were removed after verification. Existing user database/auth services were left running. Logs, test summaries and screenshots remain in the ignored build directory. Changes are left in the working tree for review; no commit, push or deployment was performed.


## Requested follow-up — 21 October 2026

The user requested working standard Compose defaults, consistent case-navigation spacing and an Operations queue showing all report categories without repeated searches. The original all-category SQL was correct; missing default local CASE permissions and absent queue refresh/category labels created the reported experience.

The scope change is explicit local fixture provisioning authorized by this follow-up: CASE_READ/CASE_UPDATE for both seeded local clients, while existing evidence grants stay limited to the primary local client. Runtime authorization, hardened provisioning, case ownership and migration history are unchanged. No retained database was reset. The seed preserves active grants and revocations on restart.

RED evidence: `backend/target/operator-followup/compose-red.log` failed the default-startup eight-grant assertion against the original four-grant seed. `ui-red.log` failed two behavioral assertions for the accessible spaced navigation group and category display. `ui-green.log` then passed eight support tests after adding the shared actions layout, category column and catalog-based staff queue refresh. Full frontend, disposable Compose and hardened configuration verification follow below. Backend Java/schema are untouched by this follow-up, so the earlier complete backend/database results remain historical evidence rather than being claimed as rerun.

Follow-up final results: **172 frontend tests passed across 28 files**, shared style checks and production build passed (`frontend-regression.log`); disposable standard Compose passed all-category cases for both local clients, public replies, existing investigation/export and retained revocation (`compose-green.log`); hardened configuration passed (`hardened.log`). Python syntax and whitespace checks passed. The harness removed only its disposable Compose resources. No retained runtime was redeployed; normal build/up applies the corrected seed.
