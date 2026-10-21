package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.*;

@Mapper
public interface OperationsAccessMapper {
    // Reused SQL predicate: account scope is enforced before sorting, limiting or serialization.
    String SCOPE =
            """
            EXISTS (SELECT 1 FROM operations_access_grants g
                    WHERE g.staff_user_id = #{actor} AND g.permission = #{permission}
                      AND g.client_id = t.client_id AND g.revoked_at IS NULL
                      AND g.valid_from <= #{now} AND g.valid_until > #{now})
            AND (#{managedClient}::integer IS NULL OR t.client_id=#{managedClient})
            AND (#{managedOrder}::integer IS NULL OR t.trade_id=#{managedOrder})
            """;

    @Select(
            """
            SELECT grant_id::text || ':' || client_id || ':' || account_reference AS identity
            FROM operations_access_grants
            WHERE staff_user_id=#{actor} AND permission=#{permission} AND revoked_at IS NULL
              AND valid_from <= #{now} AND valid_until > #{now}
            ORDER BY grant_id
            """)
    List<String> grants(
            @Param("actor") int actor,
            @Param("permission") String permission,
            @Param("now") Instant now);

    @Select(
            """
            SELECT count(*)=1 FROM operations_access_contexts
            WHERE context_id=#{context} AND actor=#{actor} AND expires_at > #{now}
              AND policy_version=#{policy}
            """)
    boolean context(
            @Param("context") UUID context,
            @Param("actor") int actor,
            @Param("now") Instant now,
            @Param("policy") String policy);

    @Insert(
            """
INSERT INTO operations_access_contexts(context_id,actor,purpose_code,case_reference,
                                      created_at,expires_at,policy_version,reference_type,managed_case_id)
VALUES(#{id},#{actor},#{purpose},#{reason},#{now},#{expires},#{policy},#{referenceType},#{managedCase})
""")
    void createContext(Map<String, Object> values);

    @Insert(
            """
INSERT INTO operations_access_events(event_id,actor,context_id,action,target_type,target_id,
    filter_digest,scope_version,outcome,returned_count,recorded_at,correlation_id,export_id)
VALUES(#{id},#{actor},#{context},#{action},#{type},#{target},#{filter},#{scope},#{outcome},
       #{count},#{now},#{correlation},#{export})
""")
    void audit(Map<String, Object> values);

    @Select(
            "SELECT c.case_id,c.client_id,c.order_id FROM support_cases c WHERE"
                + " c.reference=#{reference} AND EXISTS (SELECT 1 FROM operations_access_grants g"
                + " WHERE g.staff_user_id=#{actor} AND g.client_id=c.client_id AND"
                + " g.permission='CASE_READ' AND g.revoked_at IS NULL AND g.valid_from<=#{now} AND"
                + " g.valid_until>#{now})")
    Map<String, Object> managed(
            @Param("reference") String reference,
            @Param("actor") int actor,
            @Param("now") Instant now);

    @Select(
            "SELECT x.reference_type,x.managed_case_id,c.client_id,c.order_id FROM"
                + " operations_access_contexts x LEFT JOIN support_cases c ON"
                + " c.case_id=x.managed_case_id WHERE x.context_id=#{id}")
    Map<String, Object> binding(@Param("id") UUID id);
}
