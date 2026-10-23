package com.neueda.leap.mapper;

import java.util.Map;

/** Fixed redacted projections; request values never become SQL identifiers or projections. */
public final class ComplianceEvidenceSql {
    private ComplianceEvidenceSql() {}

    public static String query(Map<String, Object> parameters) {
        String section = (String) parameters.get("section");
        boolean count = Boolean.TRUE.equals(parameters.get("count"));
        String projection =
                switch (section) {
                    case "events" ->
                            """
SELECT e.event_id::text AS reference,e.sequence,e.actor,e.occurred_at AS "occurredAt",
       e.from_state AS "fromState",e.to_state AS "toState",e.reason,
       e.quote_id::text AS "quoteId",e.fill_id::text AS "fillId"
FROM order_events e WHERE e.order_id=#{id}
""";
                    case "decisions" ->
                            """
                            SELECT e.decision_id::text AS reference,NULL::bigint AS sequence,
                                   NULL::text AS actor,e.recorded_at AS "occurredAt",
                                   e.stage,e.policy_version AS "policyVersion",e.passed,e.code,
                                   e.explanation,e.quote_id::text AS "quoteId",
                                   c.facts::text AS "decisionContext"
                            FROM order_rule_decisions e
                            LEFT JOIN order_decision_contexts c USING(decision_id)
                            WHERE e.order_id=#{id}
                            """;
                    case "quotes" ->
                            """
                            SELECT e.quote_id::text AS reference,NULL::bigint AS sequence,
                                   NULL::text AS actor,e.recorded_at AS "occurredAt",
                                   e.stage,e.facts::text AS facts
                            FROM order_quote_observations e WHERE e.order_id=#{id}
                            """;
                    case "attempts" ->
                            """
                            SELECT e.attempt_id::text AS reference,NULL::bigint AS sequence,
                                   NULL::text AS actor,e.recorded_at AS "occurredAt",
                                   e.attempt,e.outcome,e.reason,e.quote_id::text AS "quoteId"
                            FROM order_execution_attempts e WHERE e.order_id=#{id}
                            """;
                    case "reservations" ->
                            """
SELECT e.order_id::text AS reference,NULL::bigint AS sequence,
       NULL::text AS actor,e.created_at AS "occurredAt",
       e.resource,e.amount::text AS amount,e.state,e.ended_at AS "endedAt"
FROM order_reservations e WHERE e.order_id=#{id}
""";
                    case "fills" ->
                            """
SELECT e.fill_id::text AS reference,NULL::bigint AS sequence,
       NULL::text AS actor,e.booked_at AS "occurredAt",
       e.execution_reference AS "executionReference",
       e.quote_id::text AS "quoteId",e.quantity::text AS quantity,
       e.price::text AS price,e.gross::text AS gross,e.fee::text AS fee,
       e.currency,e.posting_group::text AS "postingGroup",
       e.executed_at AS "executedAt"
FROM order_fills e WHERE e.order_id=#{id}
""";
                    case "postings" ->
                            """
SELECT e.reference,NULL::bigint AS sequence,e.actor,e.occurred_at AS "occurredAt",
       e.kind,e.delta::text AS delta,e.unit,e.fill_id::text AS "fillId",
       e.posting_group::text AS "postingGroup"
FROM (
  SELECT j.posting_id::text AS reference,j.recorded_at AS occurred_at,j.actor,
         'CASH' AS kind,j.delta,j.currency AS unit,j.fill_id,j.posting_group,
         f.order_id
  FROM cash_journal j
  JOIN order_fills f USING(fill_id)
  JOIN client_trades owner
    ON owner.trade_id=f.order_id AND owner.client_id=j.client_id
  UNION ALL
  SELECT j.posting_id::text,j.recorded_at,j.actor,'POSITION',j.delta,
         'shares',j.fill_id,j.posting_group,f.order_id
  FROM position_journal j
  JOIN order_fills f USING(fill_id)
  JOIN client_trades owner
    ON owner.trade_id=f.order_id AND owner.client_id=j.client_id
) e WHERE e.order_id=#{id}
""";
                    case "notifications" ->
                            """
SELECT e.event_id::text AS reference,NULL::bigint AS sequence,
       NULL::text AS actor,e.created_at AS "occurredAt",
       e.event_type AS type,e.version,e.schema_version AS "schemaVersion",
       'NOT_CONFIGURED' AS delivery
FROM outbox_events e WHERE e.order_id=#{id}
""";
                    default -> throw new IllegalArgumentException("Unknown evidence section");
                };
        String scoped =
                """
AND EXISTS (
    SELECT 1
    FROM client_trades scoped_order
    JOIN compliance_access_grants g ON g.client_id=scoped_order.client_id
    WHERE scoped_order.trade_id=#{id}
      AND (#{managedClient}::integer IS NULL OR scoped_order.client_id=#{managedClient})
      AND (#{managedOrder}::integer IS NULL OR scoped_order.trade_id=#{managedOrder})
      AND g.staff_user_id=#{actor}
      AND g.permission=#{permission}
      AND g.revoked_at IS NULL
      AND g.valid_from <= #{now}
      AND g.valid_until > #{now})
""";
        if (count) {
            return "SELECT count(*) FROM (" + projection + scoped + ") visible";
        }
        String order =
                switch (section) {
                    case "events" -> "e.sequence,e.occurred_at,e.event_id";
                    case "attempts" -> "e.attempt,e.recorded_at,e.attempt_id";
                    case "reservations" -> "e.created_at,e.order_id,e.resource";
                    case "postings" -> "\"occurredAt\",reference,e.kind";
                    default -> "\"occurredAt\",reference";
                };
        return projection + scoped + " ORDER BY " + order + " LIMIT #{limit} OFFSET #{offset}";
    }
}
