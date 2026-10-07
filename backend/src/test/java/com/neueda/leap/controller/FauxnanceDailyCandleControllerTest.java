package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.FauxnanceDailyCandle;
import com.neueda.leap.domain.FauxnanceDailyCandleData;
import com.neueda.leap.domain.FauxnanceDailyCandleResponse;
import com.neueda.leap.domain.FauxnanceMarketDataMeta;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.FauxnanceDailyCandleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FauxnanceDailyCandleController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class FauxnanceDailyCandleControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FauxnanceDailyCandleService fauxnanceDailyCandleService;

    @Test
    void getCandlesReturnsJsonResponse() throws Exception {
        FauxnanceDailyCandleResponse response = buildResponse();
        when(fauxnanceDailyCandleService.getCandles(
                "AAPL",
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-03"),
                "1d"
        )).thenReturn(response);

        mockMvc.perform(get("/candles/AAPL")
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-01-03")
                        .queryParam("interval", "1d")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.interval").value("1d"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.candles[0].date").value("2026-01-02"))
                .andExpect(jsonPath("$.data.candles[0].adjclose").value(191.81))
                .andExpect(jsonPath("$.meta.symbol").value("AAPL"))
                .andExpect(jsonPath("$.meta.source").value("stored"))
                .andExpect(jsonPath("$.meta.disclaimer").value("Educational data. Not for investment use."));
    }

    @Test
    void getCandlesReturnsBadRequestForInvalidRange() throws Exception {
        when(fauxnanceDailyCandleService.getCandles(
                "AAPL",
                LocalDate.parse("2026-08-27"),
                LocalDate.parse("2026-08-26"),
                null
        )).thenThrow(new IllegalArgumentException("'from' must be on or before 'to'."));

        mockMvc.perform(get("/candles/AAPL")
                        .queryParam("from", "2026-08-27")
                        .queryParam("to", "2026-08-26")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must be on or before 'to'."));
    }

    private FauxnanceDailyCandleResponse buildResponse() {
        FauxnanceDailyCandle candle = new FauxnanceDailyCandle();
        candle.setDate(LocalDate.parse("2026-01-02"));
        candle.setOpen(new BigDecimal("189.12"));
        candle.setHigh(new BigDecimal("192.48"));
        candle.setLow(new BigDecimal("188.77"));
        candle.setClose(new BigDecimal("191.81"));
        candle.setAdjclose(new BigDecimal("191.81"));
        candle.setVolume(42000000L);
        candle.setSynthetic(false);

        FauxnanceDailyCandleData data = new FauxnanceDailyCandleData();
        data.setSymbol("AAPL");
        data.setInterval("1d");
        data.setCurrency("USD");
        data.setCandles(List.of(candle));

        FauxnanceMarketDataMeta meta = new FauxnanceMarketDataMeta();
        meta.setAsOf(OffsetDateTime.parse("2026-01-03T00:00:00Z"));
        meta.setDisclaimer("Educational data. Not for investment use.");
        meta.setSymbol("AAPL");
        meta.setSource("stored");
        meta.setStale(false);
        meta.setPartial(false);

        FauxnanceDailyCandleResponse response = new FauxnanceDailyCandleResponse();
        response.setData(data);
        response.setMeta(meta);
        return response;
    }
}
