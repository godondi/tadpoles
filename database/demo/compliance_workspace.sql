-- Explicit local fixture: Compliance evidence/export access for only demo.client.
-- Hardened deployments must provision grants through a separately approved process.
BEGIN;

DO $$
BEGIN
    IF (SELECT count(*) FROM users
        WHERE enabled AND username IN ('demo.compliance', 'demo.finance', 'demo.client')
          AND email = username || '@tadpoles.test') <> 3 THEN
        RAISE EXCEPTION 'Compliance demo requires the expected local identities';
    END IF;
END
$$;

INSERT INTO compliance_access_grants (
    grant_id,staff_user_id,permission,client_id,account_reference,
    valid_from,valid_until,approved_by,approval_reference)
SELECT gen_random_uuid(),staff.user_id,requested.permission,client.client_id,'DEMO-CLIENT',
       now() - interval '1 minute',now() + interval '7 days',
       approver.user_id,'LOCAL-COMPLIANCE-FIXTURE-v1'
FROM users staff
CROSS JOIN users approver
CROSS JOIN clients client
JOIN users owner ON owner.user_id=client.user_id
CROSS JOIN (VALUES ('EVIDENCE_READ'),('EXPORT_CREATE'),('EXPORT_DOWNLOAD')) requested(permission)
WHERE staff.username='demo.compliance'
  AND staff.email='demo.compliance@tadpoles.test'
  AND approver.username='demo.finance'
  AND approver.email='demo.finance@tadpoles.test'
  AND owner.username='demo.client'
  AND owner.email='demo.client@tadpoles.test'
  AND NOT EXISTS (
      SELECT 1
      FROM compliance_access_grants existing
      WHERE existing.staff_user_id=staff.user_id
        AND existing.client_id=client.client_id
        AND existing.permission=requested.permission
        AND existing.approval_reference='LOCAL-COMPLIANCE-FIXTURE-v1'
        AND (existing.valid_until > now() OR existing.revoked_at IS NOT NULL));

COMMIT;
