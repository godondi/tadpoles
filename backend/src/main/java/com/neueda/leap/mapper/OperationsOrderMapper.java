package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface OperationsOrderMapper {
    String COLUMNS =
            """
t.trade_id AS "orderId", t.client_id AS "clientId",
(SELECT min(g.account_reference) FROM operations_access_grants g
 WHERE g.staff_user_id=#{actor} AND g.permission=#{permission} AND g.client_id=t.client_id
   AND g.revoked_at IS NULL AND g.valid_from &lt;= #{now} AND g.valid_until > #{now}) AS "accountReference",
t.instrument_id AS "instrumentId", i.ticker, t.trade_type AS side, t.quantity::text AS quantity,
t.currency_snapshot AS currency, t.record_kind AS "recordKind",
CASE WHEN t.record_kind='SELF_DIRECTED' THEN 'INTERNAL' ELSE 'LEGACY_UNKNOWN' END AS "executionMode",
t.status, t.submitted_at AS "submittedAt", t.accepted_at AS "acceptedAt",
t.updated_at AS "updatedAt", coalesce(t.version,0) AS version,
t.rejection_stage AS "rejectionStage", t.rejection_code AS "rejectionCode", t.reason,
CASE WHEN t.record_kind &lt;&gt; 'SELF_DIRECTED' THEN 'INCOMPLETE_LEGACY'
     WHEN t.status IN ('ACCEPTED','EXECUTING') THEN 'PENDING' ELSE 'NOT_CHECKED' END AS "evidenceStatus",
CASE WHEN t.status='REJECTED' THEN coalesce(t.rejection_stage,'LEGACY') || '_REJECTION'
     WHEN NOT j.completed AND j.deadline &lt; #{now} THEN 'DELAYED'
     WHEN NOT j.completed AND j.lease_until &lt; #{now} THEN 'EXPIRED_LEASE'
     WHEN NOT j.completed AND j.attempts > 1 THEN 'RETRYING'
     ELSE NULL END AS "queueCategory",
j.deadline, j.next_attempt AS "nextAttempt", j.lease_until AS "leaseUntil",
coalesce(j.attempts,0) AS attempts,
t.submitted_by_user_id AS "submittedActor", t.policy_snapshot::text AS "policySnapshot"
""";
    String FROM =
            """
            FROM client_trades t JOIN instruments i USING(instrument_id)
            LEFT JOIN order_execution_jobs j ON j.order_id=t.trade_id
            WHERE
            """
                    + "<![CDATA["
                    + OperationsAccessMapper.SCOPE
                    + "]]>";

    @Options(timeout = 5)
    @Select(
            """
            <script>SELECT
            """
                    + COLUMNS
                    + FROM
                    + """
<if test='orderId != null'>AND t.trade_id=#{orderId}</if>
<if test='accountReference != null'>AND EXISTS (
  SELECT 1 FROM operations_access_grants g WHERE g.staff_user_id=#{actor}
    AND g.permission=#{permission} AND g.client_id=t.client_id AND g.account_reference=#{accountReference}
    AND g.revoked_at IS NULL AND g.valid_from &lt;= #{now} AND g.valid_until &gt; #{now})</if>
<if test='instrumentId != null'>AND t.instrument_id=#{instrumentId}</if>
<if test='status != null'>AND t.status=#{status}</if>
<if test='side != null'>AND t.trade_type=#{side}</if>
<if test='recordKind != null'>AND t.record_kind=#{recordKind}</if>
<if test='rejectionStage != null'>AND t.rejection_stage=#{rejectionStage}</if>
<if test='from != null'>AND t.submitted_at &gt;= #{from} AND t.submitted_at &lt; #{to}</if>
<if test='queue == "REJECTED"'>AND t.status='REJECTED'</if>
<if test='queue == "DELAYED"'>AND NOT j.completed AND j.deadline &lt; #{now}</if>
<if test='queue == "RETRYING"'>AND NOT j.completed AND (j.attempts &gt; 1 OR j.lease_until &lt; #{now})</if>
<if test='cursorTime != null'>AND (t.submitted_at,t.trade_id) &lt; (#{cursorTime},#{cursorId})</if>
ORDER BY t.submitted_at DESC NULLS LAST,t.trade_id DESC LIMIT #{limit}
</script>
""")
    List<Map<String, Object>> orders(Map<String, Object> parameters);

    @Options(timeout = 5)
    @Select("<script>SELECT " + COLUMNS + FROM + " AND t.trade_id=#{id}</script>")
    Map<String, Object> order(Map<String, Object> parameters);

    @Options(timeout = 5)
    @SelectProvider(type = OperationsEvidenceSql.class, method = "query")
    List<Map<String, Object>> section(Map<String, Object> parameters);

    @Options(timeout = 5)
    @Select(
            """
            SELECT p.policy_version AS version,p.revision,p.enabled,i.is_active AS active,
                   p.buy_allowed AS "buyAllowed",p.sell_allowed AS "sellAllowed"
            FROM instrument_trading_policies p JOIN instruments i USING(instrument_id)
            WHERE p.instrument_id=#{id}
            """)
    Map<String, Object> currentPolicy(int id);

    @Options(timeout = 5)
    @Select(
            """
SELECT audit_id::text AS reference,recorded_at AS "recordedAt",actor_type AS "actorType",
       CASE WHEN actor IS NULL THEN 'SYSTEM' ELSE 'user:' || actor END AS actor,
       reason,before_values::text AS "beforeValues",after_values::text AS "afterValues"
FROM instrument_policy_audit WHERE instrument_id=#{id}
ORDER BY recorded_at DESC,audit_id DESC LIMIT #{limit} OFFSET #{offset}
""")
    List<Map<String, Object>> history(
            @Param("id") int id, @Param("limit") int limit, @Param("offset") int offset);
}
