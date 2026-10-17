package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.service.FauxnanceQuoteService;
import com.neueda.leap.trading.TradingModels.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

class FauxnanceTradingQuotesTest {
    final Instant now = Instant.parse("2026-10-14T14:00:00Z");
    final FauxnanceQuoteService provider = mock(FauxnanceQuoteService.class);
    final FauxnanceTradingQuotes adapter =
            new FauxnanceTradingQuotes(provider, Clock.fixed(now, ZoneOffset.UTC));

    Policy policy() {
        return new Policy(
                1,
                "BRK.B",
                true,
                "v1",
                "NYSE",
                "shares",
                "USD",
                "USD",
                new BigDecimal("0.01"),
                2,
                new BigDecimal("10000"),
                new BigDecimal("1000000"),
                30,
                2,
                120,
                3,
                "PROVIDER_OPEN",
                true,
                "USD",
                BigDecimal.ONE,
                "BRK-B",
                true,
                true);
    }

    FauxnanceQuoteResponse response() throws Exception {
        return new ObjectMapper()
                .findAndRegisterModules()
                .readValue(
                        """
{"data":{"symbol":"BRK-B","currency":"USD","bid":49.99,"ask":50.01,"asOf":"2026-10-14T13:59:59Z","marketState":"open"},
 "meta":{"symbol":"BRK-B","asOf":"2026-10-14T14:00:00Z","source":"cache","spreadSource":"modelled","stale":false,"disclaimer":"Educational data"}}
""",
                        FauxnanceQuoteResponse.class);
    }

    @Test
    void exactProviderMappingRetainsObservationTimeAndProvenance() throws Exception {
        when(provider.getQuote("BRK-B")).thenReturn(response());
        var q = adapter.quote(policy());
        assertNotNull(q);
        assertEquals(new BigDecimal("49.99"), q.bid());
        assertEquals("BRK-B", q.symbol());
        assertEquals("cache", q.source());
        assertEquals(now.minusSeconds(1), q.providerTime());
        assertEquals(now, q.receivedAt());
        assertEquals("modelled", q.spreadOrigin());
        assertTrue(
                TradingRules.evaluate(new Command(1, "BUY", "1", "MARKET"), policy(), q, now)
                        .eligible());
    }

    @Test
    void unknownStaleAndClosedSessionsFailClosed() throws Exception {
        var r = response();
        when(provider.getQuote("BRK-B")).thenReturn(r);
        r.getMeta().setStale(null);
        assertFalse(
                TradingRules.evaluate(
                                new Command(1, "BUY", "1", "MARKET"),
                                policy(),
                                adapter.quote(policy()),
                                now)
                        .eligible());
        r.getMeta().setStale(false);
        r.getData().setMarketState("closed");
        assertEquals(
                "SESSION_CLOSED",
                TradingRules.evaluate(
                                new Command(1, "BUY", "1", "MARKET"),
                                policy(),
                                adapter.quote(policy()),
                                now)
                        .code());
    }

    @Test
    void unknownProviderStateUsesTheScheduledSessionWithoutChangingTheObservation()
            throws Exception {
        var r = response();
        r.getData().setMarketState("unknown");
        when(provider.getQuote("BRK-B")).thenReturn(r);
        var q = adapter.quote(policy());
        assertEquals("unknown", q.marketState());
        var result = TradingRules.evaluate(new Command(1, "BUY", "1", "MARKET"), policy(), q, now);
        assertTrue(result.eligible());
        assertTrue(result.reason().contains("scheduled core session"));
        r.getMeta().setStale(true);
        assertEquals(
                "QUOTE_STALE",
                TradingRules.evaluate(
                                new Command(1, "BUY", "1", "MARKET"),
                                policy(),
                                adapter.quote(policy()),
                                now)
                        .code());
        r.getMeta().setStale(false);
        r.getData().setAsOf(now.minusSeconds(31).atOffset(ZoneOffset.UTC));
        assertEquals(
                "QUOTE_STALE",
                TradingRules.evaluate(
                                new Command(1, "BUY", "1", "MARKET"),
                                policy(),
                                adapter.quote(policy()),
                                now)
                        .code());
    }

    @Test
    void contradictoryMetadataAndMissingProviderTimeAreNeverInvented() throws Exception {
        var r = response();
        when(provider.getQuote("BRK-B")).thenReturn(r);
        r.getMeta().setSymbol("AAPL");
        assertThrows(IllegalArgumentException.class, () -> adapter.quote(policy()));
        r.getMeta().setSymbol("BRK-B");
        r.getData().setAsOf(null);
        assertNull(adapter.quote(policy()).providerTime());
    }

    @Test
    void staleProviderFlagHasAnActionableReasonEvenWhenTimestampIsRecent() throws Exception {
        var r = response();
        when(provider.getQuote("BRK-B")).thenReturn(r);
        r.getMeta().setStale(true);
        var result =
                TradingRules.evaluate(
                        new Command(1, "BUY", "1", "MARKET"),
                        policy(),
                        adapter.quote(policy()),
                        now);
        assertEquals("QUOTE_STALE", result.code());
        assertTrue(result.reason().contains("marked this quote as stale"));
        r.getMeta().setStale(null);
        result =
                TradingRules.evaluate(
                        new Command(1, "BUY", "1", "MARKET"),
                        policy(),
                        adapter.quote(policy()),
                        now);
        assertEquals("QUOTE_METADATA", result.code());
        assertTrue(result.reason().contains("did not report quote freshness"));
        r.getMeta().setStale(false);
        r.getMeta().setSpreadSource(null);
        result =
                TradingRules.evaluate(
                        new Command(1, "BUY", "1", "MARKET"),
                        policy(),
                        adapter.quote(policy()),
                        now);
        assertEquals("QUOTE_METADATA", result.code());
        assertTrue(result.reason().contains("spread source"));
    }

    @Test
    void providerOutageNeverBecomesFixturePrice() {
        when(provider.getQuote("BRK-B")).thenThrow(new IllegalStateException("offline"));
        assertThrows(IllegalStateException.class, () -> adapter.quote(policy()));
    }
}
