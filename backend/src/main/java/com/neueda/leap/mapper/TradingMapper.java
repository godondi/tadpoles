package com.neueda.leap.mapper;

import com.neueda.leap.trading.TradingModels.Policy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.annotations.*;

/**
 * All account-changing paths take the persisted client lock first. No external calls occur under
 * it.
 */
@Mapper
public interface TradingMapper {
    @Insert(
            """
            INSERT INTO client_cash_accounts(client_id, currency)
            VALUES(#{client},#{currency}) ON CONFLICT DO NOTHING
            """)
    void zeroAccount(@Param("client") int client, @Param("currency") String currency);

    @Select("SELECT client_id FROM clients WHERE client_id=#{id} FOR UPDATE")
    Integer lockClient(int id);

    @Select("SELECT EXISTS(SELECT 1 FROM instruments WHERE instrument_id=#{id})")
    boolean instrumentExists(int id);

    @Select(
            """
            SELECT EXISTS
                (SELECT 1
                 FROM clients c
                 JOIN users u USING(user_id)
                 WHERE c.client_id = #{id}
                     AND u.enabled)
            """)
    boolean accountEnabled(int id);

    @Select("SELECT EXISTS(SELECT 1 FROM bank_references WHERE client_id=#{id} AND active)")
    boolean hasBank(int id);

    @Select(
            "SELECT EXISTS(SELECT 1 FROM client_profiles WHERE client_id=#{id} AND"
                    + " onboarding_complete)")
    boolean onboarded(int id);

    @Select(
            """
            SELECT p.instrument_id,i.ticker,i.is_active AS active,p.policy_version AS version,p.venue,p.quantity_unit,
                   p.price_unit,p.currency, p.quantity_step,p.currency_scale,p.max_quantity,p.max_notional,
                   p.quote_max_age_seconds,p.future_skew_seconds, p.retry_seconds,p.max_attempts,p.session,p.enabled,
                   p.raw_price_unit,p.price_multiplier,p.provider_symbol,p.buy_allowed,p.sell_allowed
            FROM instrument_trading_policies p
            JOIN instruments i USING(instrument_id)
            WHERE p.instrument_id = #{id}
                AND p.currency = i.currency
            """)
    Policy policy(int id);

    @Select(
            """
            SELECT *
            FROM client_trades
            WHERE client_id = #{client}
                AND idempotency_key = #{key}
                AND record_kind = 'SELF_DIRECTED'
            """)
    Map<String, Object> byKey(@Param("client") int client, @Param("key") String key);

    @Select("SELECT * FROM client_trades WHERE client_id=#{client} AND trade_id=#{id}")
    Map<String, Object> order(@Param("client") int client, @Param("id") int id);

    @Select("SELECT * FROM client_trades WHERE trade_id=#{id}")
    Map<String, Object> workerOrder(int id);

    @Select("SELECT * FROM client_cash_accounts WHERE client_id=#{client} AND currency=#{currency}")
    Map<String, Object> cash(@Param("client") int client, @Param("currency") String currency);

    @Select(
            "SELECT * FROM client_positions WHERE client_id=#{client} AND"
                    + " instrument_id=#{instrument}")
    Map<String, Object> position(@Param("client") int client, @Param("instrument") int instrument);

    @Insert(
            """
            INSERT INTO client_positions(client_id, instrument_id)
            VALUES(#{client},#{instrument}) ON CONFLICT DO NOTHING
            """)
    void ensurePosition(@Param("client") int client, @Param("instrument") int instrument);

    @Select(
            value =
                    """
            INSERT INTO client_trades(client_id,instrument_id,submitted_by_user_id,trade_type,quantity,price,trade_date,status,record_kind,
            idempotency_key,request_hash,submitted_at,accepted_at,updated_at,version,order_type,currency_snapshot,policy_snapshot,rejection_stage,rejection_code,reason)
            VALUES(#{client},#{instrument},#{actor},#{side},#{quantity},NULL,CAST(#{now} AS timestamptz) AT TIME ZONE 'UTC',#{status},'SELF_DIRECTED',
            #{key},#{hash},#{now},#{accepted},#{now},1,'MARKET',#{currency},CAST(#{policy} AS jsonb),#{stage},#{code},#{reason}) RETURNING trade_id
            """,
            affectData = true)
    int insertOrder(Map<String, Object> args);

    @Update(
            "UPDATE client_cash_accounts SET"
                    + " reserved=reserved+#{amount},version=version+1,updated_at=#{now} WHERE"
                    + " client_id=#{client} AND currency=#{currency}")
    void reserveCash(
            @Param("client") int client,
            @Param("currency") String currency,
            @Param("amount") BigDecimal amount,
            @Param("now") Instant now);

