package com.neueda.leap.mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
@MybatisTest
@Sql(scripts={"classpath:mapper/schema.sql","classpath:mapper/data.sql"})
class WorkspaceReadMapperTest {
    @Autowired WorkspaceReadMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Test void scopedCountAndPageUseTheSameFilters() {
        assertEquals(1,mapper.orderCount(7,"APPROVED","BUY",11,null,null));
        var result=mapper.orders(7,"APPROVED","BUY",11,null,null,20,0);
        assertEquals(1,result.size()); assertEquals(21,result.get(0).orderId());
        assertEquals(0,mapper.orderCount(8,null,null,null,null,null));
        assertNull(mapper.order(8,21));
        assertEquals(0,mapper.orders(7,null,null,null,null,null,20,20).size());
        assertEquals(0,mapper.orderCount(7,null,"SELL",null,null,null));
        assertEquals(0,mapper.orderCount(7,null,null,null,LocalDate.of(2099,1,1),null));
    }
    @Test void currentSnapshotIsNotSummedWithHistory() {
        jdbc.update("INSERT INTO client_holdings(client_id,instrument_id,quantity,as_of_date) VALUES (7,11,99,'2026-09-25')");
        var result=mapper.positions(7);
        assertEquals(1,result.size()); assertEquals(0,result.get(0).quantity().compareTo(new java.math.BigDecimal("99")));
        assertEquals(0,mapper.positions(8).size());
    }
    @Test void catalogSearchAndPaginationAreBounded() {
        assertEquals(1,mapper.instrumentCount("%aapl%",null));
        assertEquals("AAPL",mapper.instruments("%aapl%",null,1,0).get(0).getTicker());
        assertEquals(0,mapper.instruments("%aapl%",null,1,1).size());
        assertEquals(
                "Nasdaq Global Select Market",
                mapper.instruments("%aapl%", null, 1, 0).get(0).getExchange());
        jdbc.update(
                "INSERT INTO instruments(instrument_name,ticker,currency,asset_class,security_type,exchange) VALUES (?,?,?,?,?,?)",
                "Euro / British Pound",
                "FX:EURGBP",
                "GBP",
                "Foreign Exchange",
                "FX_PAIR",
                "OTC");
        var fx = mapper.instruments("%fx:eurgbp%", null, 20, 0).get(0);
        assertEquals("GBP", fx.getCurrency());
        assertEquals("OTC", fx.getExchange());
    }

    @Test void marketAndExchangeFiltersApplyToBothRowsAndCounts() {
        jdbc.update("INSERT INTO instruments(instrument_name,ticker,currency,asset_class,security_type,exchange) VALUES (?,?,?,?,?,?)",
                "Bitcoin", "X:BTC-USD", "USD", "Cryptocurrency", "CRYPTO", "OTC");
        jdbc.update("INSERT INTO instruments(instrument_name,ticker,currency,asset_class,security_type,exchange) VALUES (?,?,?,?,?,?)",
                "Euro / US Dollar", "FX:EURUSD", "USD", "Foreign Exchange", "FX_PAIR", "OTC");
        assertEquals(1, mapper.instrumentCount("%%", null, "CRYPTO", "OTC"));
        assertEquals("X:BTC-USD", mapper.instruments("%%", null, "CRYPTO", "OTC", 20, 0).get(0).getTicker());
        assertEquals(1, mapper.instrumentCount("%%", null, "FOREX", "OTC"));
        assertEquals("FX:EURUSD", mapper.instruments("%%", null, "FOREX", "OTC", 20, 0).get(0).getTicker());
        assertEquals(0, mapper.instrumentCount("%%", null, "CRYPTO", "NYSE"));
        assertEquals(0, mapper.instruments("%%", null, "FOREX", "NYSE", 20, 0).size());
        assertTrue(mapper.instruments("%%", null, "STOCK_ETF", null, 20, 0).stream()
                .noneMatch(row -> row.getTicker().startsWith("X:") || row.getTicker().startsWith("FX:")));
    }

    @Test void broadSearchIncludesMatchingMarketsOnTheFirstPage() {
        for (int i = 0; i < 30; i++) {
            jdbc.update("INSERT INTO instruments(instrument_id,instrument_name,ticker,currency,asset_class,security_type,exchange) VALUES (?,?,?,?,?,?,?)",
                    1000 + i, "Bitcoin related equity " + i, String.format("B%02d", i), "USD", "Equity", "COMMON_STOCK", "NYSE");
        }
        jdbc.update("INSERT INTO instruments(instrument_id,instrument_name,ticker,currency,asset_class,security_type,exchange) VALUES (?,?,?,?,?,?,?)",
                2000, "Bitcoin", "X:BTC-USD", "USD", "Cryptocurrency", "CRYPTO", "OTC");
        var page = mapper.instruments("%b%", null, null, null, 20, 0);
        assertEquals(20, page.size());
        assertTrue(page.stream().anyMatch(row -> row.getTicker().equals("X:BTC-USD")));
        assertTrue(page.stream().anyMatch(row -> row.getTicker().startsWith("B")));
    }
}
