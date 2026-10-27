-- Explicit local fixture: evidence access for the primary local client; case access for both local clients.
-- Requires migrations through 017 and role_workspaces.sql. Standard local Compose runs this
-- only when local demo setup and Operations access are enabled.
BEGIN;

DO $$
BEGIN
    IF (SELECT count(*) FROM users
        WHERE enabled AND username IN ('demo.operations', 'demo.finance', 'demo.client')
          AND email = username || '@tadpoles.test') <> 3 THEN
        RAISE EXCEPTION 'Operations demo requires the three expected local demo identities';
    END IF;
END
$$;

-- Reuse active fixtures. Expired fixtures may be reissued; revoked fixtures stay revoked.
-- Seven days is a local test lifetime, not an approved production access policy.
INSERT INTO operations_access_grants (
    grant_id, staff_user_id, permission, client_id, account_reference,
    valid_from, valid_until, approved_by, approval_reference
)
SELECT gen_random_uuid(), staff.user_id, requested.permission, client.client_id,
    CASE WHEN owner.username = 'demo.client' THEN 'DEMO-CLIENT' ELSE 'DEMO-CLIENT-2' END, now() - interval '1 minute', now() + interval '7 days',
    approver.user_id, 'LOCAL-DEMO-FIXTURE-v1'
FROM users staff
CROSS JOIN users approver
CROSS JOIN clients client
JOIN users owner ON owner.user_id = client.user_id
CROSS JOIN (VALUES ('ORDER_READ'), ('EVIDENCE_READ'),
    ('RECONCILIATION_READ'), ('EVIDENCE_EXPORT'), ('CASE_READ'), ('CASE_UPDATE'), ('ACCOUNT_DIAGNOSTICS')) requested(permission)
WHERE staff.username = 'demo.operations' AND staff.email = 'demo.operations@tadpoles.test'
    AND approver.username = 'demo.finance' AND approver.email = 'demo.finance@tadpoles.test'
    AND owner.username IN ('demo.client', 'demo.client2')
    AND owner.email = owner.username || '@tadpoles.test'
    AND (owner.username = 'demo.client' OR requested.permission IN ('CASE_READ', 'CASE_UPDATE'))
    AND NOT EXISTS (
        SELECT 1 FROM operations_access_grants existing
        WHERE existing.staff_user_id = staff.user_id
            AND existing.client_id = client.client_id
            AND existing.permission = requested.permission
            AND existing.approval_reference = 'LOCAL-DEMO-FIXTURE-v1'
            AND (existing.valid_until > now() OR existing.revoked_at IS NOT NULL)
    );

COMMIT;
