# Operations incident runbook

## Access and evidence first

Use a staff identity, an approved purpose/case reference, and the minimum account scope. Keep the original order reference and UTC times in the case. Never send bank details, tokens, or unrestricted account evidence to an external incident channel. If the audit store is unavailable, sensitive reads stop; restore that dependency before investigating through the application.

## Inspect an order

Search the exact order reference, confirm account scope, and open investigation. Read submission and acceptance separately. Follow lifecycle sequence, retained decisions/quotes, attempts, reservation, fill and exact signed postings. Inspect completeness warnings before exporting. Preserve the export and displayed digest through the approved case process. An empty permitted result is not proof that an order does not exist globally.

## Stale quotes or provider trouble

1. Compare provider transport observation time with the order's retained quote times and freshness flags.
2. Distinguish timeout/connection, credentials rejection, rate limit, missing metadata, explicit stale data and closed market.
3. Inspect accepted policy age limits and quote-side selection. Do not widen limits merely to make a health badge green.
4. Use approved credential/network support procedures. Dashboard refresh does not trigger a provider probe.
5. Recheck the original order after normal recovery; do not create another instruction or force a fill.

## Stopped or failing worker

1. Read the process-local worker status, poll/completion/failure timestamps and configuration.
2. Open the delayed or retrying queue within your account scope.
3. Preserve accepted commitments. Stop new admission through deployment controls if the incident process requires it, while allowing existing work to drain where safe.
4. Recover the worker/dependency through the deployment process. Existing jobs, leases and idempotency controls resume the original work.
5. Confirm each affected order reaches a justified terminal outcome and postings reconcile. Do not delete jobs, release reservations manually, reset attempts, or disable evidence guards.

## Apply an authorized instrument halt

Review the exact instrument, current revision, reason and effect on pending orders. A full halt also prevents exits. Sell-only preserves permitted exits but still requires provider validation. Existing full halts can be saved during an outage. Save with a meaningful reason, verify policy history and affected orders, and record the action in the case. A revision conflict requires rereading the policy. Changes apply immediately; independent approval is not built into this control.

## Reconciliation mismatch

Read the stored run time and permitted findings. Distinguish internal accounting consistency from external custody or bank reconciliation. A clean page does not resolve historical quarantines or hidden findings. Follow linked orders and retain the evidence. Escalate through the designated Finance/engineering process; Operations cannot run or repair reconciliation. Any correction requires a separately authorized compensating-entry design.

## Deployment failure and rollback

Use the ordered migration runner and keep fresh/upgrade parity. Application rollback does not reverse database writes or schema migrations. Preserve accepted orders and source evidence. Diagnose the first migration/dependency failure; do not mark a version applied without its objects. Restore/redeploy compatible software through the deployment process. Reapply restricted runtime grants for new tables and verify evidence protection before enabling account reads.

## Isolated backup recovery rehearsal

The repository's `scripts/test-client-recovery.py` creates a disposable network/database/provider/API, never a retained Compose volume. It requires Docker, a freshly packaged backend JAR and cached/pullable test images. It exercises a captured dump, restore into another disposable database, original accepted-order recovery, exact exported-byte preservation, grant/context/access-event preservation, restricted-runtime reads/exports/booking and protected-table mutation denial. It prints elapsed dump/restore time and removes its own resources.

```sh
mvn -B -f backend/pom.xml -DskipTests package
python3 scripts/test-client-recovery.py
```

Measured rehearsal timing describes this fixture, not a production recovery objective. The measured loss window is relative to the captured backup snapshot; point-in-time recovery and changes committed after that snapshot are separate infrastructure responsibilities.

## Monitoring handover

The application exposes passive process-local observations and scoped investigation evidence. Deployment owners must define alert thresholds, severity, responsible owner, escalation destination, deduplication and recovery conditions. No external alert integration, production retention period, multi-instance aggregate, independently anchored integrity service or staff MFA policy is automatically provisioned by this feature.

## Managed client cases

Clients open owned cases through Support or an order receipt. The Operations case queue requires explicit, live `CASE_READ` account grants; public replies, private notes, transitions and export associations also require `CASE_UPDATE`. Role membership or knowing a reference does not grant case or evidence access. No existing account grants are widened by migration.

New purpose requests must supply `referenceType: MANAGED` for a retained case or `referenceType: EXTERNAL` for an approved operational reference. Managed contexts bind the case account and optional order and recheck case authority independently of evidence permissions. Historical contexts remain external. Start a new current context after expiry or purpose-policy change.

Keep public explanations and private notes separate. Resolution requires a public explanation and does not alter financial evidence. An authorized export can be associated with its managed case without changing original requester/context, digest or expiry checks. Clients cannot download this private association. If a write response is lost, recover the same request key before issuing another action.

Deploy migration 012 and restricted runtime grants before using the case routes. Check `supportSchemaReady` and evidence protection. The recovery rehearsal now includes cases, both visibility classes, action receipts, recipient watermarks, managed export associations and unchanged external contexts. For detailed walkthroughs and limitations, see [the managed support guide](../ImplementationDocumentation/Frontend/OperationsPageImplementation/OperationsRefactorCrashCourse.MD) and its adjacent engineering record.


For standard local Compose, the repeatable fixture seed now provisions CASE_READ/CASE_UPDATE for both seeded local client accounts, alongside the primary client's existing evidence permissions. Build/up runs that seed automatically. Hardened deployments still exclude fixture provisioning. The case queue includes every report category without searching, displays categories, and refreshes visible rows every 15 seconds or on focus; optional filters and pagination remain available.
