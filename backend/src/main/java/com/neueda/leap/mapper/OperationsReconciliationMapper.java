package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface OperationsReconciliationMapper {
    @Insert(
            """
INSERT INTO reconciliation_findings(run_id,kind,client_id,order_id,reference,detected_at)
SELECT r.run_id, finding->>'kind',
  CASE split_part(finding->>'reference',':',1)
    WHEN 'cash' THEN (SELECT client_id FROM clients WHERE client_id::text=split_part(finding->>'reference',':',2))
    WHEN 'position' THEN (SELECT client_id FROM clients WHERE client_id::text=split_part(finding->>'reference',':',2))
    WHEN 'order' THEN (SELECT client_id FROM client_trades WHERE trade_id::text=split_part(finding->>'reference',':',2))
    WHEN 'fill' THEN (SELECT t.client_id FROM order_fills f JOIN client_trades t ON t.trade_id=f.order_id WHERE f.fill_id::text=split_part(finding->>'reference',':',2))
    WHEN 'movement' THEN (SELECT client_id FROM cash_movements WHERE movement_id::text=split_part(finding->>'reference',':',2))
    WHEN 'event' THEN (SELECT client_id FROM order_events WHERE event_id::text=split_part(finding->>'reference',':',2))
  END,
  CASE split_part(finding->>'reference',':',1)
    WHEN 'order' THEN (SELECT trade_id FROM client_trades WHERE trade_id::text=split_part(finding->>'reference',':',2))
    WHEN 'fill' THEN (SELECT order_id FROM order_fills WHERE fill_id::text=split_part(finding->>'reference',':',2))
    WHEN 'event' THEN (SELECT order_id FROM order_events WHERE event_id::text=split_part(finding->>'reference',':',2))
  END,
  finding->>'reference',r.checked_at
FROM reconciliation_runs r CROSS JOIN LATERAL jsonb_array_elements(r.report->'exceptions') finding
WHERE r.run_id=#{id}
""")
    void findings(@Param("id") UUID id);

    @Options(timeout = 5)
    @Select(
            """
            <script>SELECT run_id,checked_at,report->>'evidenceVersion' AS version
            FROM reconciliation_runs
            <if test='id != null'>WHERE run_id=#{id}</if>
            ORDER BY checked_at DESC,run_id DESC LIMIT 1</script>
            """)
    Map<String, Object> run(@Param("id") UUID id);

    @Options(timeout = 5)
    @Select(
            """
SELECT f.finding_id::text AS reference,f.detected_at AS "recordedAt",f.kind,f.order_id AS "orderId"
FROM reconciliation_findings f
WHERE f.run_id=#{id} AND EXISTS (SELECT 1 FROM operations_access_grants g
  WHERE g.client_id=f.client_id AND g.staff_user_id=#{actor} AND g.permission='RECONCILIATION_READ'
  AND g.revoked_at IS NULL AND g.valid_from <= #{now} AND g.valid_until > #{now})
AND (#{managedClient}::integer IS NULL OR f.client_id=#{managedClient})
AND (#{managedOrder}::integer IS NULL OR f.order_id=#{managedOrder})
ORDER BY f.finding_id LIMIT 101 OFFSET #{offset}
""")
    List<Map<String, Object>> page(Map<String, Object> parameters);

    @Options(timeout = 5)
    @Select(
            """
SELECT f.finding_id::text AS reference,f.detected_at AS "recordedAt",f.kind,f.order_id AS "orderId"
FROM reconciliation_findings f WHERE f.run_id=#{id} AND f.order_id=#{order}
  AND EXISTS (SELECT 1 FROM operations_access_grants g WHERE g.client_id=f.client_id
    AND g.staff_user_id=#{actor} AND g.permission='RECONCILIATION_READ'
    AND g.revoked_at IS NULL AND g.valid_from <= #{now} AND g.valid_until > #{now})
AND (#{managedClient}::integer IS NULL OR f.client_id=#{managedClient})
AND (#{managedOrder}::integer IS NULL OR f.order_id=#{managedOrder})
ORDER BY f.finding_id LIMIT 2001
""")
    List<Map<String, Object>> related(Map<String, Object> parameters);

    @Options(timeout = 5)
    @Select(
            """
SELECT f.exception_id::text AS reference,f.recorded_at AS "recordedAt",f.kind
FROM financial_migration_exceptions f
WHERE EXISTS (SELECT 1 FROM operations_access_grants g
  WHERE g.client_id=f.client_id AND g.staff_user_id=#{actor} AND g.permission='RECONCILIATION_READ'
  AND g.revoked_at IS NULL AND g.valid_from <= #{now} AND g.valid_until > #{now})
AND (#{managedClient}::integer IS NULL OR f.client_id=#{managedClient})
AND #{managedOrder}::integer IS NULL
ORDER BY f.exception_id LIMIT 101 OFFSET #{offset}
""")
    List<Map<String, Object>> migration(Map<String, Object> parameters);
}
