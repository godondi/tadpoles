package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.ClientTrade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/schema.sql", "classpath:mapper/data.sql"})
class ClientTradeMapperTest {
    @Autowired
    private ClientTradeMapper clientTradeMapper;

    @Test
    void listClientTradesReturnsRows() {
        List<ClientTrade> trades = clientTradeMapper.listClientTrades(7);

        assertEquals(1, trades.size());
        assertEquals(21, trades.get(0).getTradeId());
    }

    @Test
    void getClientTradeReturnsTrade() {
        ClientTrade trade = clientTradeMapper.getClientTrade(7, 21);

        assertNotNull(trade);
        assertEquals("BUY", trade.getTradeType());
        assertEquals("APPROVED", trade.getStatus());
    }

    @Test
    void insertClientTradeCreatesNewRowWithGeneratedId() {
        ClientTrade trade = new ClientTrade();
        trade.setClientId(7);
        trade.setInstrumentId(11);
        trade.setSubmittedByUserId(1);
        trade.setApprovedByUserId(1);
        trade.setTradeType("SELL");
        trade.setQuantity(new BigDecimal("1.250000"));
        trade.setPrice(new BigDecimal("115.00"));
        trade.setTradeDate(LocalDate.of(2026, 9, 25));
        trade.setStatus("PENDING");
        trade.setReason("Trim position");

        int rows = clientTradeMapper.insertClientTrade(trade);

        assertEquals(1, rows);
        assertNotNull(trade.getTradeId());
        ClientTrade stored = clientTradeMapper.getClientTrade(7, trade.getTradeId());
        assertEquals("SELL", stored.getTradeType());
    }

    @Test
    void updateClientTradeUpdatesRequestedFields() {
        ClientTrade trade = new ClientTrade();
        trade.setClientId(7);
        trade.setTradeId(21);
        trade.setApprovedByUserId(1);
        trade.setStatus("REJECTED");
        trade.setExecutedAt(LocalDateTime.of(2026, 9, 24, 12, 0));
        trade.setReason("Desk rejected");

        int rows = clientTradeMapper.updateClientTrade(trade);

        assertEquals(1, rows);
        ClientTrade stored = clientTradeMapper.getClientTrade(7, 21);
        assertEquals("REJECTED", stored.getStatus());
        assertEquals(1, stored.getApprovedByUserId());
    }

    @Test
    void getTradeForUpdateReturnsTrade() {
        ClientTrade trade = clientTradeMapper.getTradeForUpdate(21);

        assertNotNull(trade);
        assertEquals(7, trade.getClientId());
    }

    @Test
    void markTradeExecutedUpdatesTradeState() {
        int rows = clientTradeMapper.markTradeExecuted(
                21,
                1,
                new BigDecimal("111.75"),
                LocalDateTime.of(2026, 9, 24, 11, 30),
                "Executed on market"
        );

        assertEquals(1, rows);
        ClientTrade stored = clientTradeMapper.getClientTrade(7, 21);
        assertEquals("EXECUTED", stored.getStatus());
        assertEquals(0, new BigDecimal("111.75").compareTo(stored.getPrice()));
    }
}