    @Update(
            "UPDATE client_positions SET"
                    + " reserved=reserved+#{amount},version=version+1,updated_at=#{now} WHERE"
                    + " client_id=#{client} AND instrument_id=#{instrument}")
    void reserveUnits(
            @Param("client") int client,
            @Param("instrument") int instrument,
            @Param("amount") BigDecimal amount,
            @Param("now") Instant now);

    @Insert(
            "INSERT INTO order_reservations"
                    + " VALUES(#{id},#{resource},#{amount},'ACTIVE',#{now},NULL)")
    void reservation(
            @Param("id") int id,
            @Param("resource") String resource,
            @Param("amount") BigDecimal amount,
            @Param("now") Instant now);

    @Select("SELECT * FROM order_reservations WHERE order_id=#{id}")
    Map<String, Object> reservationFor(int id);

    @Update(
            """
            UPDATE order_reservations
            SET state = #{state},ended_at = #{now}
            WHERE order_id = #{id}
                AND state = 'ACTIVE'
            """)
    int endReservation(
            @Param("id") int id, @Param("state") String state, @Param("now") Instant now);

    @Insert(
            "INSERT INTO order_execution_jobs(order_id,next_attempt,deadline)"
                    + " VALUES(#{id},#{now},#{deadline})")
    void job(@Param("id") int id, @Param("now") Instant now, @Param("deadline") Instant deadline);

    @Select(
            """
            SELECT order_id
            FROM order_execution_jobs
            WHERE NOT completed
                AND next_attempt <= #{now}
                AND (lease_until IS NULL
                     OR lease_until <= #{now})
            ORDER BY next_attempt,order_id
            LIMIT 20
            """)
    List<Integer> candidates(Instant now);

    @Select("SELECT * FROM order_execution_jobs WHERE order_id=#{id} FOR UPDATE")
    Map<String, Object> lockJob(int id);

    @Update(
            """
            UPDATE order_execution_jobs
            SET claim_token = #{token},lease_until = #{lease},attempts = attempts + 1
            WHERE order_id = #{id}
            """)
    void claim(@Param("id") int id, @Param("token") UUID token, @Param("lease") Instant lease);

    @Update("UPDATE order_execution_jobs SET completed=true,lease_until=NULL WHERE order_id=#{id}")
    void complete(int id);

    @Update(
            "UPDATE order_execution_jobs SET lease_until=NULL,next_attempt=#{next} WHERE"
                    + " order_id=#{id}")
    void retry(@Param("id") int id, @Param("next") Instant next);

    @Insert(
            """
            INSERT INTO order_execution_attempts
            VALUES(#{attemptId},#{id},#{attempt},#{outcome},#{reason},#{quote},#{now})
            """)
    void attempt(
            @Param("attemptId") UUID attemptId,
            @Param("id") int id,
            @Param("attempt") int attempt,
            @Param("outcome") String outcome,
            @Param("reason") String reason,
            @Param("quote") UUID quote,
            @Param("now") Instant now);

    @Insert(
            "INSERT INTO order_quote_observations VALUES(#{quote},#{id},#{stage},CAST(#{facts} AS"
                    + " jsonb),#{now})")
    void quote(
            @Param("quote") UUID quote,
            @Param("id") int id,
            @Param("stage") String stage,
            @Param("facts") String facts,
            @Param("now") Instant now);

    @Insert(
            """
            INSERT INTO order_rule_decisions
            VALUES(#{decision},#{id},#{stage},#{policy},#{passed},#{code},#{reason},#{quote},#{now})
            """)
    void decision(
            @Param("decision") UUID decision,
            @Param("id") int id,
            @Param("stage") String stage,
            @Param("policy") String policy,
            @Param("passed") boolean passed,
            @Param("code") String code,
            @Param("reason") String reason,
            @Param("quote") UUID quote,
            @Param("now") Instant now);

    @Update(
            """
            UPDATE client_trades
            SET status = #{status},version = version + 1,updated_at = #{now},
                                                                  rejection_stage = #{stage},rejection_code = #{code},
                                                                  reason = #{reason}
            WHERE trade_id = #{id}
            """)
    void state(
            @Param("id") int id,
            @Param("status") String status,
            @Param("stage") String stage,
            @Param("code") String code,
            @Param("reason") String reason,
            @Param("now") Instant now);

    @Select(
            value =
                    "UPDATE clients SET event_cursor=event_cursor+1 WHERE client_id=#{client}"
                            + " RETURNING event_cursor",
            affectData = true)
    long nextCursor(int client);

    @Insert(
            "INSERT INTO order_events"
                + " VALUES(#{event},#{id},#{client},#{cursor},#{version},#{previous},#{state},#{reason},#{actor},#{quote},#{fill},#{now})")
    void event(Map<String, Object> event);

    @Insert(
            """
            INSERT INTO outbox_events(event_id,
                            order_id, version, event_type, payload, created_at)
            VALUES(#{event},#{id},#{version},#{state},CAST(#{payload} AS jsonb),#{now})
            """)
    void outbox(Map<String, Object> event);

