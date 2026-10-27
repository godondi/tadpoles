package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.domain.FauxnanceDailyCandleResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

class FauxnanceDailyCandleServiceImplTest {
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private FauxnanceDailyCandleServiceImpl service;

    @Test
    void missingSyntheticProvenanceRemainsUnknown() throws Exception {
        var candle =
                new ObjectMapper()
                        .readValue("{}", com.neueda.leap.domain.FauxnanceDailyCandle.class);
        org.junit.jupiter.api.Assertions.assertNull(candle.isSynthetic());
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service =
                new FauxnanceDailyCandleServiceImpl(
                        restTemplate,
                        new ObjectMapper().findAndRegisterModules(),
                        "http://localhost:9090/v1",
                        "fnx_dev_test_key",
                        "1d");
    }

    @Test
    void getCandlesReturnsMappedResponseAndForwardsParameters() {
        server.expect(
                        requestTo(
                                "http://localhost:9090/v1/candles/AAPL?from=2026-01-01&to=2026-01-03&interval=1d"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "fnx_dev_test_key"))
                .andRespond(
                        withStatus(HttpStatus.OK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
{
  "data": {
    "symbol": "AAPL",
    "interval": "1d",
    "currency": "USD",
    "candles": [
      {
        "date": "2026-01-02",
        "open": 189.12,
        "high": 192.48,
        "low": 188.77,
        "close": 191.81,
        "adjclose": 191.81,
        "volume": 42000000,
        "synthetic": false
      }
    ]
  },
  "meta": {
    "asOf": "2026-01-03T00:00:00Z",
    "disclaimer": "Educational data. Not for investment use.",
    "symbol": "AAPL",
    "source": "stored",
    "stale": false,
    "partial": false
  }
}
"""));

        FauxnanceDailyCandleResponse response =
                service.getCandles(
                        "AAPL", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-03"), "1d");

        assertEquals("AAPL", response.getData().getSymbol());
        assertEquals("USD", response.getData().getCurrency());
        assertEquals(1, response.getData().getCandles().size());
        assertFalse(response.getData().getCandles().get(0).isSynthetic());
        server.verify();
    }

    @Test
    void getCandlesUsesDefaultIntervalWhenMissing() {
        server.expect(requestTo("http://localhost:9090/v1/candles/AAPL?interval=1d"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Api-Key", "fnx_dev_test_key"))
                .andRespond(
                        withStatus(HttpStatus.OK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
{
  "data": {
    "symbol": "AAPL",
    "interval": "1d",
    "currency": "USD",
    "candles": []
  },
  "meta": {
    "asOf": "2026-01-03T00:00:00Z",
    "disclaimer": "Educational data. Not for investment use.",
    "symbol": "AAPL",
    "source": "stored"
  }
}
"""));

        FauxnanceDailyCandleResponse response = service.getCandles("AAPL", null, null, null);

        assertEquals("1d", response.getData().getInterval());
        server.verify();
    }

    @Test
    void getCandlesRejectsInvertedDateRange() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                service.getCandles(
                                        "AAPL",
                                        LocalDate.parse("2026-08-27"),
                                        LocalDate.parse("2026-08-26"),
                                        "1d"));

        assertEquals("'from' must be on or before 'to'.", exception.getMessage());
    }

    @Test
    void getCandlesMapsNotFoundFromUpstream() {
        server.expect(requestTo("http://localhost:9090/v1/candles/BAD?interval=1d"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withStatus(HttpStatus.NOT_FOUND)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        """
                                        {
                                          "error": {
                                            "code": "SYMBOL_NOT_FOUND",
                                            "message": "Symbol was not recognized.",
                                            "details": {}
                                          }
                                        }
                                        """));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getCandles("BAD", null, null, null));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Symbol was not recognized.", exception.getReason());
        server.verify();
    }

    @Test
    void providerServerFailureIsReportedAsBadGateway() {
        server.expect(requestTo("http://localhost:9090/v1/candles/SPY?interval=1d"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        var error = assertThrows(ResponseStatusException.class, () -> service.getCandles("SPY", null, null, null));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertEquals("Market data provider is temporarily unavailable. Please retry shortly.", error.getReason());
        server.verify();
    }

    @Test
    void providerTimeoutIsReportedAsServiceUnavailable() {
        server.expect(requestTo("http://localhost:9090/v1/candles/SPY?interval=1d"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withException(
                        new java.net.SocketTimeoutException("upstream timeout")));
        var error = assertThrows(ResponseStatusException.class, () -> service.getCandles("SPY", null, null, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertEquals("Market data provider could not be reached. Please retry shortly.", error.getReason());
        server.verify();
    }
}
