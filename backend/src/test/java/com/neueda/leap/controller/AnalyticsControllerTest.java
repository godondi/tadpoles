package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.AnalyticsMetrics;
import com.neueda.leap.domain.AnalyticsSummary;
import com.neueda.leap.domain.ClientActivityTrend;
import com.neueda.leap.domain.InstrumentActivity;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.AnalyticsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class AnalyticsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    void getAnalyticsSummaryReturnsInsightsForAnalyst() throws Exception {
        when(analyticsService.getAnalyticsSummary(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 26)))
                .thenReturn(buildSummary());

        mockMvc.perform(get("/api/analytics/summary")
                        .param("fromDate", "2026-09-24")
                        .param("toDate", "2026-09-26")
                        .with(jwtWithRoles("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("INTERNAL"))
                .andExpect(jsonPath("$.metrics.totalTrades").value(4))
                .andExpect(jsonPath("$.mostActiveInstruments[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$.clientActivityTrends[0].tradeCount").value(2));
    }

    @Test
    void getAnalyticsSummaryReturnsForbiddenForClient() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .with(jwtWithRoles("CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied"));
    }

    @Test
    void getAnalyticsSummaryReturnsBadRequestForInvalidDateRange() throws Exception {
        when(analyticsService.getAnalyticsSummary(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 26)))
                .thenThrow(new IllegalArgumentException("From date must be on or before to date."));

        mockMvc.perform(get("/api/analytics/summary")
                        .param("fromDate", "2026-09-27")
                        .param("toDate", "2026-09-26")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("From date must be on or before to date."));
    }

    private AnalyticsSummary buildSummary() {
        AnalyticsMetrics metrics = new AnalyticsMetrics();
        metrics.setTotalTrades(4L);
        metrics.setExecutedTrades(2L);
        metrics.setExecutedQuantity(new BigDecimal("4.000000"));
        metrics.setExecutedNotional(new BigDecimal("385.00"));
        metrics.setActiveClients(2L);
        metrics.setActiveInstruments(2L);

        InstrumentActivity instrumentActivity = new InstrumentActivity();
        instrumentActivity.setInstrumentId(11);
        instrumentActivity.setTicker("AAPL");
        instrumentActivity.setInstrumentName("Apple Inc");
        instrumentActivity.setTradeCount(3L);
        instrumentActivity.setExecutedNotional(new BigDecimal("385.00"));

        ClientActivityTrend trend = new ClientActivityTrend();
        trend.setPeriodStart(LocalDate.of(2026, 9, 25));
        trend.setTradeCount(2L);
        trend.setExecutedTradeCount(2L);
        trend.setActiveClientCount(2L);

        AnalyticsSummary summary = new AnalyticsSummary();
        summary.setScope("INTERNAL");
        summary.setGeneratedAt(LocalDateTime.of(2026, 10, 9, 16, 0));
        summary.setFromDate(LocalDate.of(2026, 9, 24));
        summary.setToDate(LocalDate.of(2026, 9, 26));
        summary.setMetrics(metrics);
        summary.setMostActiveInstruments(List.of(instrumentActivity));
        summary.setClientActivityTrends(List.of(trend));
        return summary;
    }
}
