package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface ReconciliationMapper {
    @Select(
            """
SELECT 'CASH_JOURNAL' AS kind,'cash:' || client_id || ':' || currency AS reference
FROM client_cash_accounts c
WHERE posted <>
        (SELECT COALESCE(sum(delta), 0)
         FROM cash_journal j
         WHERE j.client_id = c.client_id
             AND j.currency = c.currency)
UNION ALL
SELECT 'POSITION_JOURNAL','position:' || client_id || ':' || instrument_id
FROM client_positions p
WHERE posted <>
        (SELECT COALESCE(sum(delta), 0)
         FROM position_journal j
         WHERE j.client_id = p.client_id
             AND j.instrument_id = p.instrument_id)
UNION ALL
SELECT 'CASH_HOLD','cash:' || client_id || ':' || currency
FROM client_cash_accounts c
WHERE reserved <>
        (SELECT COALESCE(sum(r.amount), 0)
         FROM order_reservations r
         JOIN client_trades t ON t.trade_id = r.order_id
         WHERE r.state = 'ACTIVE'
             AND r.resource = 'CASH'
             AND t.client_id = c.client_id
             AND t.currency_snapshot = c.currency)
UNION ALL
SELECT 'UNIT_HOLD','position:' || client_id || ':' || instrument_id
FROM client_positions p
WHERE reserved <>
        (SELECT COALESCE(sum(r.amount), 0)
         FROM order_reservations r
         JOIN client_trades t ON t.trade_id = r.order_id
         WHERE r.state = 'ACTIVE'
             AND r.resource = 'UNITS'
             AND t.client_id = p.client_id
             AND t.instrument_id = p.instrument_id)
UNION ALL
SELECT 'ORDER_PROCESSING','order:' || t.trade_id
FROM client_trades t
WHERE record_kind = 'SELF_DIRECTED'
    AND ((status IN ('ACCEPTED','EXECUTING')
          AND (NOT EXISTS
                   (SELECT 1
                    FROM order_execution_jobs j
                    WHERE j.order_id = t.trade_id
                        AND NOT j.completed)
               OR NOT EXISTS
                   (SELECT 1
                    FROM order_reservations r
                    WHERE r.order_id = t.trade_id
                        AND r.state = 'ACTIVE')))
         OR (status IN ('FILLED','REJECTED')
             AND (EXISTS
                      (SELECT 1
                       FROM order_reservations r
                       WHERE r.order_id = t.trade_id
                           AND r.state = 'ACTIVE')
                  OR EXISTS
                      (SELECT 1
                       FROM order_execution_jobs j
                       WHERE j.order_id = t.trade_id
                           AND NOT j.completed))))
UNION ALL
SELECT 'ORDER_FILL','order:' || t.trade_id
FROM client_trades t
WHERE t.record_kind = 'SELF_DIRECTED'
    AND t.status = 'FILLED'
    AND
        (SELECT count(*)
         FROM order_fills f
         WHERE f.order_id = t.trade_id) <> 1
UNION ALL
SELECT 'FILL_POSTING','fill:' || f.fill_id
FROM order_fills f
JOIN client_trades t ON t.trade_id = f.order_id
WHERE f.gross <> round(f.quantity * f.price, 2)
    OR f.fee <> 0
    OR f.quantity <> t.quantity
    OR f.currency <> t.currency_snapshot
    OR t.status <> 'FILLED'
    OR
        (SELECT count(*)
         FROM cash_journal j
         WHERE j.fill_id = f.fill_id
             AND j.posting_group = f.posting_group
             AND j.client_id = t.client_id
             AND j.currency = f.currency
             AND j.delta = CASE WHEN t.trade_type = 'BUY' THEN - f.gross ELSE f.gross
                           END) <> 1
    OR
        (SELECT count(*)
         FROM position_journal j
         WHERE j.fill_id = f.fill_id
             AND j.posting_group = f.posting_group
             AND j.client_id = t.client_id
             AND j.instrument_id = t.instrument_id
             AND j.delta = CASE WHEN t.trade_type = 'BUY' THEN f.quantity ELSE - f.quantity
                           END) <> 1
UNION ALL
SELECT 'MOVEMENT_POSTING','movement:' || m.movement_id
FROM cash_movements m
WHERE (m.status = 'SETTLED'
       AND
           (SELECT count(*)
            FROM cash_journal j
            WHERE j.movement_id = m.movement_id
                AND j.posting_group = m.posting_group
                AND j.client_id = m.client_id
                AND j.currency = m.currency
                AND j.delta = CASE WHEN m.direction = 'DEPOSIT' THEN m.amount ELSE - m.amount
                              END) <> 1)
    OR (m.status = 'REJECTED'
        AND EXISTS
            (SELECT 1
             FROM cash_journal j
             WHERE j.movement_id = m.movement_id))
    OR NOT EXISTS
        (SELECT 1
         FROM cash_movement_outbox o
         WHERE o.movement_id = m.movement_id
             AND o.payload ->> 'status' = m.status)
UNION ALL
SELECT 'EVENT_OUTBOX','event:' || e.event_id
FROM order_events e
WHERE NOT EXISTS
        (SELECT 1
         FROM outbox_events o
         WHERE o.event_id = e.event_id
             AND o.order_id = e.order_id
             AND o.version = e.sequence
             AND o.event_type = e.to_state
             AND o.payload ->> 'status' = e.to_state)
UNION ALL
SELECT 'ORPHAN_OUTBOX','event:' || o.event_id
FROM outbox_events o
WHERE NOT EXISTS
        (SELECT 1
         FROM order_events e
         WHERE e.event_id = o.event_id)
ORDER BY kind,reference
""")
    List<Map<String, Object>> exceptions();

    @Select("SELECT count(*) FROM financial_migration_exceptions")
    long historicalExceptions();

    @Insert(
            "INSERT INTO reconciliation_runs VALUES(#{id},#{actor},#{now},#{matched},CAST(#{report}"
                    + " AS jsonb))")
    void save(Map<String, Object> args);

    @Select(
            "SELECT report::text FROM reconciliation_runs ORDER BY checked_at DESC,run_id DESC"
                    + " LIMIT 1")
    String latest();

    @Select("SELECT count(*) FROM order_execution_jobs WHERE NOT completed AND deadline<now()")
    long overdueJobs();

    @Select(
            """
            SELECT count(*)
            FROM instrument_trading_policies p
            JOIN instruments i USING(instrument_id)
            WHERE p.enabled
                AND i.is_active
                AND p.provider_symbol IS NOT NULL
                AND p.currency = 'USD'
            """)
    long enabledPolicies();

    @Select(
            """
            SELECT count(*) = 5
            FROM roles
            WHERE role_name IN ('CLIENT','TRADING_OPS','FINANCE','COMPLIANCE','ANALYST')
            """)
    boolean referenceDataReady();

    @Select("SELECT count(*) FROM order_execution_jobs WHERE NOT completed")
    long pendingJobs();

    @Select(
            """
            SELECT min(t.accepted_at)::text
            FROM order_execution_jobs j
            JOIN client_trades t ON t.trade_id = j.order_id
            WHERE NOT j.completed
            """)
    String oldestPendingOrder();

    @Select(
            """
SELECT bool_and(has_table_privilege(table_name, privilege))
FROM (
      VALUES ('client_cash_accounts'),('client_positions'),('order_reservations'),('order_execution_jobs'),('bank_references')) t(table_name)
CROSS JOIN (
            VALUES ('SELECT'),('INSERT'),('UPDATE')) p(privilege)
""")
    boolean operationalAccess();

    @Select(
            """
SELECT NOT EXISTS
    (SELECT 1
     FROM pg_roles
     WHERE rolname = current_user
         AND (rolsuper
              OR rolcreaterole
              OR rolcreatedb
              OR rolbypassrls))
AND NOT EXISTS
    (SELECT 1
     FROM pg_class
     WHERE relnamespace = 'public'::regnamespace
         AND relowner =
             (SELECT oid
              FROM pg_roles
              WHERE rolname = current_user))
AND NOT has_table_privilege('operations_access_grants','INSERT')
AND NOT has_table_privilege('operations_access_grants','UPDATE')
AND NOT has_table_privilege('operations_access_grants','DELETE')
AND NOT EXISTS
    (SELECT 1
     FROM (
           VALUES ('cash_journal'),('position_journal'),('order_fills'),('order_events'),('outbox_events'),('cash_movements'),('cash_movement_outbox'),('instrument_policy_audit'),('provider_setup_checks'),('reconciliation_runs'),('financial_migration_exceptions'),('order_quote_observations'),('order_rule_decisions'),('order_execution_attempts'),('operations_access_events'),('operations_access_contexts'),('operations_grant_events'),('operations_evidence_exports'),('order_decision_contexts'),('reconciliation_findings'),('support_case_events'),('support_case_notes'),('support_case_requests'),('support_case_exports')) t(table_name)
     CROSS JOIN (
                 VALUES ('UPDATE'),('DELETE'),('TRUNCATE')) p(privilege)
     WHERE has_table_privilege(table_name, privilege))
""")
    boolean evidenceProtected();
}
