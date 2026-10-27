package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;

import com.neueda.leap.trading.TradingModels.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TradingRulesTest {
    final Instant now = Instant.parse("2026-10-14T14:00:00Z");

    BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    Policy policy() {
        return policy(30);
    }

    Policy policy(int maxAge) {
        return new Policy(
                1,
                "TEST",
                true,
                "test-v1",
                "TEST-UK",
                "shares",
                "USD",
                "USD",
                n("0.01"),
                2,
                n("10000"),
                n("1000000"),
                maxAge,
                2,
                120,
                3,
                "PROVIDER_OPEN",
                true,
                "USD",
                n("1"),
                "TEST",
                true,
                true,
                null);
    }

    Quote quote(String symbol, Instant at, String bid, String ask) {
        return new Quote(
                UUID.randomUUID(),
                1,
                symbol,
                "USD",
                "USD",
                "USD",
                n(bid),
                n(ask),
                n(bid),
                n(ask),
                at,
                now,
                "SIMULATOR",
                "Synthetic",
                "modelled",
                "open",
                false,
                now);
    }

    Command buy(String q) {
        return new Command(1, "BUY", q, "MARKET");
    }

    @Test
    void thirtyMinuteQuotePolicyStillEnforcesTheTimestampBoundary() {
        assertTrue(
                TradingRules.evaluate(
                                buy("1"),
                                policy(1800),
                                quote("TEST", now.minusSeconds(1800), "49", "50"),
                                now)
                        .eligible());
        assertEquals(
                "QUOTE_STALE",
                TradingRules.evaluate(
                                buy("1"),
                                policy(1800),
                                quote("TEST", now.minusSeconds(1801), "49", "50"),
                                now)
                        .code());
        assertEquals(
                "QUOTE_FUTURE",
                TradingRules.evaluate(
                                buy("1"),
                                policy(1800),
                                quote("TEST", now.plusSeconds(3), "49", "50"),
                                now)
                        .code());
    }

    @Test
    void unknownProviderStatusAllowsSpyOnCoreSessionDespiteUppercaseCatalogVenue() {
        var marketOpenNow = Instant.parse("2026-10-27T13:35:00Z");
        var unknown = quote("SPY", marketOpenNow, "49", "50");
        unknown = new Quote(
                unknown.quoteId(), unknown.instrumentId(), unknown.symbol(), unknown.currency(),
                unknown.rawPriceUnit(), unknown.priceUnit(), unknown.rawBid(), unknown.rawAsk(),
                unknown.bid(), unknown.ask(), unknown.providerTime(), marketOpenNow,
                unknown.source(), unknown.disclaimer(), unknown.spreadOrigin(), "unknown",
                unknown.stale(), marketOpenNow);
        for (String venue : java.util.List.of("NYSE ARCA", "NASDAQ", "NYSE", "US")) {
            var spyPolicy = new Policy(
                    1, "SPY", true, "test-v1", venue, "shares", "USD", "USD",
                    n("0.01"), 2, n("10000"), n("1000000"), 1800, 2, 120, 3,
                    "PROVIDER_OPEN", true, "USD", n("1"), "SPY", true, true, null);
            assertTrue(TradingRules.evaluate(buy("1"), spyPolicy, unknown, marketOpenNow).eligible(), venue);
        }
    }

    @Test
    void unknownProviderStatusUsesFxAndCryptoTradingSessions() {
        var fxPolicy =
                new Policy(
                        1,
                        "FX:EURUSD",
                        true,
                        "test-v1",
                        "OTC",
                        "shares",
                        "USD",
                        "USD",
                        n("0.01"),
                        2,
                        n("10000"),
                        n("1000000"),
                        1800,
                        2,
                        120,
                        3,
                        "PROVIDER_OPEN",
                        true,
                        "USD",
                        n("1"),
                        "FX:EURUSD",
                        true,
                        true,
                        "Foreign Exchange");
        var cryptoPolicy =
                new Policy(
                        1,
                        "X:BTC-USD",
                        true,
                        "test-v1",
                        "OTC",
                        "shares",
                        "USD",
                        "USD",
                        n("0.01"),
                        2,
                        n("10000"),
                        n("1000000"),
                        1800,
                        2,
                        120,
                        3,
                        "PROVIDER_OPEN",
                        true,
                        "USD",
                        n("1"),
                        "X:BTC-USD",
                        true,
                        true,
                        "Cryptocurrency");
        var fxQuote = quote("FX:EURUSD", now, "1.1", "1.2");
        var cryptoQuote = quote("X:BTC-USD", now, "49", "50");
        fxQuote = withMarketState(fxQuote, "unknown");
        cryptoQuote = withMarketState(cryptoQuote, "unknown");

        assertTrue(TradingRules.evaluate(buy("1"), fxPolicy, fxQuote, now).eligible());
        assertTrue(
                TradingRules.evaluate(
                                buy("1"),
                                fxPolicy,
                                withMarketState(fxQuote, "closed"),
                                now)
                        .eligible());
        assertTrue(
                TradingRules.evaluate(
                                buy("1"),
                                fxPolicy,
                                withMarketState(fxQuote, null),
                                now)
                        .eligible());
        assertTrue(
                TradingRules.evaluate(
                                buy("1"),
                                fxPolicy,
                                withMarketState(fxQuote, ""),
                                now)
                        .eligible());
        assertTrue(
                TradingRules.evaluate(
                                buy("1"),
                                fxPolicy,
                                withMetadataTime(
                                        withMarketState(fxQuote, "unknown"),
                                        now.minusSeconds(1801)),
                                now)
                        .eligible());
        assertEquals(
                "QUOTE_METADATA_TIME",
                TradingRules.evaluate(
                                buy("1"),
                                fxPolicy,
                                withMetadataTime(
                                        withMarketState(fxQuote, "unknown"), now.plusSeconds(3)),
                                now)
                        .code());
        assertEquals(
                "SESSION_CLOSED",
                TradingRules.evaluate(
                                buy("1"),
                                fxPolicy,
                                withMarketState(fxQuote, "halted"),
                                now)
                        .code());
        assertTrue(TradingRules.evaluate(buy("1"), cryptoPolicy, cryptoQuote, now).eligible());
    }

    Quote withMarketState(Quote quote, String marketState) {
        return new Quote(
                quote.quoteId(),
                quote.instrumentId(),
                quote.symbol(),
                quote.currency(),
                quote.rawPriceUnit(),
                quote.priceUnit(),
                quote.rawBid(),
                quote.rawAsk(),
                quote.bid(),
                quote.ask(),
                quote.providerTime(),
                quote.receivedAt(),
                quote.source(),
                quote.disclaimer(),
                quote.spreadOrigin(),
                marketState,
                quote.stale(),
                quote.metadataTime());
    }

    Quote withMetadataTime(Quote quote, Instant metadataTime) {
        return new Quote(
                quote.quoteId(),
                quote.instrumentId(),
                quote.symbol(),
                quote.currency(),
                quote.rawPriceUnit(),
                quote.priceUnit(),
                quote.rawBid(),
                quote.rawAsk(),
                quote.bid(),
                quote.ask(),
                quote.providerTime(),
                quote.receivedAt(),
                quote.source(),
                quote.disclaimer(),
                quote.spreadOrigin(),
                quote.marketState(),
                quote.stale(),
                metadataTime);
    }

    @Test
    void unknownSpreadClassificationCannotAuthorizeExecution() {
        var q =
                new Quote(
                        UUID.randomUUID(),
                        1,
                        "TEST",
                        "USD",
                        "USD",
                        "USD",
                        n("49"),
                        n("50"),
                        n("49"),
                        n("50"),
                        now,
                        now,
                        "cache",
                        "Educational",
                        "unknown",
                        "open",
                        false,
                        now);
        assertEquals("QUOTE_METADATA", TradingRules.evaluate(buy("1"), policy(), q, now).code());
    }

    @Test
    void exactSidePriceAndCurrencyRounding() {
        var buy =
                TradingRules.evaluate(
                        buy("0.13"), policy(), quote("TEST", now, "49.99", "50"), now);
        assertEquals(n("6.50"), buy.gross());
        assertEquals(n("50"), buy.price());
        var sell =
                TradingRules.evaluate(
                        new Command(1, "SELL", "0.13", "MARKET"),
                        policy(),
                        quote("TEST", now, "49.99", "50"),
                        now);
        assertEquals(n("49.99"), sell.price());
        assertEquals(n("6.50"), sell.gross());
    }

    @Test
    void missingQuoteSymbolFailsClosedInsteadOfThrowing() {
        assertEquals(
                "QUOTE_MAPPING",
                TradingRules.evaluate(buy("1"), policy(), quote(null, now, "49", "50"), now)
                        .code());
    }

    @Test
    void staleFutureCrossedMissingAndPrecisionAreRejected() {
        assertEquals(
                "QUOTE_STALE",
                TradingRules.evaluate(
                                buy("1"),
                                policy(),
                                quote("TEST", now.minusSeconds(31), "49", "50"),
                                now)
                        .code());
        assertEquals(
                "QUOTE_FUTURE",
                TradingRules.evaluate(
                                buy("1"),
                                policy(),
                                quote("TEST", now.plusSeconds(3), "49", "50"),
                                now)
                        .code());
        assertEquals(
                "QUOTE_INVALID",
                TradingRules.evaluate(buy("1"), policy(), quote("TEST", now, "51", "50"), now)
                        .code());
        assertEquals(
                "QUOTE_PRECISION",
                TradingRules.evaluate(
                                buy("1"),
                                policy(),
                                quote("TEST", now, "49", "50.0000000000001"),
                                now)
                        .code());
        assertEquals("QUOTE_MISSING", TradingRules.evaluate(buy("1"), policy(), null, now).code());
        assertEquals(
                "QUANTITY_STEP",
                TradingRules.evaluate(buy("0.001"), policy(), quote("TEST", now, "49", "50"), now)
                        .code());
        assertThrows(IllegalArgumentException.class, () -> TradingRules.validate(buy("1e3")));
        assertThrows(
                IllegalArgumentException.class,
                () -> TradingRules.validate(buy("0.0000000000001")));
        assertThrows(
                IllegalArgumentException.class,
                () -> TradingRules.validate(buy("99999999999999999")));
    }
}
