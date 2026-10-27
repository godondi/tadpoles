package com.neueda.leap.service.impl;

import static com.neueda.leap.trading.TradingData.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.InstrumentPolicyRequest;
import com.neueda.leap.mapper.InstrumentPolicyMapper;
import com.neueda.leap.service.*;
import com.neueda.leap.trading.*;
import com.neueda.leap.trading.TradingModels.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@PreAuthorize("hasRole('TRADING_OPS') and !hasRole('CLIENT')")
public class InstrumentPolicyServiceImpl implements InstrumentPolicyService {
    private final InstrumentPolicyMapper db;
    private final FauxnanceQuoteService provider;
    private final FauxnanceDailyCandleService candles;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public InstrumentPolicyServiceImpl(
            InstrumentPolicyMapper db,
            FauxnanceQuoteService provider,
            FauxnanceDailyCandleService candles,
            ObjectMapper json,
            Clock clock,
            PlatformTransactionManager manager) {
        this.db = db;
        this.provider = provider;
        this.candles = candles;
        this.json = json;
        this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
    }

    public List<Map<String, Object>> list(String search, int page) {
        if (search == null || search.length() > 80 || page < 0 || page > 10000)
            throw new IllegalArgumentException("Invalid search");
        return db.list(search, page * 100);
    }

    public Map<String, Object> get(int id) {
        Map<String, Object> instrument = db.get(id);
        if (instrument == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found");
        }
        return instrument;
    }

