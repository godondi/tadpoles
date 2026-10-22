package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface ComplianceEvidenceMapper {
    List<String> SECTIONS =
            List.of(
                    "events",
                    "decisions",
                    "quotes",
                    "attempts",
                    "reservations",
                    "fills",
                    "postings",
                    "notifications");

    String SCOPE =
            """
            EXISTS (
                SELECT 1
                FROM compliance_access_grants g
                WHERE g.staff_user_id=#{actor}
                  AND g.permission=#{permission}
                  AND g.client_id=t.client_id
                  AND g.revoked_at IS NULL
                  AND g.valid_from &lt;= #{now}
                  AND g.valid_until > #{now})
            """;

    String COLUMNS =
            """
            t.trade_id AS "orderId",
            (SELECT min(g.account_reference) FROM compliance_access_grants g
             WHERE g.staff_user_id=#{actor} AND g.permission='EVIDENCE_READ'
               AND g.client_id=t.client_id AND g.revoked_at IS NULL
               AND g.valid_from &lt;= #{now} AND g.valid_until > #{now}) AS "clientReference",
            i.ticker AS instrument,t.trade_type AS side,t.quantity::text AS quantity,
            coalesce(p.quantity_unit,'shares') AS unit,t.status,t.submitted_at AS "submittedAt",
            t.accepted_at AS "acceptedAt",
            (SELECT max(e.occurred_at) FROM order_events e
             WHERE e.order_id=t.trade_id AND e.to_state=t.status) AS "outcomeAt",
            CASE WHEN t.record_kind='SELF_DIRECTED' THEN 'PROVIDER_BACKED'
                 ELSE 'LEGACY_UNKNOWN' END AS "executionMode",
            t.record_kind AS "recordKind",
            CASE
              WHEN t.record_kind='LEGACY' THEN 'UNKNOWN'
              WHEN NOT EXISTS (SELECT 1 FROM order_events e WHERE e.order_id=t.trade_id)
                THEN 'UNKNOWN'
              WHEN t.status='FILLED'
                AND EXISTS (SELECT 1 FROM order_fills f WHERE f.order_id=t.trade_id)
                AND EXISTS (SELECT 1 FROM order_quote_observations q WHERE q.order_id=t.trade_id)
                AND EXISTS (SELECT 1 FROM order_rule_decisions d WHERE d.order_id=t.trade_id)
                AND NOT EXISTS (
                  SELECT 1 FROM order_rule_decisions d
                  LEFT JOIN order_decision_contexts c USING(decision_id)
                  WHERE d.order_id=t.trade_id AND c.decision_id IS NULL)
                THEN 'PASS'
              WHEN t.status='REJECTED'
                AND EXISTS (SELECT 1 FROM order_rule_decisions d WHERE d.order_id=t.trade_id)
                AND NOT EXISTS (
                  SELECT 1 FROM order_rule_decisions d
                  LEFT JOIN order_decision_contexts c USING(decision_id)
                  WHERE d.order_id=t.trade_id AND c.decision_id IS NULL)
                THEN 'PASS'
              ELSE 'NOT_EVALUATED'
            END AS "evidenceCompleteness",
            t.client_id AS "clientId",t.instrument_id AS "instrumentId",
            t.policy_snapshot::text AS "policySnapshot"
            """;

    String FROM =
            """
            FROM client_trades t
            JOIN instruments i ON i.instrument_id=t.instrument_id
            LEFT JOIN instrument_trading_policies p ON p.instrument_id=t.instrument_id
            WHERE
            """
                    + SCOPE;

    @Select(
            """
            <script>
            SELECT
            """
                    + COLUMNS
                    + FROM
                    + """
            <if test='orderId != null'>AND t.trade_id=#{orderId}</if>
            <if test='clientReference != null'>AND EXISTS (
              SELECT 1 FROM compliance_access_grants g
              WHERE g.staff_user_id=#{actor} AND g.permission='EVIDENCE_READ'
                AND g.client_id=t.client_id AND g.account_reference=#{clientReference}
                AND g.revoked_at IS NULL AND g.valid_from &lt;= #{now} AND g.valid_until > #{now})
            </if>
            <if test='instrument != null'>AND i.ticker=#{instrument}</if>
            <if test='status != null'>AND t.status=#{status}</if>
            <if test='from != null'>AND t.submitted_at >= #{from} AND t.submitted_at &lt; #{to}</if>
            ORDER BY t.submitted_at DESC NULLS LAST,t.trade_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Map<String, Object>> orders(Map<String, Object> parameters);

    @Select(
            """
            <script>
            SELECT count(*)
            """
                    + FROM
                    + """
            <if test='orderId != null'>AND t.trade_id=#{orderId}</if>
            <if test='clientReference != null'>AND EXISTS (
              SELECT 1 FROM compliance_access_grants g
              WHERE g.staff_user_id=#{actor} AND g.permission='EVIDENCE_READ'
                AND g.client_id=t.client_id AND g.account_reference=#{clientReference}
                AND g.revoked_at IS NULL AND g.valid_from &lt;= #{now} AND g.valid_until > #{now})
            </if>
            <if test='instrument != null'>AND i.ticker=#{instrument}</if>
            <if test='status != null'>AND t.status=#{status}</if>
            <if test='from != null'>AND t.submitted_at >= #{from} AND t.submitted_at &lt; #{to}</if>
            </script>
            """)
    long count(Map<String, Object> parameters);

    @Select("<script>SELECT " + COLUMNS + FROM + " AND t.trade_id=#{id}</script>")
    Map<String, Object> order(Map<String, Object> parameters);

    @SelectProvider(type = ComplianceEvidenceSql.class, method = "query")
    List<Map<String, Object>> section(Map<String, Object> parameters);

    @SelectProvider(type = ComplianceEvidenceSql.class, method = "query")
    long sectionCount(Map<String, Object> parameters);

    @Select(
            """
            SELECT p.policy_version AS version,p.revision,p.enabled,i.is_active AS active,
                   p.buy_allowed AS "buyAllowed",p.sell_allowed AS "sellAllowed",
                   p.venue,p.quantity_unit AS "quantityUnit",p.price_unit AS "priceUnit",
                   p.currency,p.quantity_step::text AS "quantityStep",
                   p.currency_scale AS "currencyScale",p.max_quantity::text AS "maxQuantity",
                   p.max_notional::text AS "maxNotional",
                   p.quote_max_age_seconds AS "quoteMaxAgeSeconds",
                   p.future_skew_seconds AS "futureSkewSeconds",
                   p.retry_seconds AS "retrySeconds",
                   p.max_attempts AS "maxAttempts",
                   p.session,p.raw_price_unit AS "rawPriceUnit",
                   p.price_multiplier::text AS "priceMultiplier",
                   p.provider_symbol AS "providerSymbol"
            FROM instrument_trading_policies p
            JOIN instruments i USING(instrument_id)
            WHERE p.instrument_id=#{instrumentId}
            """)
    Map<String, Object> currentPolicy(@Param("instrumentId") int instrumentId);

    @Select(
            """
            SELECT f.finding_id::text AS reference,f.kind,f.detected_at AS "occurredAt"
            FROM reconciliation_findings f
            WHERE f.order_id=#{orderId}
              AND EXISTS (
                SELECT 1 FROM compliance_access_grants g
                WHERE g.staff_user_id=#{actor} AND g.permission='EVIDENCE_READ'
                  AND g.client_id=f.client_id AND g.revoked_at IS NULL
                  AND g.valid_from <= #{now} AND g.valid_until > #{now})
            ORDER BY f.detected_at,f.finding_id
            LIMIT #{limit}
            """)
    List<Map<String, Object>> findings(Map<String, Object> parameters);
}
