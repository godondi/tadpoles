package com.neueda.leap.trading;

import static com.neueda.leap.trading.TradingData.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.mapper.*;
import com.neueda.leap.service.FauxnanceQuoteService;
import com.neueda.leap.trading.TradingModels.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Validates exact manifest symbols; never guesses provider suffixes or replaces an Operations
 * policy.
 */
@Service
public class CatalogSetup {
    private final TradingSetupMapper setup;
    private final InstrumentPolicyMapper db;
    private final FauxnanceQuoteService provider;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public CatalogSetup(
            TradingSetupMapper setup,
            InstrumentPolicyMapper db,
            FauxnanceQuoteService provider,
            Clock clock,
            ObjectMapper json,
            PlatformTransactionManager manager) {
        this.setup = setup;
        this.db = db;
        this.provider = provider;
        this.clock = clock;
        this.json = json;
        this.tx = new TransactionTemplate(manager);
    }

    public void initialize() {
        for (var item :
                List.of(
                        new String[] {"AAPL", "Apple Inc.", "NASDAQ"},
                        new String[] {"MSFT", "Microsoft Corporation", "NASDAQ"},
                        new String[] {"AMZN", "Amazon.com Inc.", "NASDAQ"},
                        new String[] {"GOOGL", "Alphabet Inc.", "NASDAQ"},
                        new String[] {"TSLA", "Tesla Inc.", "NASDAQ"},
                        new String[] {"VOO", "Vanguard S&P 500 ETF", "NYSE Arca"},
                        new String[] {"AGG", "iShares Core U.S. Aggregate Bond ETF", "NYSE Arca"},
                        new String[] {"JNJ", "Johnson & Johnson", "NYSE"},
                        new String[] {"PG", "Procter & Gamble", "NYSE"},
                        new String[] {"NVDA", "NVIDIA Corporation", "NASDAQ"},
                        new String[] {"JPM", "JPMorgan Chase & Co.", "NYSE"},
                        new String[] {"UNH", "UnitedHealth Group", "NYSE"},
                        new String[] {"XOM", "Exxon Mobil", "NYSE"},
                        new String[] {"BRK.B", "Berkshire Hathaway Class B", "NYSE"})) {
            Integer existing = setup.instrument(item[0]);
            if (existing != null && setup.version(existing) != null) continue;
            var p =
                    new Policy(
                            existing == null ? 1 : existing,
                            item[0],
                            true,
                            "startup-provider-v1",
                            item[2],
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
                            true,
                            "USD",
                            BigDecimal.ONE,
                            item[0],
                            true,
                            true);
            Quote q;
            try {
                q = new FauxnanceTradingQuotes(provider, clock).quote(p);
            } catch (RuntimeException e) {
                record(
                        item[0],
                        "PROVIDER_UNAVAILABLE",
                        "Provider check failed; no policy enabled. Operations can test again.");
                continue;
            }
            if (q == null
                    || !"USD".equals(q.currency())
                    || q.bid() == null
                    || q.ask() == null
                    || q.bid().signum() <= 0
                    || q.ask().compareTo(q.bid()) < 0
                    || q.providerTime() == null
                    || q.source() == null
                    || q.spreadOrigin() == null) {
                record(
                        item[0],
                        "UNCONFIRMED",
                        "Identity, USD currency or price metadata could not be confirmed; no policy"
                                + " enabled.");
                continue;
            }
            record(
                    item[0],
                    "RECOGNIZED",
                    "USD identity recognized. Per-order freshness/session validation is still"
                            + " required.");
            // Recognition is distinct from current executability. Freshness/open-session checks
            // still run on every order.
            tx.executeWithoutResult(
                    status -> {
                        setup.lockCatalog();
                        Integer id = setup.instrument(item[0]);
                        if (id != null && setup.version(id) != null) return;
                        var a =
                                map(
                                        "assetClass",
                                        item[0].equals("AGG")
                                                ? "Fixed Income"
                                                : item[0].equals("VOO") ? "ETF" : "EQUITY",
                                        "securityType",
                                        Set.of("VOO", "AGG").contains(item[0]) ? "ETF" : "STOCK",
                                        "name",
                                        item[1],
                                        "ticker",
                                        item[0],
                                        "active",
                                        true,
                                        "venue",
                                        item[2],
                                        "providerSymbol",
                                        item[0],
                                        "step",
                                        BigDecimal.ONE,
                                        "maxQuantity",
                                        p.maxQuantity(),
                                        "maxNotional",
                                        p.maxNotional(),
                                        "age",
                                        p.quoteMaxAgeSeconds(),
                                        "buyAllowed",
                                        true,
                                        "sellAllowed",
                                        true,
                                        "revision",
                                        1,
                                        "policyVersion",
                                        "startup-provider-v1",
                                        "session",
                                        "PROVIDER_OPEN",
                                        "validated",
                                        clock.instant());
                        if (id == null) id = db.create(a);
                        a.put("id", id);
                        db.policy(a);
                        try {
                            setup.audit(
                                    map(
                                            "audit",
                                            UUID.randomUUID(),
                                            "id",
                                            id,
                                            "after",
                                            json.writeValueAsString(db.get(id)),
                                            "now",
                                            clock.instant()));
                        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                            throw new IllegalStateException(e);
                        }
                    });
        }
    }

    private void record(String symbol, String result, String detail) {
        setup.providerCheck(
                map(
                        "id",
                        UUID.randomUUID(),
                        "symbol",
                        symbol,
                        "now",
                        clock.instant(),
                        "result",
                        result,
                        "detail",
                        detail));
    }
}
