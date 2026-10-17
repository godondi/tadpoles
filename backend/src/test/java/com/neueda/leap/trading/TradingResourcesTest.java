package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.mapper.TradingMapper;
import com.neueda.leap.trading.TradingModels.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class TradingResourcesTest {
    @Test
    void incompleteQuoteTimestampReturnsAnIneligiblePreviewInsteadOfCrashing() throws Exception {
        var db = mock(TradingMapper.class);
        var tx = mock(TradingTransactions.class);
        var provider = mock(com.neueda.leap.service.FauxnanceQuoteService.class);
        var fixture = new FauxnanceTradingQuotesTest();
        var response = fixture.response();
        response.getData().setAsOf(null);
        when(provider.getQuote("BRK-B")).thenReturn(response);
        when(db.policy(1)).thenReturn(fixture.policy());
        when(tx.resources(anyInt(), any(), any(), any())).thenAnswer(i -> i.getArgument(3));
        var clock = Clock.systemUTC();
        var service =
                new com.neueda.leap.service.impl.TradingServiceImpl(
                        db,
                        tx,
                        new FauxnanceTradingQuotes(provider, clock),
                        new ObjectMapper(),
                        clock,
                        true);
        var result = service.preview(1, new Command(1, "BUY", "1", "MARKET"));
        assertFalse(result.eligible());
        assertEquals("QUOTE_MISSING", result.code());
        assertNull(result.quoteTime());
        assertNull(result.expiresAt());
    }

    @Test
    void bothSidesRequireAnOwnedActiveBankEvenWhenFundsAndUnitsExist() {
        var db = mock(TradingMapper.class);
        when(db.onboarded(1)).thenReturn(true);
        when(db.accountEnabled(1)).thenReturn(true);
        when(db.cash(1, "USD"))
                .thenReturn(Map.of("posted", 10000, "reserved", 0, "available_for_trading", true));
        when(db.position(1, 1))
                .thenReturn(Map.of("posted", 10, "reserved", 0, "available_for_trading", true));
        var tx =
                new TradingTransactions(
                        db,
                        new ObjectMapper(),
                        Clock.systemUTC(),
                        new DefaultListableBeanFactory().getBeanProvider(BookingProbe.class));
        var p = new FauxnanceTradingQuotesTest().policy();
        for (String side : new String[] {"BUY", "SELL"})
            assertEquals(
                    "BANK_REQUIRED",
                    tx.resources(
                                    1,
                                    new Command(1, side, "1", "MARKET"),
                                    p,
                                    new Evaluation(
                                            true, "ELIGIBLE", "", BigDecimal.ONE, BigDecimal.ONE))
                            .code());
    }
}
