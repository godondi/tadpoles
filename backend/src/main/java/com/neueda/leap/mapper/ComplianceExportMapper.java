package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface ComplianceExportMapper {
    @Select(
            "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{key},#{actor})))"
                    + " locked")
    int lockKey(@Param("actor") int actor, @Param("key") String key);

    @Select(
            """
            SELECT export_id::text
            FROM compliance_evidence_exports
            WHERE requester=#{actor} AND idempotency_key=#{key}
            """)
    String existing(@Param("actor") int actor, @Param("key") String key);

    @Select(
            """
SELECT e.export_id::text AS "requestReference",e.requester,e.context_id AS "contextId",
       e.order_id AS "orderId",t.client_id AS "clientId",e.scope_version AS "scopeVersion",
       e.snapshot_at AS "asOf",e.created_at AS "createdAt",e.expires_at AS "expiresAt",
       e.state,e.digest,e.manifest::text AS manifest,c.case_reference AS "purposeReference",
       u.username AS actor,c.investigation_id AS "investigationId"
FROM compliance_evidence_exports e
JOIN compliance_access_contexts c ON c.context_id=e.context_id
JOIN users u ON u.user_id=e.requester
JOIN client_trades t ON t.trade_id=e.order_id
WHERE e.export_id=#{id}
""")
    Map<String, Object> metadata(@Param("id") UUID id);

    @Select(
            """
            SELECT archive
            FROM compliance_evidence_exports
            WHERE export_id=#{id}
            """)
    Map<String, Object> archive(@Param("id") UUID id);

    String MANAGED_SCOPE =
            """
AND (#{managedClient}::integer IS NULL OR t.client_id=#{managedClient})
AND (#{managedOrder}::integer IS NULL OR t.trade_id=#{managedOrder})
AND (#{investigation}::uuid IS NULL OR e.context_id IN (
    SELECT context_id FROM compliance_access_contexts WHERE investigation_id=#{investigation}))
""";

    @Select(
            """
SELECT e.export_id::text AS "requestReference",e.requester,e.context_id AS "contextId",
       e.order_id AS "orderId",t.client_id AS "clientId",e.scope_version AS "scopeVersion",
       e.snapshot_at AS "asOf",e.created_at AS "createdAt",e.expires_at AS "expiresAt",
       e.state,e.digest,e.manifest::text AS manifest,c.case_reference AS "purposeReference",
       u.username AS actor,c.investigation_id AS "investigationId"
FROM compliance_evidence_exports e
JOIN compliance_access_contexts c ON c.context_id=e.context_id
JOIN users u ON u.user_id=e.requester
JOIN client_trades t ON t.trade_id=e.order_id
WHERE e.requester=#{actor}
  AND EXISTS (
    SELECT 1 FROM compliance_access_grants g
    WHERE g.staff_user_id=#{actor} AND g.permission='EVIDENCE_READ'
      AND g.client_id=t.client_id AND g.revoked_at IS NULL
      AND g.valid_from <= #{now} AND g.valid_until > #{now})
"""
                    + MANAGED_SCOPE
                    + """
                      ORDER BY e.created_at DESC,e.export_id DESC
                      LIMIT #{limit} OFFSET #{offset}
                      """)
    List<Map<String, Object>> history(Map<String, Object> parameters);

    @Select(
            """
            SELECT count(*)
            FROM compliance_evidence_exports e
            JOIN client_trades t ON t.trade_id=e.order_id
            WHERE e.requester=#{actor}
              AND EXISTS (
                SELECT 1 FROM compliance_access_grants g
                WHERE g.staff_user_id=#{actor} AND g.permission='EVIDENCE_READ'
                  AND g.client_id=t.client_id AND g.revoked_at IS NULL
                  AND g.valid_from <= #{now} AND g.valid_until > #{now})
            """
                    + MANAGED_SCOPE)
    long historyCount(Map<String, Object> parameters);

    @Insert(
            "INSERT INTO"
                + " compliance_investigation_exports(investigation_id,export_id,actor,recorded_at)"
                + " VALUES(#{investigation},#{id},#{actor},#{now})")
    void associate(Map<String, Object> p);

    @Insert(
            """
            INSERT INTO compliance_evidence_exports(
                export_id,requester,context_id,order_id,idempotency_key,request_hash,
                scope_version,snapshot_at,created_at,expires_at,state,digest,manifest,archive)
            VALUES(#{id},#{actor},#{context},#{order},#{key},#{hash},#{scope},#{snapshot},
                   #{created},#{expires},#{state},#{digest},CAST(#{manifest} AS jsonb),#{archive})
            """)
    void save(Map<String, Object> values);
}
