package com.neueda.leap.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FinanceSummaryMapper {
    @Select("SELECT transaction_timestamp()")
    Instant transactionTimestamp();

    /** Each source is aggregated independently to prevent fill/journal row multiplication. */
    @Select(
            """
            WITH movement_totals AS (
                SELECT
                    COALESCE(SUM(m.amount) FILTER (WHERE m.direction = 'DEPOSIT'), 0::numeric)
                        AS settled_deposits,
                    COALESCE(SUM(m.amount) FILTER (WHERE m.direction = 'WITHDRAW'), 0::numeric)
                        AS settled_withdrawals
                FROM cash_movements m
                WHERE m.status = 'SETTLED'
                    AND m.currency = 'USD'
                    AND m.recorded_at >= #{fromInclusive}
                    AND m.recorded_at < #{toExclusive}
            ), fill_totals AS (
                SELECT
                    COALESCE(SUM(f.gross) FILTER (WHERE t.trade_type = 'BUY'), 0::numeric)
                        AS gross_buy_consideration,
                    COALESCE(SUM(f.gross) FILTER (WHERE t.trade_type = 'SELL'), 0::numeric)
                        AS gross_sell_consideration,
                    COUNT(DISTINCT f.fill_id)::bigint AS booked_fill_count
                FROM order_fills f
                JOIN client_trades t ON t.trade_id = f.order_id
                WHERE f.currency = 'USD'
                    AND t.trade_type IN ('BUY', 'SELL')
                    AND f.booked_at >= #{fromInclusive}
                    AND f.booked_at < #{toExclusive}
            )
            SELECT
                movement_totals.settled_deposits,
                movement_totals.settled_withdrawals,
                fill_totals.gross_buy_consideration,
                fill_totals.gross_sell_consideration,
                fill_totals.booked_fill_count
            FROM movement_totals
            CROSS JOIN fill_totals
            """)
    @ConstructorArgs({
        @Arg(column = "settled_deposits", javaType = BigDecimal.class),
        @Arg(column = "settled_withdrawals", javaType = BigDecimal.class),
        @Arg(column = "gross_buy_consideration", javaType = BigDecimal.class),
        @Arg(column = "gross_sell_consideration", javaType = BigDecimal.class),
        @Arg(column = "booked_fill_count", javaType = long.class)
    })
    Aggregate aggregate(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    record Aggregate(
            BigDecimal settledDeposits,
            BigDecimal settledWithdrawals,
            BigDecimal grossBuyConsideration,
            BigDecimal grossSellConsideration,
            long bookedFillCount) {}
}
