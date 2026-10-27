package com.neueda.leap.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InstrumentPolicyMapper {
    String COLUMNS =
            "i.instrument_id AS \"instrumentId\",i.ticker,i.asset_class AS \"assetClass\","
                + "i.instrument_name AS"
                + " name,i.currency,i.is_active AS active,p.provider_symbol AS"
                + " \"providerSymbol\",p.venue,p.quantity_step::text AS"
                + " \"quantityStep\",p.max_quantity::text AS \"maxQuantity\",p.max_notional::text"
                + " AS \"maxNotional\",p.quote_max_age_seconds AS"
                + " \"quoteMaxAgeSeconds\",p.buy_allowed AS \"buyAllowed\",p.sell_allowed AS"
                + " \"sellAllowed\",COALESCE(p.revision,0) AS revision,p.validated_at AS"
                + " \"validatedAt\"";

    @Select(
            "SELECT "
                    + COLUMNS
                    + " FROM instruments i LEFT JOIN instrument_trading_policies p"
                    + " USING(instrument_id) WHERE i.ticker ILIKE '%'||#{search}||'%' OR"
                    + " i.instrument_name ILIKE '%'||#{search}||'%' ORDER BY i.ticker LIMIT 100"
                    + " OFFSET #{offset}")
    List<Map<String, Object>> list(@Param("search") String search, @Param("offset") int offset);

    @Select(
            "SELECT "
                    + COLUMNS
                    + " FROM instruments i LEFT JOIN instrument_trading_policies p"
                    + " USING(instrument_id) WHERE i.instrument_id=#{id}")
    Map<String, Object> get(int id);

    @Select("SELECT instrument_id FROM instruments WHERE instrument_id=#{id} FOR UPDATE")
    Integer lock(int id);

    @Select(
            value =
                    "INSERT INTO"
                        + " instruments(instrument_name,ticker,currency,asset_class,security_type,is_active)"
                        + " VALUES(#{name},#{ticker},'USD',#{assetClass},#{securityType},#{active})"
                        + " RETURNING instrument_id",
            affectData = true)
    int create(Map<String, Object> args);

    @Update(
            "UPDATE instruments SET instrument_name=#{name},is_active=#{active} WHERE"
                    + " instrument_id=#{id}")
    void edit(Map<String, Object> args);

    @Insert(
            """
            INSERT INTO instrument_trading_policies (
                instrument_id, policy_version, venue, quantity_unit, price_unit, currency,
                quantity_step, currency_scale, max_quantity, max_notional, quote_max_age_seconds,
                future_skew_seconds, retry_seconds, max_attempts, session, enabled, raw_price_unit,
                price_multiplier, provider_symbol, buy_allowed, sell_allowed, validated_at, revision
            )
            VALUES (
                #{id}, #{policyVersion}, #{venue}, 'shares', 'USD', 'USD', #{step}, 2,
                #{maxQuantity}, #{maxNotional}, #{age}, 2, 120, 3, #{session}, #{active}, 'USD', 1,
                #{providerSymbol}, #{buyAllowed}, #{sellAllowed}, #{validated}, #{revision}
            )
            ON CONFLICT (instrument_id) DO UPDATE SET
                policy_version = EXCLUDED.policy_version,
                venue = EXCLUDED.venue,
                quantity_step = EXCLUDED.quantity_step,
                max_quantity = EXCLUDED.max_quantity,
                max_notional = EXCLUDED.max_notional,
                quote_max_age_seconds = EXCLUDED.quote_max_age_seconds,
                session = EXCLUDED.session,
                enabled = EXCLUDED.enabled,
                provider_symbol = EXCLUDED.provider_symbol,
                buy_allowed = EXCLUDED.buy_allowed,
                sell_allowed = EXCLUDED.sell_allowed,
                validated_at = EXCLUDED.validated_at,
                revision = EXCLUDED.revision
            """)
    void policy(Map<String, Object> args);

    @Insert(
            """
            INSERT INTO instrument_policy_audit(audit_id,
                            instrument_id, actor, reason, before_values, after_values, recorded_at)
            VALUES(#{audit},#{id},#{actor},#{reason},CAST(#{before} AS jsonb),CAST(#{after} AS jsonb),#{now})
            """)
    void audit(Map<String, Object> args);
}
