package com.neueda.leap.mapper;

import com.neueda.leap.dto.WorkspaceDtos.Order;
import com.neueda.leap.dto.WorkspaceDtos.Position;
import com.neueda.leap.domain.Instrument;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WorkspaceReadMapper {
    String ORDER_COLUMNS = "SELECT t.trade_id AS orderId, t.instrument_id AS instrumentId, i.ticker, i.instrument_name AS instrumentName, i.currency, t.trade_type AS side, t.quantity, t.price AS recordedPrice, t.trade_date AS tradeDate, t.status, t.executed_at AS executedAt, t.reason FROM client_trades t JOIN instruments i ON i.instrument_id=t.instrument_id ";
    String ORDER_FILTER = """
        WHERE t.client_id=#{clientId}
        <if test="status != null">AND t.status=#{status}</if>
        <if test="side != null">AND t.trade_type=#{side}</if>
        <if test="instrumentId != null">AND t.instrument_id=#{instrumentId}</if>
        <if test="from != null">AND t.trade_date &gt;= #{from}</if>
        <if test="to != null">AND t.trade_date &lt;= #{to}</if>
        """;
    @Select("<script>" + ORDER_COLUMNS + ORDER_FILTER + " ORDER BY t.trade_date DESC, t.trade_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Order> orders(@Param("clientId") int clientId, @Param("status") String status, @Param("side") String side,
            @Param("instrumentId") Integer instrumentId, @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("limit") int limit, @Param("offset") int offset);
    @Select("<script>SELECT COUNT(*) FROM client_trades t " + ORDER_FILTER + "</script>")
    long orderCount(@Param("clientId") int clientId, @Param("status") String status, @Param("side") String side,
            @Param("instrumentId") Integer instrumentId, @Param("from") LocalDate from, @Param("to") LocalDate to);
    @Select(ORDER_COLUMNS + " WHERE t.client_id=#{clientId} AND t.trade_id=#{orderId}")
    Order order(@Param("clientId") int clientId, @Param("orderId") int orderId);
    @Select("""
        SELECT h.holding_id AS holdingId, h.instrument_id AS instrumentId, i.ticker,
               i.instrument_name AS name, i.currency, h.quantity, h.as_of_date AS asOfDate
        FROM client_holdings h JOIN instruments i ON i.instrument_id=h.instrument_id
        WHERE h.client_id=#{clientId} AND NOT EXISTS (
          SELECT 1 FROM client_holdings newer WHERE newer.client_id=h.client_id
          AND newer.instrument_id=h.instrument_id AND (newer.as_of_date > h.as_of_date
          OR (newer.as_of_date=h.as_of_date AND newer.holding_id > h.holding_id)))
        ORDER BY i.ticker, h.holding_id
        """)
    List<Position> positions(@Param("clientId") int clientId);
    String CATALOG_FILTER = """
        WHERE (LOWER(ticker) LIKE #{search} OR LOWER(instrument_name) LIKE #{search})
        <if test="assetClass != null">AND asset_class=#{assetClass}</if>
        """;
    @Select("<script>SELECT instrument_id, instrument_name, ticker, currency, asset_class, security_type, is_active FROM instruments " + CATALOG_FILTER + " ORDER BY ticker, instrument_id LIMIT #{limit} OFFSET #{offset}</script>")
    List<Instrument> instruments(@Param("search") String search, @Param("assetClass") String assetClass, @Param("limit") int limit, @Param("offset") int offset);
    @Select("<script>SELECT COUNT(*) FROM instruments " + CATALOG_FILTER + "</script>")
    long instrumentCount(@Param("search") String search, @Param("assetClass") String assetClass);
}
