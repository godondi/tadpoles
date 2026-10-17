package com.neueda.leap.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BankingMapper {
    @Select(
            """
            SELECT *
            FROM bank_references
            WHERE client_id = #{client}
                AND active
            ORDER BY is_default DESC,created_at,bank_id
            """)
    List<Map<String, Object>> banks(int client);

    @Select("SELECT * FROM bank_references WHERE client_id=#{client} AND bank_id=#{id}")
    Map<String, Object> bank(@Param("client") int client, @Param("id") UUID id);

    @Select("SELECT EXISTS(SELECT 1 FROM bank_references WHERE client_id=#{client} AND active)")
    boolean hasBank(int client);

    @Select(
            """
            SELECT EXISTS
                (SELECT 1
                 FROM bank_references
                 WHERE client_id = #{client}
                     AND active
                     AND lower(nickname) = lower(#{name})
                     AND bank_id <> #{id})
            """)
    boolean duplicate(
            @Param("client") int client, @Param("name") String name, @Param("id") UUID id);

    @Select(
            """
            SELECT EXISTS
                (SELECT 1
                 FROM client_trades
                 WHERE client_id = #{client}
                     AND record_kind = 'SELF_DIRECTED'
                     AND status IN ('ACCEPTED', 'EXECUTING'))
            """)
    boolean pendingOrders(int client);

    @Update(
            """
            UPDATE bank_references
            SET is_default = false,version = version + 1,updated_at = #{now}
            WHERE client_id = #{client}
                AND is_default
            """)
    void clearDefault(@Param("client") int client, @Param("now") Instant now);

    @Insert(
            """
            INSERT INTO bank_references(bank_id,
                            client_id, nickname, encrypted_details, last_four, is_default, created_at, updated_at)
            VALUES(#{id},#{client},#{name},#{encrypted},#{last},#{isDefault},#{now},#{now})
            """)
    void add(Map<String, Object> bank);

    @Update(
            """
            UPDATE bank_references
            SET nickname = #{name},encrypted_details = #{encrypted},last_four = #{last},
                is_default = #{isDefault},version = version + 1,updated_at = #{now}
            WHERE client_id = #{client}
                AND bank_id = #{id}
            """)
    void edit(Map<String, Object> bank);

    @Update(
            """
            UPDATE bank_references
            SET active = false,is_default = false,version = version + 1,updated_at = #{now}
            WHERE client_id = #{client}
                AND bank_id = #{id}
            """)
    void deactivate(@Param("client") int client, @Param("id") UUID id, @Param("now") Instant now);

    @Update(
            """
            UPDATE bank_references
            SET is_default = true,version = version + 1,updated_at = #{now}
            WHERE bank_id =
                    (SELECT bank_id
                     FROM bank_references
                     WHERE client_id = #{client}
                         AND active
                     ORDER BY created_at,bank_id
                     LIMIT 1)
            """)
    void chooseDefault(@Param("client") int client, @Param("now") Instant now);

    @Insert(
            """
            INSERT INTO audit_logs(user_id, entity_name, entity_id, action_type, new_values)
            VALUES(#{actor},'bank_references',#{id},#{action},CAST(#{facts} AS jsonb))
            """)
    void audit(
            @Param("actor") int actor,
            @Param("id") String id,
            @Param("action") String action,
            @Param("facts") String facts);

    @Select("SELECT * FROM cash_movements WHERE client_id=#{client} AND request_key=#{key}")
    Map<String, Object> byKey(@Param("client") int client, @Param("key") String key);

    @Select(
            """
            SELECT *
            FROM cash_movements
            WHERE client_id = #{client}
            ORDER BY recorded_at DESC,movement_id
            LIMIT #{limit}
            OFFSET #{offset}
            """)
    List<Map<String, Object>> history(
            @Param("client") int client, @Param("limit") int limit, @Param("offset") int offset);

    @Select(
            """
            SELECT COALESCE(sum(amount), 0)
            FROM cash_movements
            WHERE client_id = #{client}
                AND direction = 'DEPOSIT'
                AND status = 'SETTLED'
                AND recorded_at >= #{start}
                AND recorded_at < #{end}
            """)
    BigDecimal dailyDeposits(
            @Param("client") int client, @Param("start") Instant start, @Param("end") Instant end);

    @Insert(
            "INSERT INTO"
                + " cash_movements(movement_id,client_id,bank_id,request_key,request_hash,direction,amount,status,reason,bank_snapshot,posting_group,actor,recorded_at)"
                + " VALUES(#{id},#{client},#{bank},#{key},#{hash},#{direction},#{amount},#{status},#{reason},#{snapshot},#{group},#{actor},#{now})")
    void movement(Map<String, Object> movement);

    @Insert(
            """
            INSERT INTO cash_journal(posting_id,
                            client_id, currency, delta, movement_id, posting_group, actor, recorded_at)
            VALUES(#{posting},#{client},'USD',#{delta},#{id},#{group},#{actor},#{now})
            """)
    void journal(Map<String, Object> movement);

    @Insert(
            """
            INSERT INTO cash_movement_outbox(event_id,
                            movement_id, event_type, payload, created_at)
            VALUES(#{event},#{id},#{status},CAST(#{payload} AS jsonb),#{now})
            """)
    void outbox(Map<String, Object> movement);
}
