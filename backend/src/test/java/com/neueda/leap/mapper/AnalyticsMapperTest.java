package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.AnalyticsMetrics;
import com.neueda.leap.domain.ClientActivityTrend;
import com.neueda.leap.domain.InstrumentActivity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/schema.sql", "classpath:mapper/data.sql", "classpath:mapper/analytics_data.sql"})
class AnalyticsMapperTest {
    @Autowired
    private AnalyticsMapper analyticsMapper;

    @Test
    void getAnalyticsMetricsReturnsAggregatedValues() {
        AnalyticsMetrics metrics = analyticsMapper.getAnalyticsMetrics(null, null);

        assertNotNull(metrics);
        assertEquals(4L, metrics.getTotalTrades());
        assertEquals(2L, metrics.getExecutedTrades());
        assertEquals(0, new BigDecimal("4.000000").compareTo(metrics.getExecutedQuantity()));
        assertEquals(0, new BigDecimal("385.00").compareTo(metrics.getExecutedNotional()));
        assertEquals(2L, metrics.getActiveClients());
        assertEquals(2L, metrics.getActiveInstruments());
    }

    @Test
    void listMostActiveInstrumentsReturnsRankedRows() {
        List<InstrumentActivity> instruments = analyticsMapper.listMostActiveInstruments(null, null);

        assertEquals(2, instruments.size());
        assertEquals(11, instruments.get(0).getInstrumentId());
        assertEquals("AAPL", instruments.get(0).getTicker());
        assertEquals(3L, instruments.get(0).getTradeCount());
        assertEquals(12, instruments.get(1).getInstrumentId());
    }

    @Test
    void listClientActivityTrendsSupportsDateFiltering() {
        List<ClientActivityTrend> trends = analyticsMapper.listClientActivityTrends(
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 9, 26)
        );

        assertEquals(2, trends.size());
        assertEquals(LocalDate.of(2026, 9, 25), trends.get(0).getPeriodStart());
        assertEquals(2L, trends.get(0).getTradeCount());
        assertEquals(2L, trends.get(0).getExecutedTradeCount());
        assertEquals(2L, trends.get(0).getActiveClientCount());
    }
}
