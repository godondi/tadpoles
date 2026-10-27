package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface SupportWorkflowMapper {
    @Select(
            """
SELECT CASE WHEN EXISTS(SELECT 1 FROM schema_migrations WHERE version=17) THEN
  NOT EXISTS(SELECT 1 FROM unnest(ARRAY['support_case_intake','support_case_workflow',
    'support_case_workflow_events','support_case_workflow_requests','support_case_tasks',
    'support_case_resolutions','support_case_referrals','compliance_investigations',
    'compliance_investigation_events','compliance_investigation_requests',
    'compliance_investigation_exports']) t(name)
    WHERE NOT has_table_privilege(current_user,name,'SELECT')
       OR NOT has_table_privilege(current_user,name,'INSERT'))
  AND has_column_privilege(current_user,'support_case_workflow','version','UPDATE')
  AND has_column_privilege(current_user,'support_case_tasks','state','UPDATE')
  AND has_column_privilege(current_user,'compliance_investigations','version','UPDATE')
ELSE false END
""")
    boolean ready();

    @Select("SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE version=16)")
    boolean supportReady();

    @Select(
            "SELECT EXISTS(SELECT 1 FROM support_case_intake WHERE case_id=#{id}) OR EXISTS(SELECT"
                    + " 1 FROM support_case_workflow_requests WHERE case_id=#{id})")
    boolean requiresResolution(@Param("id") UUID id);

    String QUEUE_FROM =
            " FROM support_cases c JOIN support_case_workflow w USING(case_id) "
                    + " LEFT JOIN users owner ON owner.user_id=w.assignee WHERE "
                    + SupportCaseMapper.SCOPE
                    + SupportCaseMapper.FILTER
                    + """
                      AND (#{assignee}::integer IS NULL OR w.assignee=#{assignee})
                      AND (#{priority}::text IS NULL OR w.priority=#{priority})
                      AND (#{category}::text IS NULL OR w.effective_category=#{category})
                      AND (#{view}='ALL'
                        OR (#{view}='ACTIONABLE' AND c.status<>'RESOLVED')
                        OR (#{view}='UNASSIGNED' AND w.assignee IS NULL AND c.status<>'RESOLVED')
                        OR (#{view}='MINE' AND w.assignee=#{actor} AND c.status<>'RESOLVED')
                        OR (#{view}='AWAITING_CLIENT' AND c.status='AWAITING_CLIENT')
                        OR (#{view}='RESOLVED' AND c.status='RESOLVED')
                        OR (#{view}='AWAITING_REVIEW' AND c.status<>'RESOLVED' AND EXISTS(
                          SELECT 1 FROM support_case_referrals r
                          JOIN compliance_investigations i USING(investigation_id)
                          WHERE r.case_id=c.case_id AND i.status NOT IN ('DECIDED','CLOSED'))))
                      AND (NOT #{overdueOnly} OR (c.status<>'RESOLVED' AND w.due_at<#{now}))
                      """;

    @Select(
            "SELECT c.*,w.assignee,owner.username AS"
                + " assignee_label,w.priority,w.next_action,w.due_at,w.effective_category,w.version"
                + " AS workflow_version"
                    + QUEUE_FROM
                    + " ORDER BY CASE w.priority WHEN 'HIGH' THEN 0 WHEN 'NORMAL' THEN 1 ELSE 2"
                    + " END,w.due_at NULLS LAST,c.created_at,c.case_id LIMIT #{limit} OFFSET"
                    + " #{offset}")
    List<Map<String, Object>> queue(Map<String, Object> p);

    @Select("SELECT count(*)" + QUEUE_FROM)
    long queueCount(Map<String, Object> p);

    @Insert(
            "INSERT INTO support_case_intake(case_id,schema_version,payload,recorded_at)"
                    + " VALUES(#{id},1,#{intake}::jsonb,#{now})")
    void intake(Map<String, Object> p);

    @Select("SELECT octet_length(#{payload}::jsonb::text)")
    int intakeSize(@Param("payload") String payload);

    @Insert(
            "INSERT INTO support_case_workflow(case_id,effective_category,updated_at)"
                    + " VALUES(#{id},#{category},#{now})")
    void initialize(Map<String, Object> p);

    @Insert(
            "INSERT INTO support_case_workflow_events(case_id,version,actor,kind,facts,recorded_at)"
                    + " VALUES(#{id},#{workflowVersion},#{actor},#{kind},#{facts}::jsonb,#{now})")
    void event(Map<String, Object> p);

    @Select("SELECT * FROM support_case_workflow WHERE case_id=#{id}")
    Map<String, Object> workflow(@Param("id") UUID id);

    @Select("SELECT * FROM support_case_workflow WHERE case_id=#{id} FOR UPDATE")
    Map<String, Object> lock(@Param("id") UUID id);

    @Select("SELECT payload::text FROM support_case_intake WHERE case_id=#{id}")
    String intakeJson(@Param("id") UUID id);

    @Select(
            "SELECT code,label,state,reason,evidence_kind AS \"evidenceKind\",evidence_reference AS"
                + " \"evidenceReference\",actor,recorded_at AS \"recordedAt\" FROM"
                + " support_case_tasks WHERE case_id=#{id} AND checklist_revision=#{revision} ORDER"
                + " BY code")
    List<Map<String, Object>> tasks(@Param("id") UUID id, @Param("revision") long revision);

    @Insert(
            """
INSERT INTO support_case_tasks(case_id,checklist_revision,code,label,state,reason,evidence_kind,evidence_reference,actor,recorded_at)
VALUES(#{id},#{revision},#{code},#{label},#{state},#{reason},#{evidenceKind},#{evidenceReference},#{actor},#{now})
ON CONFLICT(case_id,checklist_revision,code) DO UPDATE SET state=excluded.state,reason=excluded.reason,
    evidence_kind=excluded.evidence_kind,evidence_reference=excluded.evidence_reference,actor=excluded.actor,recorded_at=excluded.recorded_at
""")
    void task(Map<String, Object> p);

    @Update(
            """
UPDATE support_case_workflow SET effective_category=#{category},assignee=#{assignee},priority=#{priority},
    next_action=#{nextAction},due_at=#{dueAt},version=#{workflowVersion},checklist_revision=#{revision},updated_at=#{now}
WHERE case_id=#{id}
""")
    void advance(Map<String, Object> p);

    @Select(
            "SELECT request_hash,receipt::text FROM support_case_workflow_requests WHERE"
                    + " case_id=#{id} AND actor=#{actor} AND request_key=#{key}")
    Map<String, Object> request(Map<String, Object> p);

    @Insert(
            "INSERT INTO"
                + " support_case_workflow_requests(case_id,actor,request_key,request_hash,receipt,recorded_at)"
                + " VALUES(#{id},#{actor},#{key},#{hash},#{receipt}::jsonb,#{now})")
    void receipt(Map<String, Object> p);

    @Select(
            "SELECT version,actor,kind,facts::text,recorded_at AS \"recordedAt\" FROM"
                + " support_case_workflow_events WHERE case_id=#{id} ORDER BY version DESC LIMIT 20"
                + " OFFSET #{offset}")
    List<Map<String, Object>> history(@Param("id") UUID id, @Param("offset") int offset);

    String ELIGIBLE =
            """
SELECT u.user_id AS id,u.username FROM users u
WHERE u.enabled AND EXISTS(SELECT 1 FROM user_roles r JOIN roles role USING(role_id)
      WHERE r.user_id=u.user_id AND role.role_name='TRADING_OPS')
  AND NOT EXISTS(SELECT 1 FROM user_roles r JOIN roles role USING(role_id)
      WHERE r.user_id=u.user_id AND role.role_name='CLIENT')
  AND EXISTS(SELECT 1 FROM operations_access_grants g WHERE g.staff_user_id=u.user_id AND g.client_id=#{client}
      AND g.permission='CASE_READ' AND g.revoked_at IS NULL AND g.valid_from<=#{now} AND g.valid_until>#{now})
  AND EXISTS(SELECT 1 FROM operations_access_grants g WHERE g.staff_user_id=u.user_id AND g.client_id=#{client}
      AND g.permission='CASE_UPDATE' AND g.revoked_at IS NULL AND g.valid_from<=#{now} AND g.valid_until>#{now})
""";

    @Select(ELIGIBLE + " ORDER BY u.username,u.user_id LIMIT 100")
    List<Map<String, Object>> assignees(Map<String, Object> p);

    @Select("SELECT EXISTS(" + ELIGIBLE + " AND u.user_id=#{assignee})")
    boolean eligible(Map<String, Object> p);

    @Select(
            "SELECT EXISTS(SELECT 1 FROM cash_movements WHERE movement_id=#{movement} AND"
                    + " client_id=#{client})")
    boolean ownedMovement(@Param("movement") UUID movement, @Param("client") int client);

    @Select(
            "SELECT EXISTS(SELECT 1 FROM financial_migration_exceptions WHERE"
                    + " exception_id=#{exception} AND client_id=#{client})")
    boolean ownedException(@Param("exception") long exception, @Param("client") int client);

    @Select("SELECT EXISTS(SELECT 1 FROM instruments WHERE instrument_id=#{instrument})")
    boolean instrument(int instrument);

    @Select(
            """
SELECT u.enabled AS "identityEnabled",
  EXISTS(SELECT 1 FROM client_profiles p WHERE p.client_id=c.client_id AND p.onboarding_complete) AS "onboarded",
  EXISTS(SELECT 1 FROM bank_references b WHERE b.client_id=c.client_id AND b.active) AS "bankPresent",
  a.currency,a.posted::text,a.reserved::text,(a.posted-a.reserved)::text AS available,
  a.available_for_trading AS "availableForTrading",a.updated_at AS "balanceAsOf"
FROM clients c JOIN users u USING(user_id)
LEFT JOIN client_cash_accounts a ON a.client_id=c.client_id AND a.currency='USD' WHERE c.client_id=#{client}
""")
    Map<String, Object> account(int client);

    @Select(
            """
SELECT p.instrument_id AS "instrumentId",i.ticker,p.posted::text,p.reserved::text,
  (p.posted-p.reserved)::text AS available,p.available_for_trading AS "availableForTrading"
FROM client_positions p JOIN instruments i USING(instrument_id)
WHERE p.client_id=#{client} ORDER BY p.instrument_id LIMIT 51
""")
    List<Map<String, Object>> positions(int client);

    @Select(
            "SELECT trade_id AS \"orderId\",status,instrument_id AS \"instrumentId\",trade_type AS"
                + " side,quantity::text FROM client_trades WHERE client_id=#{client} AND status IN"
                + " ('ACCEPTED','EXECUTING') ORDER BY trade_id LIMIT 51")
    List<Map<String, Object>> pendingOrders(int client);

    @Select(
            "SELECT movement_id AS"
                    + " reference,direction,amount::text,currency,status,reason,recorded_at AS"
                    + " \"recordedAt\" FROM cash_movements WHERE client_id=#{client} AND"
                    + " movement_id=#{movement}")
    Map<String, Object> movement(@Param("client") int client, @Param("movement") UUID movement);

    @Select(
            """
SELECT exception_id AS reference,source_reference AS "sourceReference",kind,instrument_id AS "instrumentId",
  recorded_at AS "capturedAt",
  jsonb_strip_nulls(jsonb_build_object(
    'reportedCashBalance',facts->>'cashBalance','recordedCurrency',facts->>'currency',
    'reportedQuantity',facts->>'quantity','reportedPosted',facts->>'posted',
    'reportedReserved',facts->>'reserved','originalDate',facts->>'as_of_date',
    'disposition',facts->>'disposition'))::text AS facts
FROM financial_migration_exceptions WHERE client_id=#{client} AND exception_id=#{exception}
""")
    Map<String, Object> historical(@Param("client") int client, @Param("exception") long exception);

    @Select(
            "SELECT audit_id::text FROM instrument_policy_audit WHERE audit_id=#{audit} AND"
                    + " instrument_id=#{instrument}")
    String policyChange(@Param("audit") UUID audit, @Param("instrument") int instrument);

    @Insert(
            """
INSERT INTO support_case_resolutions(resolution_id,case_id,public_version,workflow_version,category,code,checklist,actor,recorded_at)
VALUES(#{resolution},#{id},#{publicVersion},#{workflowVersion},#{category},#{code},#{checklist}::jsonb,#{actor},#{now})
""")
    void resolution(Map<String, Object> p);

    @Select(
            """
SELECT i.reference,i.status,i.reviewer AS "nextOwner",i.disposition,
       CASE WHEN i.status='NEEDS_INFORMATION' THEN (
           SELECT e.facts->>'reason' FROM compliance_investigation_events e
           WHERE e.investigation_id=i.investigation_id AND e.kind='TRANSITION'
             AND e.facts->>'status'='NEEDS_INFORMATION' ORDER BY e.version DESC LIMIT 1)
       END AS "informationRequest",
       i.client_safe_explanation AS "clientSafeExplanation",i.updated_at AS "updatedAt"
FROM support_case_referrals r JOIN compliance_investigations i USING(investigation_id)
WHERE r.case_id=#{id} ORDER BY r.recorded_at DESC,r.referral_id DESC LIMIT 20
""")
    List<Map<String, Object>> referrals(@Param("id") UUID id);

    @Select(
            """
SELECT EXISTS(SELECT 1 FROM support_case_referrals r JOIN compliance_investigations i USING(investigation_id)
    WHERE r.case_id=#{id} AND r.mandatory AND (i.status NOT IN ('DECIDED','CLOSED') OR i.disposition IS NULL))
""")
    boolean pendingReferral(@Param("id") UUID id);

    @Select(
            """
SELECT e.export_id AS "exportId",x.order_id AS "orderId",e.digest,e.recorded_at AS "associatedAt",x.expires_at AS "expiresAt",
       x.requester FROM support_case_exports e JOIN operations_evidence_exports x USING(export_id)
WHERE e.case_id=#{id} ORDER BY e.recorded_at DESC,e.export_id DESC LIMIT 21 OFFSET #{offset}
""")
    List<Map<String, Object>> exports(@Param("id") UUID id, @Param("offset") int offset);
}
