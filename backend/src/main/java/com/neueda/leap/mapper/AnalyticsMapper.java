package com.neueda.leap.mapper;

import com.neueda.leap.domain.AnalyticsMetrics;
import com.neueda.leap.domain.ClientActivityTrend;
import com.neueda.leap.domain.InstrumentActivity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AnalyticsMapper {
    @Select("""
            SELECT COUNT(*) AS totalTrades,
                   COALESCE(SUM(CASE WHEN status = 'EXECUTED' THEN 1 ELSE 0 END), 0) AS executedTrades,
                   COALESCE(SUM(CASE WHEN status = 'EXECUTED' THEN quantity ELSE 0 END), 0) AS executedQuantity,
                   COALESCE(SUM(CASE WHEN status = 'EXECUTED' THEN quantity * price ELSE 0 END), 0) AS executedNotional,
                   COUNT(DISTINCT client_id) AS activeClients,
                   COUNT(DISTINCT instrument_id) AS activeInstruments
            FROM client_trades
            WHERE (#{fromDate} IS NULL OR trade_date >= #{fromDate})
              AND (#{toDate} IS NULL OR trade_date <= #{toDate})
            """)
    AnalyticsMetrics getAnalyticsMetrics(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Select("""
            SELECT ct.instrument_id AS instrumentId,
                   i.ticker AS ticker,
                   i.instrument_name AS instrumentName,
                   COUNT(*) AS tradeCount,
                   COALESCE(SUM(CASE WHEN ct.status = 'EXECUTED' THEN ct.quantity * ct.price ELSE 0 END), 0) AS executedNotional
            FROM client_trades ct
            JOIN instruments i
              ON i.instrument_id = ct.instrument_id
            WHERE (#{fromDate} IS NULL OR ct.trade_date >= #{fromDate})
              AND (#{toDate} IS NULL OR ct.trade_date <= #{toDate})
            GROUP BY ct.instrument_id, i.ticker, i.instrument_name
            ORDER BY tradeCount DESC, executedNotional DESC, i.ticker ASC
            LIMIT 5
            """)
    List<InstrumentActivity> listMostActiveInstruments(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Select("""
            SELECT trade_date AS periodStart,
                   COUNT(*) AS tradeCount,
                   COALESCE(SUM(CASE WHEN status = 'EXECUTED' THEN 1 ELSE 0 END), 0) AS executedTradeCount,
                   COUNT(DISTINCT client_id) AS activeClientCount
            FROM client_trades
            WHERE (#{fromDate} IS NULL OR trade_date >= #{fromDate})
              AND (#{toDate} IS NULL OR trade_date <= #{toDate})
            GROUP BY trade_date
            ORDER BY trade_date
            """)
    List<ClientActivityTrend> listClientActivityTrends(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
