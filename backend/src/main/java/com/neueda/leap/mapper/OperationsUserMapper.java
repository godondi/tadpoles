package com.neueda.leap.mapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface OperationsUserMapper {
    @Select("""
        SELECT u.user_id AS id, u.username, coalesce(u.display_name, '') AS name, u.email,
               c.client_id AS client_id,
               coalesce((SELECT ca.posted - ca.reserved FROM client_cash_accounts ca
                         WHERE ca.client_id=c.client_id AND ca.currency='USD'), c.cash_balance, 0) AS balance,
               (u.trade_access_revoked_at IS NOT NULL AND
                 (u.trade_access_until IS NULL OR u.trade_access_until > now())) AS revoked,
               u.trade_access_until AS revoked_until,
               r.role_name AS roles
        FROM users u JOIN user_roles ur ON ur.user_id=u.user_id
        JOIN roles r ON r.role_id=ur.role_id
        LEFT JOIN clients c ON c.user_id=u.user_id
        WHERE r.role_name = 'CLIENT'
          AND (#{search,jdbcType=VARCHAR} = '' OR
               lower(concat_ws(' ', u.username, u.display_name, c.client_name, u.email)) LIKE '%' || lower(#{search,jdbcType=VARCHAR}) || '%')
        ORDER BY lower(u.username), u.user_id
        """)
    List<Map<String,Object>> clients(@Param("search") String search);

    @Update("""
        UPDATE users u SET trade_access_revoked_at=now(), trade_access_until=#{until}, trade_access_reason=#{reason}
        FROM clients c WHERE c.user_id=u.user_id AND c.client_id=#{clientId}
        """)
    int revoke(@Param("clientId") int clientId, @Param("until") Instant until, @Param("reason") String reason);

    @Update("""
        UPDATE users u SET trade_access_revoked_at=NULL, trade_access_until=NULL, trade_access_reason=NULL
        FROM clients c WHERE c.user_id=u.user_id AND c.client_id=#{clientId}
        """)
    int unrevoke(@Param("clientId") int clientId);

    @Insert("INSERT INTO client_trade_access_events(client_id,actor_subject,action,revoked_until,reason) VALUES(#{clientId},#{actor},#{action},#{until},#{reason})")
    int recordChange(@Param("clientId") int clientId, @Param("actor") String actor,
                     @Param("action") String action, @Param("until") Instant until,
                     @Param("reason") String reason);
}
