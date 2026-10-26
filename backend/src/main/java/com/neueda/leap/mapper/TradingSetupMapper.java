package com.neueda.leap.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TradingSetupMapper {
    @Select(
            """
            SELECT c.client_id,u.user_id
            FROM clients c
            JOIN users u USING(user_id)
            WHERE u.username = 'demo.client'
                AND u.email = 'demo.client@tadpoles.test'
                AND u.enabled
                AND EXISTS
                    (SELECT 1
                     FROM user_roles ur
                     JOIN roles r USING(role_id)
                     WHERE ur.user_id = u.user_id
                         AND r.role_name = 'CLIENT')
            """)
    Map<String, Object> demo();

    @Select("SELECT EXISTS(SELECT 1 FROM cash_journal WHERE client_id=#{client})")
    boolean hasHistory(int client);

    @Insert(
            """
            INSERT INTO cash_journal(posting_id,
                            client_id, currency, delta, setup_reference, posting_group, actor, recorded_at)
            VALUES(#{posting},#{client},'USD',10000,#{reference},#{group},'local demo opening',#{now})
            """)
    void opening(Map<String, Object> args);

    @Select("SELECT instrument_id FROM instruments WHERE ticker=#{ticker}")
    Integer instrument(String ticker);

    @Select(
            """
            SELECT instrument_id AS "instrumentId", ticker, instrument_name AS name,
                   exchange AS venue, asset_class AS "assetClass", security_type AS "securityType",
                   currency, is_active AS active
            FROM instruments
            WHERE is_active AND currency = 'USD'
            ORDER BY ticker
            """)
    List<Map<String, Object>> usdCatalog();

    @Select("SELECT policy_version FROM instrument_trading_policies WHERE instrument_id=#{id}")
    String version(int id);

    @Select("SELECT 1 FROM pg_advisory_xact_lock(7414015)")
    int lockCatalog();

    @Insert(
            """
            INSERT INTO instrument_policy_audit(audit_id,
                            instrument_id, actor, actor_type, reason, before_values, after_values, recorded_at)
            VALUES(#{audit},#{id},NULL,'SYSTEM','Default manifest provider identity validation',NULL,CAST(#{after} AS jsonb),#{now})
            """)
    void audit(Map<String, Object> args);

    @Insert("INSERT INTO provider_setup_checks VALUES(#{id},#{symbol},#{now},#{result},#{detail})")
    void providerCheck(Map<String, Object> args);

    @Select(
            """
            SELECT DISTINCT ON(symbol) symbol,checked_at AS "checkedAt",result,detail
            FROM provider_setup_checks
            ORDER BY symbol,checked_at DESC
            """)
    List<Map<String, Object>> checks();
}
