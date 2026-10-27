package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.*;

@Mapper
public interface ComplianceAccessMapper {
    @Select(
            """
            SELECT permission || ':' || client_id || ':' || account_reference AS identity
            FROM compliance_access_grants
            WHERE staff_user_id=#{actor} AND permission=#{permission}
              AND revoked_at IS NULL AND valid_from <= #{now} AND valid_until > #{now}
            ORDER BY client_id, permission, grant_id
            """)
    List<String> grants(
            @Param("actor") int actor,
            @Param("permission") String permission,
            @Param("now") Instant now);

    @Select(
            """
            SELECT permission || ':' || client_id || ':' || account_reference AS identity
            FROM compliance_access_grants
            WHERE staff_user_id=#{actor}
              AND revoked_at IS NULL AND valid_from <= #{now} AND valid_until > #{now}
            ORDER BY permission,client_id,grant_id
            """)
    List<String> allGrantIdentities(@Param("actor") int actor, @Param("now") Instant now);

    @Select(
            """
            SELECT DISTINCT permission
            FROM compliance_access_grants
            WHERE staff_user_id=#{actor} AND revoked_at IS NULL
              AND valid_from <= #{now} AND valid_until > #{now}
            ORDER BY permission
            """)
    List<String> permissions(@Param("actor") int actor, @Param("now") Instant now);

    @Select(
            """
            SELECT count(*)=1
            FROM compliance_access_contexts
            WHERE context_id=#{context} AND actor=#{actor}
              AND purpose_code=#{purpose} AND policy_version=#{policy}
              AND revoked_at IS NULL AND expires_at > #{now}
            """)
    boolean context(
            @Param("context") UUID context,
            @Param("actor") int actor,
            @Param("purpose") String purpose,
            @Param("policy") String policy,
            @Param("now") Instant now);

    @Insert(
            """
            INSERT INTO compliance_access_contexts(
                context_id,actor,purpose_code,case_reference,created_at,expires_at,
                policy_version,scope_version)
            VALUES(#{id},#{actor},#{purpose},#{caseReference},#{now},#{expires},#{policy},#{scope})
            """)
    void createContext(Map<String, Object> values);

    @Insert(
            """
INSERT INTO compliance_access_contexts(context_id,actor,purpose_code,case_reference,created_at,expires_at,
    policy_version,scope_version,reference_type,investigation_id,managed_reviewer,managed_version)
VALUES(#{id},#{actor},#{purpose},#{caseReference},#{now},#{expires},#{policy},#{scope},
    'MANAGED',#{investigation},#{managedReviewer},#{managedVersion})
""")
    void createManagedContext(Map<String, Object> p);

    @Select(
            """
SELECT i.investigation_id,i.client_id,i.order_id,i.reviewer,i.version
FROM compliance_investigations i WHERE i.reference=#{reference} AND i.reviewer=#{actor}
  AND EXISTS(SELECT 1 FROM compliance_access_grants g WHERE g.staff_user_id=#{actor}
    AND g.permission='INVESTIGATION_READ' AND g.client_id=i.client_id AND g.revoked_at IS NULL
    AND g.valid_from<=#{now} AND g.valid_until>#{now})
""")
    Map<String, Object> managed(
            @Param("reference") String reference,
            @Param("actor") int actor,
            @Param("now") Instant now);

    @Insert(
            """
            INSERT INTO compliance_access_events(
                event_id,actor,context_id,action,target_type,target_id,filter_digest,
                scope_version,outcome,returned_count,recorded_at,correlation_id,export_id)
            VALUES(#{id},#{actor},#{context},#{action},#{type},#{target},#{filter},
                   #{scope},#{outcome},#{count},#{now},#{correlation},#{export})
            """)
    void audit(Map<String, Object> values);

    @Select(
            """
            SELECT *
            FROM compliance_access_contexts
            WHERE context_id=#{context} AND actor=#{actor}
            """)
    Map<String, Object> contextDetails(@Param("context") UUID context, @Param("actor") int actor);

    @Select(
            """
            SELECT username
            FROM users
            WHERE user_id=#{actor}
            """)
    String username(@Param("actor") int actor);

    @Select(
            """
            SELECT count(*) > 0
            FROM compliance_access_grants
            WHERE staff_user_id=#{actor} AND permission=#{permission} AND client_id=#{client}
              AND revoked_at IS NULL AND valid_from <= #{now} AND valid_until > #{now}
            """)
    boolean has(
            @Param("actor") int actor,
            @Param("permission") String permission,
            @Param("client") int client,
            @Param("now") Instant now);

    @Select(
            """
            SELECT account_reference
            FROM compliance_access_grants
            WHERE staff_user_id=#{actor} AND permission=#{permission} AND client_id=#{client}
              AND revoked_at IS NULL AND valid_from <= #{now} AND valid_until > #{now}
            ORDER BY grant_id
            LIMIT 1
            """)
    String accountReference(
            @Param("actor") int actor,
            @Param("permission") String permission,
            @Param("client") int client,
            @Param("now") Instant now);
}