    private String symbol(String value) {
        String s = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!s.matches("[A-Z0-9][A-Z0-9.=-]{0,29}")
                || s.startsWith("SIM-")
                || Set.of("CASH", "USD", "GBP", "INR", "EUR").contains(s))
            throw new IllegalArgumentException("Enter a supported provider ticker");
        return s;
    }

    private Quote fetch(Policy p) {
        try {
            return new FauxnanceTradingQuotes(provider, clock).quote(p);
        } catch (ResponseStatusException e) {
            int status = e.getStatusCode().value();
            String reason =
                    status == 429
                            ? "Provider rate limit; try again later"
                            : status == 401 || status == 403
                                    ? "Provider credentials need operator attention"
                                    : status == 404
                                            ? "Provider did not recognize this symbol"
                                            : "Provider is unavailable; no changes saved";
            throw new ResponseStatusException(
                    status == 404 ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE, reason);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Provider symbol is invalid or ambiguous; no changes saved");
        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Provider is unavailable; no changes saved");
        }
    }

    private Policy policy(int id, InstrumentPolicyRequest r, String ticker, String providerSymbol) {
        return new Policy(
                id,
                ticker,
                r.active(),
                "provider-v" + (r.revision() + 1),
                r.venue(),
                "shares",
                "USD",
                "USD",
                decimal(r.quantityStep()),
                2,
                decimal(r.maxQuantity()),
                decimal(r.maxNotional()),
                r.quoteMaxAgeSeconds(),
                2,
                120,
                3,
                "PROVIDER_OPEN",
                true,
                "USD",
                BigDecimal.ONE,
                providerSymbol,
                r.buyAllowed(),
                r.sellAllowed());
    }

    public Map<String, Object> test(String value) {
        String s = symbol(value);
        var r =
                new InstrumentPolicyRequest(
                        s,
                        s,
                        s,
                        "Provider",
                        "1",
                        "10000",
                        "1000000",
                        30,
                        true,
                        true,
                        true,
                        "Test",
                        0);
        var p = policy(1, r, s, s);
        Quote q = fetch(p);
        if (q == null)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Provider returned incomplete symbol data");
        var evaluation =
                TradingRules.evaluate(new Command(1, "BUY", "1", "MARKET"), p, q, clock.instant());
        boolean chart = false;
        String chartReason = "Daily data unavailable";
        try {
            LocalDate to = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
            var c = candles.getCandles(s, to.minusDays(30), to, "1d");
            chart =
                    c != null
                            && c.getData() != null
                            && s.equals(c.getData().getSymbol())
                            && "1d".equals(c.getData().getInterval())
                            && c.getData().getCandles() != null
                            && !c.getData().getCandles().isEmpty();
            chartReason =
                    chart
                            ? "Daily bars returned; chart performs row validation"
                            : "No daily bars returned";
        } catch (RuntimeException e) {
            chartReason = "Daily data could not be checked; quote recognition is independent";
        }
        return map(
                "recognized",
                true,
                "symbol",
                q.symbol(),
                "currency",
                q.currency(),
                "source",
                q.source(),
                "asOf",
                q.providerTime(),
                "spreadSource",
                q.spreadOrigin(),
                "disclaimer",
                q.disclaimer(),
                "quoteUsable",
                evaluation.eligible(),
                "eligible",
                evaluation.eligible(),
                "reason",
                evaluation.reason(),
                "chartAvailable",
                chart,
                "chartReason",
                chartReason);
    }

    public Map<String, Object> save(Integer id, int actor, InstrumentPolicyRequest r) {
        if (r == null
                || r.name() == null
                || r.name().isBlank()
                || r.name().length() > 120
                || r.venue() == null
                || !r.venue().matches("[A-Za-z0-9 ._-]{2,40}")
                || r.reason() == null
                || r.reason().trim().length() < 8
                || r.reason().length() > 500
                || r.revision() < 0)
            throw new IllegalArgumentException(
                    "Enter a name, venue and a meaningful change reason (8–500 characters)");
        String ticker = symbol(r.ticker()), providerSymbol = symbol(r.providerSymbol());
        var p = policy(id == null ? 1 : id, r, ticker, providerSymbol);
        if (p.quantityStep().signum() <= 0
                || p.quantityStep().scale() > 8
                || p.maxQuantity().compareTo(p.quantityStep()) < 0
                || p.maxQuantity().compareTo(new BigDecimal("1000000")) > 0
                || p.maxNotional().signum() <= 0
                || p.maxNotional().compareTo(new BigDecimal("1000000")) > 0
                || r.quoteMaxAgeSeconds() < 1
                || r.quoteMaxAgeSeconds() > 1800)
            throw new IllegalArgumentException("Invalid quantity, notional or quote-age limits");
        // Existing full halts must remain available during provider outages. All enable/edit
        // requests are freshly validated.
        Quote q = r.active() || id == null ? fetch(p) : null;
        if (id == null && q == null)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Provider identity is unavailable");
        if (q != null && !"USD".equals(q.currency()))
            throw new IllegalArgumentException("Only USD-priced instruments are supported");
        if (r.active()) {
            if (q == null)
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Provider quote is unavailable");
            var check =
                    new Policy(
                            p.instrumentId(),
                            p.ticker(),
                            true,
                            p.version(),
                            p.venue(),
                            p.quantityUnit(),
                            p.priceUnit(),
                            p.currency(),
                            p.quantityStep(),
                            p.currencyScale(),
                            p.maxQuantity(),
                            p.maxNotional(),
                            p.quoteMaxAgeSeconds(),
                            p.futureSkewSeconds(),
                            p.retrySeconds(),
                            p.maxAttempts(),
                            p.session(),
                            true,
                            p.rawPriceUnit(),
                            p.priceMultiplier(),
                            p.providerSymbol(),
                            true,
                            true);
            var e =
                    TradingRules.evaluate(
                            new Command(
                                    p.instrumentId(),
                                    "BUY",
                                    p.quantityStep().toPlainString(),
                                    "MARKET"),
                            check,
                            q,
                            clock.instant());
            if (!e.eligible())
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Cannot enable: " + e.reason());
        }
        return transaction.execute(
                status -> {
                    Map<String, Object> before = null;
                    if (id != null) {
                        if (db.lock(id) == null)
                            throw new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Instrument not found");
                        before = db.get(id);
                    }
                    long revision =
                            before == null ? 0 : ((Number) before.get("revision")).longValue();
                    if (revision != r.revision())
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Instrument changed; refresh and review before saving");
                    if (before != null
                            && (!ticker.equals(before.get("ticker"))
                                    || !"USD".equals(before.get("currency"))
                                    || before.get("providerSymbol") != null
                                            && !providerSymbol.equals(
                                                    before.get("providerSymbol"))))
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Economic identity is immutable; create a replacement instrument");
                    var args =
                            map(
                                    "assetClass",
                                    "EQUITY",
                                    "securityType",
                                    "STOCK",
                                    "name",
                                    r.name().trim(),
                                    "ticker",
                                    ticker,
                                    "providerSymbol",
                                    providerSymbol,
                                    "venue",
                                    r.venue(),
                                    "step",
                                    p.quantityStep(),
                                    "maxQuantity",
                                    p.maxQuantity(),
                                    "maxNotional",
                                    p.maxNotional(),
                                    "age",
                                    p.quoteMaxAgeSeconds(),
                                    "active",
                                    r.active(),
                                    "buyAllowed",
                                    r.buyAllowed(),
                                    "sellAllowed",
                                    r.sellAllowed(),
                                    "revision",
                                    revision + 1,
                                    "policyVersion",
                                    "provider-v" + (revision + 1),
                                    "session",
                                    r.active() ? "PROVIDER_OPEN" : "CLOSED",
                                    "validated",
                                    q == null ? before.get("validatedAt") : clock.instant());
                    int target = id == null ? db.create(args) : id;
                    args.put("id", target);
                    if (id != null) db.edit(args);
                    db.policy(args);
                    var after = db.get(target);
                    db.audit(
                            map(
                                    "audit",
                                    UUID.randomUUID(),
                                    "id",
                                    target,
                                    "actor",
                                    actor,
                                    "reason",
                                    r.reason().trim(),
                                    "before",
                                    encode(before),
                                    "after",
                                    encode(after),
                                    "now",
                                    clock.instant()));
                    return after;
                });
    }

    private BigDecimal decimal(String value) {
        if (value == null || !value.matches("[0-9]{1,10}(\\.[0-9]{1,12})?")) {
            throw new IllegalArgumentException("Enter positive exact decimal limits");
        }
        // PostgreSQL pads stored limits to twelve places. Padding is not extra precision.
        BigDecimal normalized = new BigDecimal(value).stripTrailingZeros();
        if (normalized.scale() > 8) {
            throw new IllegalArgumentException("Use at most eight decimal places for limits");
        }
        return normalized;
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot retain policy audit", e);
        }
    }
}
