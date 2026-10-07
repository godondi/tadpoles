package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

class FauxnanceQuoteServiceImplTest {
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private FauxnanceQuoteServiceImpl service;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new FauxnanceQuoteServiceImpl(
                restTemplate,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:9090/v1",
                "fnx_dev_test_key"
        );
    }

    @Test
    void getQuoteReturnsMappedResponseAndForwardsSymbol() {
        server.expect(requestTo("http://localhost:9090/v1/quotes/AAPL"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "fnx_dev_test_key"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "data": {
                                    "symbol": "AAPL",
                                    "price": 313.53,
                                    "bid": 313.5,
                                    "ask": 313.56,
                                    "spreadBps": 1.8587,
                                    "currency": "USD",
                                    "change": 3.63,
                                    "changePercent": 1.1713,
                                    "previousClose": 309.9,
                                    "asOf": "2026-08-26T16:13:24Z",
                                    "marketState": "open"
                                  },
                                  "meta": {
                                    "asOf": "2026-08-26T16:13:24Z",
                                    "disclaimer": "Educational data. Not for investment use.",
                                    "symbol": "AAPL",
                                    "source": "cache",
                                    "spreadSource": "modelled",
                                    "stale": false
                                  }
                                }
                                """));

        FauxnanceQuoteResponse response = service.getQuote("AAPL");

        assertEquals("AAPL", response.getData().getSymbol());
        assertEquals("USD", response.getData().getCurrency());
        assertEquals("cache", response.getMeta().getSource());
        assertEquals("modelled", response.getMeta().getSpreadSource());
        assertEquals(false, response.getMeta().getStale());
        assertEquals(OffsetDateTime.parse("2026-08-26T16:13:24Z"), response.getData().getAsOf());
        server.verify();
    }

    @Test
    void getQuoteNormalizesLowerCaseSymbols() {
        server.expect(requestTo("http://localhost:9090/v1/quotes/TSLA"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "fnx_dev_test_key"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "data": {
                                    "symbol": "TSLA",
                                    "price": 10,
                                    "bid": 9.9,
                                    "ask": 10.1,
                                    "spreadBps": 2,
                                    "currency": "USD",
                                    "change": 0.1,
                                    "changePercent": 1,
                                    "previousClose": 9.9,
                                    "asOf": "2026-08-26T16:13:24Z",
                                    "marketState": "open"
                                  },
                                  "meta": {
                                    "asOf": "2026-08-26T16:13:24Z",
                                    "disclaimer": "Educational data. Not for investment use.",
                                    "symbol": "TSLA",
                                    "source": "cache",
                                    "spreadSource": "modelled",
                                    "stale": false
                                  }
                                }
                                """));

        FauxnanceQuoteResponse response = service.getQuote("tsla");

        assertEquals("TSLA", response.getData().getSymbol());
        server.verify();
    }

    @Test
    void getQuoteMapsNotFoundFromUpstream() {
        server.expect(requestTo("http://localhost:9090/v1/quotes/BAD"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "code": "SYMBOL_NOT_FOUND",
                                    "message": "Symbol was not recognized.",
                                    "details": {}
                                  }
                                }
                                """));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getQuote("BAD")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Symbol was not recognized.", exception.getReason());
        server.verify();
    }
}

