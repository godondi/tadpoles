package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.FauxnanceQuote;
import com.neueda.leap.domain.FauxnanceQuoteMeta;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.FauxnanceQuoteService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FauxnanceQuoteController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class FauxnanceQuoteControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FauxnanceQuoteService fauxnanceQuoteService;

    @Test
    void getQuoteReturnsJsonResponse() throws Exception {
        FauxnanceQuoteResponse response = buildResponse();
        when(fauxnanceQuoteService.getQuote("AAPL")).thenReturn(response);

        mockMvc.perform(get("/quotes/AAPL").with(jwtWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.symbol").value("AAPL"))
                .andExpect(jsonPath("$.data.price").value(313.53))
                .andExpect(jsonPath("$.data.bid").value(313.5))
                .andExpect(jsonPath("$.data.ask").value(313.56))
                .andExpect(jsonPath("$.data.spreadBps").value(1.8587))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.change").value(3.63))
                .andExpect(jsonPath("$.data.changePercent").value(1.1713))
                .andExpect(jsonPath("$.data.previousClose").value(309.9))
                .andExpect(jsonPath("$.data.asOf").value("2026-08-26T16:13:24Z"))
                .andExpect(jsonPath("$.data.marketState").value("open"))
                .andExpect(jsonPath("$.meta.symbol").value("AAPL"))
                .andExpect(jsonPath("$.meta.source").value("cache"))
                .andExpect(jsonPath("$.meta.disclaimer").value("Educational data. Not for investment use."))
                .andExpect(jsonPath("$.meta.spreadSource").value("modelled"))
                .andExpect(jsonPath("$.meta.stale").value(false));
    }

    @Test
    void getQuoteReturnsBadRequestForBlankSymbol() throws Exception {
        when(fauxnanceQuoteService.getQuote("AAPL")).thenThrow(new IllegalArgumentException("Symbol is required."));

        mockMvc.perform(get("/quotes/AAPL").with(jwtWithRoles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Symbol is required."));
    }

    private FauxnanceQuoteResponse buildResponse() {
        FauxnanceQuote quote = new FauxnanceQuote();
        quote.setSymbol("AAPL");
        quote.setPrice(new BigDecimal("313.53"));
        quote.setBid(new BigDecimal("313.50"));
        quote.setAsk(new BigDecimal("313.56"));
        quote.setSpreadBps(new BigDecimal("1.8587"));
        quote.setCurrency("USD");
        quote.setChange(new BigDecimal("3.63"));
        quote.setChangePercent(new BigDecimal("1.1713"));
        quote.setPreviousClose(new BigDecimal("309.9"));
        quote.setAsOf(OffsetDateTime.parse("2026-08-26T16:13:24Z"));
        quote.setMarketState("open");

        FauxnanceQuoteMeta meta = new FauxnanceQuoteMeta();
        meta.setAsOf(OffsetDateTime.parse("2026-08-26T16:13:24Z"));
        meta.setDisclaimer("Educational data. Not for investment use.");
        meta.setSymbol("AAPL");
        meta.setSource("cache");
        meta.setSpreadSource("modelled");
        meta.setStale(false);

        FauxnanceQuoteResponse response = new FauxnanceQuoteResponse();
        response.setData(quote);
        response.setMeta(meta);
        return response;
    }
}


