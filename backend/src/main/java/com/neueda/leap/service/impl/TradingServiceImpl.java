package com.neueda.leap.service.impl;

import static com.neueda.leap.trading.TradingRules.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.mapper.TradingMapper;
import com.neueda.leap.service.TradingService;
import com.neueda.leap.trading.*;
import com.neueda.leap.trading.TradingModels.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class TradingServiceImpl implements TradingService {
    private final TradingMapper db;
    private final TradingTransactions tx;
    private final TradingQuotes quotes;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;

    public TradingServiceImpl(
            TradingMapper db,
            TradingTransactions tx,
            TradingQuotes quotes,
            ObjectMapper json,
            Clock clock,
            @Value("${trading.admission-enabled:true}") boolean enabled) {
        this.db = db;
        this.tx = tx;
        this.quotes = quotes;
        this.json = json;
        this.clock = clock;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Map<String, Object> tradeAccessState(int client) {
        Map<String, Object> state = db.tradeAccess(client);
        if (state == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client account not found.");
        Object until = state.get("revoked_until");
        boolean revoked = state.get("revoked_at") != null
                && (!(until instanceof java.time.OffsetDateTime expiry) || expiry.isAfter(OffsetDateTime.now(clock)));
        return Map.of("revoked", revoked, "revokedUntil", revoked && until != null ? until : "");
    }

    public Map<String, Object> availability(int client, Integer instrument) {
        String reason =
                !enabled
                        ? "New orders are paused"
                        : !db.schemaReady()
                                ? "Trading schema is unavailable"
                                : !db.onboarded(client) || !db.accountEnabled(client)
                                        ? "Complete your profile before trading"
                                        : !db.hasBank(client)
                                                ? "Add a saved bank account before buying or"
                                                        + " selling"
                                                : null;
        String code =
                reason == null
                        ? "ELIGIBLE"
                        : reason.startsWith("Add a saved bank") ? "BANK_REQUIRED" : "UNAVAILABLE";
        Policy p = instrument == null ? null : db.policy(instrument);
        if (reason == null
                && instrument != null
                && (p == null
                        || !p.enabled()
                        || !p.active()
                        || !"PROVIDER_OPEN".equals(p.session())
                        || !"USD".equals(p.currency()))) {
            reason = "This instrument is not currently enabled for USD trading";
            code = "INSTRUMENT_UNAVAILABLE";
        }
        var cash = db.cash(client, "USD");
        if (reason == null
                && (cash == null || !Boolean.TRUE.equals(cash.get("available_for_trading")))) {
            reason = "USD account requires review; contact support";
            code = "ACCOUNT_REVIEW";
        }
        boolean instrumentReady =
                instrument == null
                        || (p != null
                                && p.enabled()
                                && p.active()
                                && "PROVIDER_OPEN".equals(p.session())
                                && "USD".equals(p.currency()));
        boolean accountReady =
                cash != null && Boolean.TRUE.equals(cash.get("available_for_trading"));
        boolean buyAllowed = instrumentReady && accountReady && (p == null || p.buyAllowed());
        boolean sellAllowed = instrumentReady && accountReady && (p == null || p.sellAllowed());
        if (reason == null && p != null) {
            if (!buyAllowed && !sellAllowed) {
                reason = "Buying and selling are currently paused for this instrument";
                code = "TRADING_HALTED";
            } else if (!buyAllowed) code = "SELL_ONLY";
            else if (!sellAllowed) code = "BUY_ONLY";
        }
        return map(
                "buyAllowed",
                buyAllowed && (reason == null || code.equals("BANK_REQUIRED")),
                "sellAllowed",
                sellAllowed && (reason == null || code.equals("BANK_REQUIRED")),
                "enabled",
                reason == null,
                "reason",
                reason == null
                        ? "Internally booked trades · provider prices · zero project fees"
                        : reason,
                "code",
                code,
                "policyVersion",
                p == null ? null : p.version(),
                "quantityUnit",
                p == null ? null : p.quantityUnit(),
                "venue",
                p == null ? null : p.venue());
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Preview preview(int client, Command command) {
        requireEnabled();
        validate(command);
        Policy p = db.policy(command.instrumentId());
        Quote quote = quotes.quote(p);
        Instant now = clock.instant();
        Evaluation e = tx.resources(client, command, p, evaluate(command, p, quote, now));
        var cash = p == null ? null : db.cash(client, p.currency());
        var position = db.position(client, command.instrumentId());
        BigDecimal posted = amount(cash, "posted"), owned = amount(position, "posted");
        boolean buy = "BUY".equals(command.side());
        return new Preview(
                e.eligible(),
                e.code(),
                e.reason(),
                "INTERNAL",
                p == null ? null : p.version(),
                p == null ? null : p.currency(),
                p == null ? null : p.quantityUnit(),
                p == null ? null : p.venue(),
                decimal(e.price()),
                decimal(e.gross()),
                "0",
                decimal(posted.subtract(amount(cash, "reserved"))),
                decimal(owned.subtract(amount(position, "reserved"))),
                e.eligible()
                        ? decimal(buy ? posted.subtract(e.gross()) : posted.add(e.gross()))
                        : null,
                e.eligible()
                        ? decimal(
                                buy
                                        ? owned.add(validate(command))
                                        : owned.subtract(validate(command)))
                        : null,
                quote == null ? null : quote.providerTime(),
                quote == null || quote.providerTime() == null
                        ? null
                        : quote.providerTime().plusSeconds(p.quoteMaxAgeSeconds()),
                quote == null ? null : quote.source(),
                "Estimate only. Execution uses a fresh provider quote and is booked internally. No"
                        + " external order is routed.");
    }

    public Map<String, Object> submit(int client, int actor, String key, Command command) {
        validateKey(key);
        validate(command);
        String hash = hash(command);
        // Replay precedes configuration/quote checks, so a committed result survives a later outage
        // or halt.
        var prior = db.byKey(client, key);
        if (prior != null) {
            tx.checkHash(prior, hash);
            return receipt(prior);
        }
        requireEnabled();
        if (!db.instrumentExists(command.instrumentId()))
            throw new IllegalArgumentException(
                    "Unknown instrument ID; choose an instrument from the catalog");
        Policy p = db.policy(command.instrumentId());
        Quote q;
        try {
            q = quotes.quote(p);
        } catch (RuntimeException failure) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Quote service is unavailable; no acceptance was returned. Retry using the same"
                            + " request key");
        }
        return receipt(tx.admit(client, actor, key, hash, command, p, q));
    }

    public Map<String, Object> byRequest(int client, String key) {
        validateKey(key);
        return receipt(require(db.byKey(client, key)));
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object portfolio(int client, Object legacy) {
        var data = convert(legacy);
        for (String field : List.of("postedCash", "availableCash", "currency", "positions"))
            data.remove(field);
        data.put("balances", cleanDecimals(db.balances(client)));
        data.put("currentPositions", cleanDecimals(db.positions(client)));
        data.put("migrationExceptions", db.migrationExceptions(client));
        data.put("tradingEnabled", availability(client, null).get("enabled"));
        data.put(
                "limitation",
                "USD ledger. Available = settled posted cash − reserved cash. Internal settlement"
                        + " is immediate on booking; no real bank transfer or exchange execution.");
        return data;
    }

    public Object catalog(int client, Object page) {
        var data = convert(page);
        @SuppressWarnings("unchecked")
        var items = (List<Map<String, Object>>) data.get("items");
        for (var item : items)
            item.put(
                    "trading",
                    availability(client, ((Number) item.get("instrumentId")).intValue()));
        return data;
    }

    public Object orders(int client, Object legacy) {
        var data = convert(legacy);
        @SuppressWarnings("unchecked")
        var items = (List<Map<String, Object>>) data.get("items");
        for (var item : items)
            enrich(item, db.order(client, ((Number) item.get("orderId")).intValue()));
        return data;
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object detail(int client, int id, Object legacy) {
        var row = require(db.order(client, id));
        var data = convert(legacy);
        @SuppressWarnings("unchecked")
        var order = (Map<String, Object>) data.get("order");
        enrich(order, row);
        if ("SELF_DIRECTED".equals(row.get("record_kind"))) {
            var fill = db.fillFor(id);
            var evidence = db.clientEvidence(client, id);
            var checks = new ArrayList<Map<String, Object>>();
            var explanations =
                    Map.of(
                            "admission",
                            "Retained admission decision",
                            "timeline",
                            "Current state and contiguous retained event versions",
                            "notification",
                            "Retained notification facts (external delivery is not configured)",
                            "quotes",
                            "Decision-to-quote relationships",
                            "reservation",
                            "Reservation state for this order outcome",
                            "booking",
                            "Fill, execution quote, terminal event and owned signed posting"
                                + " relationships");
            boolean integrity =
                    evidence == null
                            || evidence.values().stream().anyMatch(v -> !Boolean.TRUE.equals(v));
            boolean pending = Set.of("ACCEPTED", "EXECUTING").contains(row.get("status"));
            for (String name :
                    List.of(
                            "admission",
                            "timeline",
                            "notification",
                            "quotes",
                            "reservation",
                            "booking"))
                checks.add(
                        map(
                                "code",
                                name.toUpperCase(Locale.ROOT),
                                "state",
                                Boolean.TRUE.equals(evidence == null ? null : evidence.get(name))
                                        ? (name.equals("booking") && pending
                                                ? "PENDING"
                                                : name.equals("booking")
                                                                && !"FILLED"
                                                                        .equals(row.get("status"))
                                                        ? "NOT_APPLICABLE"
                                                        : "RETAINED")
                                        : "INTEGRITY_FINDING",
                                "explanation",
                                explanations.get(name)));
            var quoteRows = db.clientQuotes(client, id);
            var decisions = db.clientDecisions(client, id);
            boolean bounded = quoteRows.size() > 200 || decisions.size() > 200;
            data.put(
                    "evidenceCompleteness",
                    integrity
                            ? "INTEGRITY_FINDING"
                            : bounded
                                    ? "PARTIAL"
                                    : pending ? "PENDING" : "RETAINED_INTERNAL_EVIDENCE");
            data.put("evidenceChecks", checks);
            data.put(
                    "explanation",
                    integrity
                            ? "Some expected retained relationships are missing or inconsistent."
                                  + " Report the issue for review."
                            : pending
                                    ? "The accepted order is still processing. A reservation is not"
                                          + " a fill. Terminal evidence is not yet applicable."
                                    : "Checks cover retained admission, event, quote, reservation"
                                          + " and internal booking relationships. They do not"
                                          + " certify external execution or settlement.");
            data.put("reservation", safeReservation(db.reservationFor(id)));
            data.put("fills", fill == null ? List.of() : List.of(safeFill(fill)));
            data.put("decisions", decisions.stream().limit(200).map(this::clientDecision).toList());
            data.put("events", db.events(client, id, 100, 0));
            data.put("eventTotal", db.eventCount(client, id));
            data.put(
                    "quotes",
                    quoteRows.stream()
                            .limit(200)
                            .map(q -> clientQuote(q, "BUY".equals(row.get("trade_type"))))
                            .toList());
            data.put("quoteCoverage", bounded ? "PARTIAL" : "RETAINED_OBSERVATIONS");
        }
        return data;
    }

    public Object events(int client, int id, int page, int size) {
        require(db.order(client, id));
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid pagination");
        return map(
                "items",
                db.events(client, id, size, page * size),
                "page",
                page,
                "pageSize",
                size,
                "total",
                db.eventCount(client, id));
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object changes(int client, long cursor) {
        if (cursor < 0) throw new IllegalArgumentException("Cursor must be nonnegative");
        long latest = db.cursor(client);
        var events = db.changes(client, cursor);
        // Each cursor is allocated while holding the client lock, in the same transaction as its
        // event.
        long next =
                events.isEmpty()
                        ? latest
                        : ((Number) events.get(events.size() - 1).get("cursor")).longValue();
        return map(
                "items",
                events,
                "cursor",
                next,
                "reset",
                cursor > latest,
                "hasMore",
                next < latest);
    }

    private void enrich(Map<String, Object> target, Map<String, Object> row) {
        target.put("recordKind", row.get("record_kind"));
        if (!"SELF_DIRECTED".equals(row.get("record_kind"))) return;
        target.putAll(receipt(row));
        target.put("currency", row.get("currency_snapshot"));
        target.put("exactQuantity", decimal(amount(row, "quantity")));
        target.put("submittedAt", row.get("submitted_at"));
        target.put("updatedAt", row.get("updated_at"));
        target.put("policyVersion", tx.policy(row) == null ? null : tx.policy(row).version());
        var fill = db.fillFor(((Number) row.get("trade_id")).intValue());
        target.put("fill", fill == null ? null : safeFill(fill));
    }

    private Map<String, Object> receipt(Map<String, Object> row) {
        int id = ((Number) row.get("trade_id")).intValue();
        return map(
                "orderId",
                id,
                "recordKind",
                "SELF_DIRECTED",
                "status",
                row.get("status"),
                "acceptedAt",
                row.get("accepted_at"),
                "version",
                row.get("version"),
                "statusUrl",
                "/api/orders/" + id,
                "executionMode",
                tx.policy(row) != null && tx.policy(row).providerSymbol() != null
                        ? "INTERNAL"
                        : "HISTORICAL_SYNTHETIC",
                "rejectionStage",
                row.get("rejection_stage"),
                "code",
                row.get("rejection_code"),
                "message",
                row.get("reason"));
    }

    private List<Map<String, Object>> cleanDecimals(List<Map<String, Object>> rows) {
        for (var row : rows)
            for (String key : List.of("posted", "reserved", "available", "owned"))
                if (row.containsKey(key))
                    row.put(key, decimal(new BigDecimal(row.get(key).toString())));
        return rows;
    }

    private Object safeFill(Map<String, Object> row) {
        return map(
                "fillId",
                row.get("fill_id"),
                "executionReference",
                row.get("execution_reference"),
                "quantity",
                decimal(amount(row, "quantity")),
                "price",
                decimal(amount(row, "price")),
                "gross",
                decimal(amount(row, "gross")),
                "fee",
                "0",
                "currency",
                row.get("currency"),
                "postingGroup",
                row.get("posting_group"),
                "executedAt",
                row.get("executed_at"),
                "bookedAt",
                row.get("booked_at"));
    }

    private Object safeReservation(Map<String, Object> row) {
        return row == null
                ? null
                : map(
                        "resource",
                        row.get("resource"),
                        "amount",
                        decimal(amount(row, "amount")),
                        "state",
                        row.get("state"));
    }

    private Map<String, Object> convert(Object v) {
        return json.convertValue(v, new TypeReference<Map<String, Object>>() {});
    }

    private Object clientQuote(Map<String, Object> row, boolean buy) {
        var value =
                (com.fasterxml.jackson.databind.JsonNode)
                        parse(Objects.toString(row.get("facts"), "{}"));
        String price = null;
        try {
            if (value.path(buy ? "ask" : "bid").isValueNode()
                    && !value.path(buy ? "ask" : "bid").isNull())
                price = decimal(new BigDecimal(value.path(buy ? "ask" : "bid").asText()));
        } catch (NumberFormatException ignored) {
        }
        var result =
                map(
                        "quoteId",
                        row.get("id"),
                        "stage",
                        row.get("stage"),
                        "applicableSide",
                        buy ? "ASK" : "BID",
                        "price",
                        price,
                        "usedForFill",
                        row.get("usedForFill"));
        for (String field :
                List.of(
                        "currency",
                        "providerTime",
                        "receivedAt",
                        "metadataTime",
                        "source",
                        "marketState")) {
            var node = value.get(field);
            result.put(
                    field,
                    node != null && node.isTextual() && node.asText().length() <= 200
                            ? node.asText()
                            : null);
        }
        result.put(
                "stale", value.path("stale").isBoolean() ? value.path("stale").asBoolean() : null);
        return result;
    }

    private Object clientDecision(Map<String, Object> row) {
        var result =
                map(
                        "stage",
                        row.get("stage"),
                        "passed",
                        row.get("passed"),
                        "code",
                        row.get("code"),
                        "explanation",
                        row.get("explanation"),
                        "recordedAt",
                        row.get("recordedAt"));
        var checks = new ArrayList<Map<String, Object>>();
        var allowed =
                Set.of(
                        "01_INSTRUMENT_UNAVAILABLE",
                        "02_SESSION_CLOSED",
                        "03_CURRENCY_UNSUPPORTED",
                        "04_SIDE_HALTED",
                        "05_QUANTITY_STEP",
                        "06_QUANTITY_LIMIT",
                        "07_QUOTE_MISSING",
                        "08_QUOTE_STALE",
                        "09_QUOTE_METADATA",
                        "10_QUOTE_METADATA",
                        "11_QUOTE_METADATA",
                        "12_QUOTE_METADATA",
                        "13_SESSION_CLOSED",
                        "14_QUOTE_METADATA_TIME",
                        "15_QUOTE_MAPPING",
                        "16_QUOTE_INVALID",
                        "17_QUOTE_FUTURE",
                        "18_QUOTE_STALE",
                        "19_QUOTE_PRECISION",
                        "20_NOTIONAL_LIMIT",
                        "POLICY_CHANGED",
                        "ACCOUNT_UNAVAILABLE",
                        "BANK_REQUIRED",
                        "CURRENCY_UNAVAILABLE",
                        "INSUFFICIENT_CASH",
                        "POSITION_REVIEW",
                        "INSUFFICIENT_UNITS");
        if (row.get("context") != null) {
            var context =
                    (com.fasterxml.jackson.databind.JsonNode) parse(row.get("context").toString());
            if (context.path("checks").isArray())
                for (var check : context.path("checks")) {
                    String code = check.path("code").asText(),
                            outcome = check.path("result").asText();
                    if (checks.size() < 30
                            && allowed.contains(code)
                            && Set.of("PASS", "FAIL", "NOT_EVALUATED").contains(outcome))
                        checks.add(map("code", code, "result", outcome));
                }
        }
        result.put("checks", checks);
        result.put(
                "checkCoverage", checks.isEmpty() ? "NOT_RETAINED" : "RETAINED_ALLOWLISTED_CHECKS");
        return result;
    }

    private Object parse(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid evidence", e);
        }
    }

    private void requireEnabled() {
        if (!enabled)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Trading is not enabled. No order has been accepted or recorded.");
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("[a-zA-Z0-9_-]{16,128}"))
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 16–128 letters, digits, underscores or hyphens");
    }

    private String hash(Command c) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (c.instrumentId()
                                                            + "|"
                                                            + c.side()
                                                            + "|"
                                                            + decimal(validate(c))
                                                            + "|MARKET")
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> require(Map<String, Object> row) {
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return row;
    }

    private static BigDecimal amount(Map<String, Object> row, String key) {
        return row == null || row.get(key) == null
                ? BigDecimal.ZERO
                : new BigDecimal(row.get(key).toString());
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
        return m;
    }
}