    @Insert(
            "INSERT INTO order_fills"
                + " VALUES(#{fill},#{id},#{reference},#{quote},#{quantity},#{price},#{gross},0,#{currency},#{group},#{executed},#{now})")
    void fill(Map<String, Object> fill);

    @Select("SELECT * FROM order_fills WHERE order_id=#{id}")
    Map<String, Object> fillFor(int id);

    @Update(
            "UPDATE client_cash_accounts SET"
                + " posted=posted+#{delta},reserved=reserved-#{release},version=version+1,updated_at=#{now}"
                + " WHERE client_id=#{client} AND currency=#{currency}")
    void cashMovement(
            @Param("client") int client,
            @Param("currency") String currency,
            @Param("delta") BigDecimal delta,
            @Param("release") BigDecimal release,
            @Param("now") Instant now);

    @Update(
            "UPDATE client_positions SET"
                + " posted=posted+#{delta},reserved=reserved-#{release},version=version+1,updated_at=#{now}"
                + " WHERE client_id=#{client} AND instrument_id=#{instrument}")
    void positionMovement(
            @Param("client") int client,
            @Param("instrument") int instrument,
            @Param("delta") BigDecimal delta,
            @Param("release") BigDecimal release,
            @Param("now") Instant now);

    @Insert(
            """
            INSERT INTO cash_journal(posting_id,
                            client_id, currency, delta, fill_id, posting_group, actor, recorded_at)
            VALUES(#{posting},#{client},#{currency},#{cashDelta},#{fill},#{group},'execution-worker',#{now})
            """)
    void cashJournal(Map<String, Object> fill);

    @Insert(
            """
            INSERT INTO position_journal(posting_id,
                            client_id, instrument_id, delta, fill_id, posting_group, actor, recorded_at)
            VALUES(#{posting},#{client},#{instrument},#{unitDelta},#{fill},#{group},'execution-worker',#{now})
            """)
    void positionJournal(Map<String, Object> fill);

    @Select(
            """
            SELECT currency,posted::text,reserved::text,(posted - reserved)::text AS available,version,
                   updated_at AS "updatedAt"
            FROM client_cash_accounts
            WHERE client_id = #{client}
                AND available_for_trading
                AND currency = 'USD'
            ORDER BY currency
            """)
    List<Map<String, Object>> balances(int client);

    // Keep closed rows in the ledger, but only expose currently owned positions.
    @Select(
            """
            SELECT p.instrument_id AS "instrumentId", i.ticker, i.instrument_name AS name, i.currency, p.posted::text AS owned, p.reserved::text,
                   (p.posted - p.reserved)::text AS available, p.version, p.updated_at AS "updatedAt"
            FROM client_positions p
            JOIN instruments i USING (instrument_id)
            WHERE p.client_id = #{client}
                AND p.available_for_trading
                AND p.posted > 0
            ORDER BY i.ticker
            """)
    List<Map<String, Object>> positions(int client);

    @Select(
            """
            SELECT event_id AS "eventId",sequence,
            cursor,to_state AS status,reason,occurred_at AS "occurredAt"
            FROM order_events
            WHERE client_id = #{client}
                AND order_id = #{id}
            ORDER BY sequence
            LIMIT #{limit}
            OFFSET #{offset}
            """)
    List<Map<String, Object>> events(
            @Param("client") int client,
            @Param("id") int id,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("SELECT count(*) FROM order_events WHERE client_id=#{client} AND order_id=#{id}")
    long eventCount(@Param("client") int client, @Param("id") int id);

    @Select(
            """
            SELECT event_id AS "eventId",order_id AS "orderId",sequence AS version,
            cursor,to_state AS status
            FROM order_events
            WHERE client_id = #{client}
                AND
                cursor > #{cursor}
            ORDER BY
            cursor
            LIMIT 100
            """)
    List<Map<String, Object>> changes(@Param("client") int client, @Param("cursor") long cursor);

    @Select("SELECT event_cursor FROM clients WHERE client_id=#{client}")
    long cursor(int client);

    @Select(
            """
            SELECT stage,passed,code,explanation,recorded_at AS "recordedAt"
            FROM order_rule_decisions
            WHERE order_id = #{id}
            ORDER BY recorded_at,decision_id
            """)
    List<Map<String, Object>> decisions(int id);

    @Select(
            "SELECT facts::text FROM order_quote_observations WHERE order_id=#{id} ORDER BY"
                    + " recorded_at,quote_id")
    List<String> quotes(int id);

    @Select(
            """
            SELECT exception_id AS id,kind,recorded_at AS "recordedAt"
            FROM financial_migration_exceptions
            WHERE client_id = #{client}
            ORDER BY exception_id
            """)
    List<Map<String, Object>> migrationExceptions(int client);

    @Select("SELECT count(*)=1 FROM schema_migrations WHERE version=8")
    boolean schemaReady();
}
