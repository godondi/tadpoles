package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.neueda.leap.domain.FauxnanceQuote;
import com.neueda.leap.domain.FauxnanceQuoteMeta;
import com.neueda.leap.domain.FauxnanceQuoteResponse;
import com.neueda.leap.mapper.TradingMapper;
import com.neueda.leap.mapper.TradingMapper.ExternalCashFlow;
import com.neueda.leap.service.FauxnanceQuoteService;
import com.neueda.leap.trading.TradingModels.Policy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PortfolioValuationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-16T20:00:00Z");
    private final TradingMapper mapper = mock(TradingMapper.class);
    private final FauxnanceQuoteService provider = mock(FauxnanceQuoteService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private PortfolioValuationService service;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service =
                new PortfolioValuationService(
                        mapper, provider, Clock.fixed(NOW, ZoneOffset.UTC), transactions);
        when(mapper.balances(7))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "currency",
                                        "USD",
                                        "posted",
                                        "1000.01",
                                        "reserved",
                                        "200",
                                        "available",
                                        "800.01")));
        when(mapper.positions(7)).thenReturn(List.of(position(1, "AAPL", "0.5")));
        when(mapper.externalCashFlows(7)).thenReturn(List.of());
        when(mapper.policy(1)).thenReturn(policy(1, "AAPL"));
        when(provider.getQuote("AAPL")).thenReturn(quote("AAPL", "100.125"));
    }

    @Test
    void allTimeReturnWeightsDepositsAndWithdrawalsByTime() {
        when(mapper.balances(7)).thenReturn(List.of(Map.of("posted", "15000")));
        when(mapper.positions(7)).thenReturn(List.of());
        when(mapper.externalCashFlows(7))
                .thenReturn(
                        List.of(
                                new ExternalCashFlow(
                                        NOW.minus(Duration.ofDays(30)),
                                        new BigDecimal("10000")),
                                new ExternalCashFlow(NOW, new BigDecimal("-5000"))));

        assertEquals("100.00", service.value(7).allTimeReturnPercent());

        when(mapper.balances(7)).thenReturn(List.of(Map.of("posted", "5000")));
        assertEquals("0.00", service.value(7).allTimeReturnPercent());
    }

    @Test
    void addsOwnedHoldingsToPostedCashWithoutDeductingReservations() {
        var value = service.value(7);

        assertEquals("COMPLETE", value.status());
        assertEquals("1050.07", value.totalValue());
        assertEquals("50.06", value.holdingsValue());
        assertEquals("1000.01", value.cashBalance());
        assertEquals(NOW, value.snapshotAt());
        assertEquals(NOW.minusSeconds(10), value.quoteAsOf());
        assertTrue(value.unpricedTickers().isEmpty());
        var order = inOrder(transactions, provider);
        order.verify(transactions).getTransaction(any());
        order.verify(transactions).commit(any());
        order.verify(provider).getQuote("AAPL");
    }

    @Test
    void sumsExactProductsBeforeRoundingAndPreservesLargeCashAmounts() {
        when(mapper.balances(7)).thenReturn(List.of(Map.of("posted", "9007199254740993.01")));
        when(mapper.positions(7))
                .thenReturn(List.of(position(1, "AAPL", "0.001"), position(2, "MSFT", "0.001")));
        when(mapper.policy(2)).thenReturn(policy(2, "MSFT"));
        when(provider.getQuote("AAPL")).thenReturn(quote("AAPL", "4"));
        when(provider.getQuote("MSFT")).thenReturn(quote("MSFT", "4"));

        var value = service.value(7);

        assertEquals("0.01", value.holdingsValue());
        assertEquals("9007199254740993.02", value.totalValue());
    }

    @Test
    void emptyPortfolioUsesCashWithoutCallingTheProvider() {
        when(mapper.positions(7)).thenReturn(List.of());

        var value = service.value(7);

        assertEquals("1000.01", value.totalValue());
        assertEquals("0.00", value.holdingsValue());
        assertNull(value.quoteAsOf());
        verifyNoInteractions(provider);
    }

    @Test
    void missingCashIsNotInventedAsZero() {
        when(mapper.balances(7)).thenReturn(List.of());
        var value = service.value(7);
        assertEquals("UNAVAILABLE", value.status());
        assertNull(value.totalValue());
        assertNull(value.cashBalance());
    }

    @Test
    void outageOrMissingMappingDoesNotReturnAPartialTotal() {
        when(mapper.positions(7))
                .thenReturn(List.of(position(1, "AAPL", "2"), position(2, "MSFT", "3")));
        when(mapper.policy(2)).thenReturn(policy(2, "MSFT"));
        when(provider.getQuote("MSFT"))
                .thenThrow(new IllegalStateException("provider unavailable"));

        var value = service.value(7);

        assertEquals("UNAVAILABLE", value.status());
        assertNull(value.totalValue());
        assertNull(value.holdingsValue());
        assertNull(value.allTimeReturnPercent());
        assertEquals("1000.01", value.cashBalance());
        assertEquals(List.of("MSFT"), value.unpricedTickers());
        when(mapper.policy(1)).thenReturn(null);
        assertEquals(List.of("AAPL", "MSFT"), service.value(7).unpricedTickers());
    }

    @Test
    void tradingHaltsAndClosedMarketsDoNotEraseOwnedValue() {
        var quote = quote("AAPL", "100");
        quote.getData().setMarketState("closed");
        when(provider.getQuote("AAPL")).thenReturn(quote);
        // The fixture policy is deliberately inactive and has both trading sides disabled.
        assertEquals("1050.01", service.value(7).totalValue());
    }

    @Test
    void acceptsTheAgeBoundaryAndReportsTheOldestQuoteTime() {
        var quote = quote("AAPL", "100");
        quote.getData().setAsOf(OffsetDateTime.ofInstant(NOW.minusSeconds(1200), ZoneOffset.UTC));
        quote.getMeta().setAsOf(quote.getData().getAsOf());
        when(provider.getQuote("AAPL")).thenReturn(quote);
        assertEquals("COMPLETE", service.value(7).status());
        assertEquals(NOW.minusSeconds(1200), service.value(7).quoteAsOf());
    }

    @ParameterizedTest
    @MethodSource("unusableQuotes")
    void refusesMissingStaleOrMismatchedPrices(Consumer<FauxnanceQuoteResponse> corrupt) {
        var quote = quote("AAPL", "100");
        corrupt.accept(quote);
        when(provider.getQuote("AAPL")).thenReturn(quote);

        var value = service.value(7);

        assertEquals("UNAVAILABLE", value.status());
        assertNull(value.totalValue());
        assertNull(value.holdingsValue());
        assertEquals(List.of("AAPL"), value.unpricedTickers());
    }

    static Stream<Consumer<FauxnanceQuoteResponse>> unusableQuotes() {
        return Stream.of(
                q -> q.setData(null),
                q -> q.setMeta(null),
                q -> q.getData().setPrice(null),
                q -> q.getData().setPrice(BigDecimal.ZERO),
                q -> q.getData().setPrice(new BigDecimal("-1")),
                q -> q.getData().setSymbol("MSFT"),
                q -> q.getMeta().setSymbol("MSFT"),
                q -> q.getData().setCurrency("GBP"),
                q -> q.getMeta().setSource(" "),
                q -> q.getMeta().setStale(true),
                q -> q.getMeta().setStale(null),
                q -> q.getData().setAsOf(null),
                q -> q.getMeta().setAsOf(null),
                q ->
                        q.getData()
                                .setAsOf(
                                        OffsetDateTime.ofInstant(
                                                NOW.minusSeconds(1201), ZoneOffset.UTC)),
                q ->
                        q.getMeta()
                                .setAsOf(
                                        OffsetDateTime.ofInstant(
                                                NOW.minusSeconds(1201), ZoneOffset.UTC)),
                q ->
                        q.getData()
                                .setAsOf(
                                        OffsetDateTime.ofInstant(
                                                NOW.plusSeconds(3), ZoneOffset.UTC)),
                q ->
                        q.getMeta()
                                .setAsOf(
                                        OffsetDateTime.ofInstant(
                                                NOW.plusSeconds(3), ZoneOffset.UTC)));
    }

    private Map<String, Object> position(int id, String ticker, String owned) {
        return Map.of(
                "instrumentId",
                id,
                "ticker",
                ticker,
                "currency",
                "USD",
                "owned",
                owned,
                "reserved",
                owned,
                "available",
                "0");
    }

    private Policy policy(int id, String ticker) {
        return new Policy(
                id,
                ticker,
                false,
                "test-v1",
                "NASDAQ",
                "shares",
                "USD",
                "USD",
                BigDecimal.ONE,
                2,
                new BigDecimal("10000"),
                new BigDecimal("1000000"),
                1200,
                2,
                120,
                3,
                "PROVIDER_OPEN",
                false,
                "USD",
                BigDecimal.ONE,
                ticker,
                false,
                false,
                null);
    }

    private FauxnanceQuoteResponse quote(String symbol, String price) {
        var data = new FauxnanceQuote();
        data.setSymbol(symbol);
        data.setCurrency("USD");
        data.setPrice(new BigDecimal(price));
        data.setAsOf(OffsetDateTime.ofInstant(NOW.minusSeconds(10), ZoneOffset.UTC));
        var meta = new FauxnanceQuoteMeta();
        meta.setSymbol(symbol);
        meta.setSource("Test provider");
        meta.setStale(false);
        meta.setAsOf(data.getAsOf());
        var response = new FauxnanceQuoteResponse();
        response.setData(data);
        response.setMeta(meta);
        return response;
    }
}
