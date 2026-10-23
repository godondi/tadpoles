package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface ComplianceCaseMapper {
    String SCOPE =
            """
            EXISTS(SELECT 1 FROM compliance_access_grants g WHERE g.staff_user_id=#{actor}
                AND g.permission=#{permission} AND g.client_id=i.client_id AND g.revoked_at IS NULL
                AND g.valid_from<=#{now} AND g.valid_until>#{now})
            """;
    String ELIGIBLE =
            """
SELECT u.user_id AS id,u.username FROM users u WHERE u.enabled
  AND EXISTS(SELECT 1 FROM user_roles r JOIN roles role USING(role_id)
      WHERE r.user_id=u.user_id AND role.role_name='COMPLIANCE')
  AND EXISTS(SELECT 1 FROM compliance_access_grants g WHERE g.staff_user_id=u.user_id
      AND g.client_id=#{client} AND g.permission='INVESTIGATION_READ' AND g.revoked_at IS NULL
      AND g.valid_from<=#{now} AND g.valid_until>#{now})
  AND EXISTS(SELECT 1 FROM compliance_access_grants g WHERE g.staff_user_id=u.user_id
      AND g.client_id=#{client} AND g.permission='INVESTIGATION_UPDATE' AND g.revoked_at IS NULL
      AND g.valid_from<=#{now} AND g.valid_until>#{now})
""";

    @Select(ELIGIBLE + " ORDER BY u.username,u.user_id LIMIT 100")
    List<Map<String, Object>> reviewers(Map<String, Object> p);

    @Select("SELECT EXISTS(" + ELIGIBLE + " AND u.user_id=#{reviewer})")
    boolean eligible(Map<String, Object> p);

    @Insert(
            """
INSERT INTO compliance_investigations(investigation_id,reference,case_id,client_id,order_id,reviewer,reason_code,summary,created_at,updated_at)
VALUES(#{investigation},#{reviewReference},#{id},#{client},#{order},#{reviewer},#{reasonCode},#{summary},#{now},#{now})
""")
    void create(Map<String, Object> p);

    @Insert(
            "INSERT INTO"
                + " support_case_referrals(referral_id,case_id,investigation_id,actor,recorded_at)"
                + " VALUES(#{referral},#{id},#{investigation},#{actor},#{now})")
    void link(Map<String, Object> p);

    @Insert(
            """
INSERT INTO compliance_investigation_events(investigation_id,version,actor,kind,facts,recorded_at)
VALUES(#{investigation},#{reviewVersion},#{actor},#{reviewKind},#{reviewFacts}::jsonb,#{now})
""")
    void event(Map<String, Object> p);

    @Select(
            "SELECT i.* FROM compliance_investigations i WHERE i.reference=#{reference} AND "
                    + SCOPE)
    Map<String, Object> detail(Map<String, Object> p);

    @Select(
            "SELECT i.* FROM compliance_investigations i WHERE i.reference=#{reference} AND "
                    + SCOPE
                    + " FOR UPDATE OF i")
    Map<String, Object> lock(Map<String, Object> p);

    @Select("SELECT case_id FROM support_cases WHERE case_id=#{id} FOR UPDATE")
    Map<String, Object> lockCase(@Param("id") UUID id);

    @Select(
            "SELECT i.* FROM compliance_investigations i WHERE "
                    + SCOPE
                    + " AND (#{status}::text IS NULL OR i.status=#{status}) ORDER BY i.updated_at"
                    + " DESC,i.investigation_id DESC LIMIT 20 OFFSET #{offset}")
    List<Map<String, Object>> list(Map<String, Object> p);

    @Select(
            "SELECT count(*) FROM compliance_investigations i WHERE "
                    + SCOPE
                    + " AND (#{status}::text IS NULL OR i.status=#{status})")
    long count(Map<String, Object> p);

    @Select(
            """
SELECT version,actor,kind,facts::text,recorded_at AS "recordedAt" FROM compliance_investigation_events
WHERE investigation_id=#{investigation} ORDER BY version DESC LIMIT 20 OFFSET #{offset}
""")
    List<Map<String, Object>> history(Map<String, Object> p);

    @Select(
            "SELECT request_hash,receipt::text FROM compliance_investigation_requests WHERE"
                + " investigation_id=#{investigation} AND actor=#{actor} AND request_key=#{key}")
    Map<String, Object> request(Map<String, Object> p);

    @Insert(
            "INSERT INTO"
                + " compliance_investigation_requests(investigation_id,actor,request_key,request_hash,receipt,recorded_at)"
                + " VALUES(#{investigation},#{actor},#{key},#{hash},#{receipt}::jsonb,#{now})")
    void receipt(Map<String, Object> p);

    @Update(
            """
UPDATE compliance_investigations SET reviewer=#{reviewer},status=#{status},version=#{reviewVersion},
    disposition=#{disposition},findings=#{findings},client_safe_explanation=#{clientSafeExplanation},
    updated_at=#{now} WHERE investigation_id=#{investigation}
""")
    void advance(Map<String, Object> p);
}
