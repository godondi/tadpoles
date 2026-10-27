package com.neueda.leap.mapper;

import java.util.Map;

/** Only these fixed projections can be selected. No user text becomes SQL. */
public final class OperationsEvidenceSql {
    private OperationsEvidenceSql() {}

    public static String query(Map<String, Object> parameters) {
        String section = (String) parameters.get("section");
        String projection =
                switch (section) {
                    case "events" ->
                            """
SELECT e.event_id::text AS reference,e.occurred_at AS "recordedAt",e.sequence,
       e.from_state AS "fromState",e.to_state AS "toState",e.reason,e.actor,
       e.quote_id AS "quoteId",e.fill_id AS "fillId"
FROM order_events e WHERE e.order_id=#{id}
""";
                    case "decisions" ->
                            """
SELECT e.decision_id::text AS reference,e.recorded_at AS "recordedAt",e.stage,
       e.policy_version AS "policyVersion",e.passed,e.code,e.explanation,
       e.quote_id AS "quoteId",c.facts::text AS "decisionContext"
FROM order_rule_decisions e LEFT JOIN order_decision_contexts c USING(decision_id)
WHERE e.order_id=#{id}
""";
                    case "quotes" ->
                            """
SELECT e.quote_id::text AS reference,e.recorded_at AS "recordedAt",e.stage,e.facts::text AS quote
FROM order_quote_observations e WHERE e.order_id=#{id}
""";
                    case "attempts" ->
                            """
SELECT e.attempt_id::text AS reference,e.recorded_at AS "recordedAt",e.attempt,e.outcome,
       CASE WHEN e.outcome='CLAIMED' THEN 'Worker lease acquired' ELSE e.reason END AS reason,
       e.quote_id AS "quoteId"
FROM order_execution_attempts e WHERE e.order_id=#{id}
""";
                    case "reservations" ->
                            """
SELECT e.order_id::text AS reference,e.created_at AS "recordedAt",e.resource,e.amount::text,e.state,
       e.ended_at AS "endedAt"
FROM order_reservations e WHERE e.order_id=#{id}
""";
                    case "fills" ->
                            """
SELECT e.fill_id::text AS reference,e.booked_at AS "recordedAt",
       e.execution_reference AS "executionReference",e.quote_id AS "quoteId",
       e.quantity::text,e.price::text,e.gross::text,e.fee::text,e.currency,
       e.posting_group AS "postingGroup",e.executed_at AS "executedAt"
FROM order_fills e WHERE e.order_id=#{id}
""";
                    case "postings" ->
                            """
SELECT e.posting_id::text AS reference,e.recorded_at AS "recordedAt",e.kind,e.delta::text,
       e.unit,e.fill_id AS "fillId",e.posting_group AS "postingGroup",e.actor
FROM (
    SELECT j.posting_id,j.recorded_at,'CASH' AS kind,j.delta,j.currency AS unit,
           j.fill_id,j.posting_group,j.actor,f.order_id
    FROM cash_journal j JOIN order_fills f USING(fill_id)
                    JOIN client_trades owner ON owner.trade_id=f.order_id AND owner.client_id=j.client_id
    UNION ALL
    SELECT j.posting_id,j.recorded_at,'POSITION',j.delta,'shares',
           j.fill_id,j.posting_group,j.actor,f.order_id
    FROM position_journal j JOIN order_fills f USING(fill_id)
                    JOIN client_trades owner ON owner.trade_id=f.order_id AND owner.client_id=j.client_id
) e WHERE e.order_id=#{id}
""";
                    case "notifications" ->
                            """
SELECT e.event_id::text AS reference,e.created_at AS "recordedAt",e.event_type AS type,
       e.version,e.schema_version AS "schemaVersion",'NOT_CONFIGURED' AS delivery
FROM outbox_events e WHERE e.order_id=#{id}
""";
                    default -> throw new IllegalArgumentException("Unknown evidence section");
                };
        String order =
                switch (section) {
                    case "events" -> "e.sequence";
                    case "attempts" -> "e.attempt,e.recorded_at,e.attempt_id";
                    default -> "\"recordedAt\",reference";
                };
        return projection
                + " AND EXISTS (SELECT 1 FROM client_trades t WHERE t.trade_id=#{id} AND "
                + OperationsAccessMapper.SCOPE
                + ") ORDER BY "
                + order
                + " LIMIT #{limit} OFFSET #{offset}";
    }
}
