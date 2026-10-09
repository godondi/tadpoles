package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.AnalyticsMetrics;
import com.neueda.leap.domain.AnalyticsSummary;
import com.neueda.leap.domain.ClientActivityTrend;
import com.neueda.leap.domain.InstrumentActivity;
import com.neueda.leap.mapper.AnalyticsMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {
    @Mock
    private AnalyticsMapper analyticsMapper;

    private AnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsServiceImpl(analyticsMapper);
    }

    @Test
    void getAnalyticsSummaryReturnsAggregatedInsights() {
        LocalDate fromDate = LocalDate.of(2026, 9, 24);
        LocalDate toDate = LocalDate.of(2026, 9, 26);
        when(analyticsMapper.getAnalyticsMetrics(fromDate, toDate)).thenReturn(buildMetrics());
        when(analyticsMapper.listMostActiveInstruments(fromDate, toDate)).thenReturn(List.of(buildInstrumentActivity()));
        when(analyticsMapper.listClientActivityTrends(fromDate, toDate)).thenReturn(List.of(buildClientTrend()));

        AnalyticsSummary summary = analyticsService.getAnalyticsSummary(fromDate, toDate);

        assertEquals("INTERNAL", summary.getScope());
        assertEquals(fromDate, summary.getFromDate());
        assertEquals(toDate, summary.getToDate());
        assertEquals(4L, summary.getMetrics().getTotalTrades());
        assertEquals(1, summary.getMostActiveInstruments().size());
        assertEquals(1, summary.getClientActivityTrends().size());
        assertNotNull(summary.getGeneratedAt());
    }

    @Test
    void getAnalyticsSummaryRejectsInvalidDateRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> analyticsService.getAnalyticsSummary(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 26))
        );
    }

    private AnalyticsMetrics buildMetrics() {
        AnalyticsMetrics metrics = new AnalyticsMetrics();
        metrics.setTotalTrades(4L);
        metrics.setExecutedTrades(2L);
        metrics.setExecutedQuantity(new BigDecimal("4.000000"));
        metrics.setExecutedNotional(new BigDecimal("385.00"));
        metrics.setActiveClients(2L);
        metrics.setActiveInstruments(2L);
        return metrics;
    }

    private InstrumentActivity buildInstrumentActivity() {
        InstrumentActivity activity = new InstrumentActivity();
        activity.setInstrumentId(11);
        activity.setTicker("AAPL");
        activity.setInstrumentName("Apple Inc");
        activity.setTradeCount(3L);
        activity.setExecutedNotional(new BigDecimal("385.00"));
        return activity;
    }

    private ClientActivityTrend buildClientTrend() {
        ClientActivityTrend trend = new ClientActivityTrend();
        trend.setPeriodStart(LocalDate.of(2026, 9, 25));
        trend.setTradeCount(2L);
        trend.setExecutedTradeCount(2L);
        trend.setActiveClientCount(2L);
        return trend;
    }
}
