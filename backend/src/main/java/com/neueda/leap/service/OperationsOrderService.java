package com.neueda.leap.service;

import com.fasterxml.jackson.databind.*;
import com.neueda.leap.dto.OperationsDtos.*;
import com.neueda.leap.mapper.OperationsOrderMapper;
import com.neueda.leap.service.OperationsAccessService.Access;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class OperationsOrderService {
    public static final List<String> SECTIONS =
            List.of(
                    "events",
                    "decisions",
                    "quotes",
                    "attempts",
                    "reservations",
                    "fills",
                    "postings",
                    "notifications");
    public static final int EVIDENCE_LIMIT = 2000;
    private static final Set<String> POLICY_FIELDS =
            Set.of(
                    "instrumentId",
                    "ticker",
                    "active",
                    "version",
                    "venue",
                    "quantityUnit",
                    "priceUnit",
                    "currency",
                    "quantityStep",
                    "currencyScale",
                    "maxQuantity",
                    "maxNotional",
                    "quoteMaxAgeSeconds",
                    "futureSkewSeconds",
                    "retrySeconds",
                    "maxAttempts",
                    "session",
                    "enabled",
                    "rawPriceUnit",
                    "priceMultiplier",
                    "providerSymbol",
                    "buyAllowed",
                    "sellAllowed");
    private static final Set<String> QUOTE_FIELDS =
            Set.of(
                    "quoteId",
                    "instrumentId",
                    "symbol",
                    "currency",
                    "rawPriceUnit",
                    "priceUnit",
                    "rawBid",
                    "rawAsk",
                    "bid",
                    "ask",
                    "providerTime",
                    "receivedAt",
                    "source",
                    "spreadOrigin",
                    "marketState",
                    "stale",
                    "metadataTime");
    private final OperationsOrderMapper db;
    private final OperationsAccessService access;
    private final OperationsCursor cursors;
    private final ObjectMapper json;
    private final Clock clock;
    private final com.neueda.leap.mapper.OperationsReconciliationMapper reconciliations;

    public OperationsOrderService(
            OperationsOrderMapper db,
            OperationsAccessService access,
            OperationsCursor cursors,
            ObjectMapper json,
            Clock clock,
            com.neueda.leap.mapper.OperationsReconciliationMapper reconciliations) {
        this.db = db;
        this.access = access;
        this.cursors = cursors;
        this.json = json;
        this.clock = clock;
        this.reconciliations = reconciliations;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public OrderPage search(UUID context, Map<String, String> input) {
        Access grant = access.require(context, "ORDER_READ");
        var p = filters(input, grant.now());
        String canonical = new TreeMap<>(p).toString();
        String binding =
                OperationsAccessService.digest(
                        grant.actor()
                                + "|"
                                + context
                                + "|"
                                + grant.scopeVersion()
                                + "|"
                                + canonical);
        int size = (int) p.remove("pageSize");
        String cursor = input.get("cursor");
        if (cursor != null && !cursor.isBlank()) {
            var position = cursors.decode(cursor, binding);
            p.put("cursorTime", position.time());
            p.put("cursorId", position.id());
        }
        p.putAll(grant.parameters());
        p.put("limit", size + 1);
        var rows = db.orders(p);
        boolean more = rows.size() > size;
        var items = rows.stream().limit(size).map(this::row).toList();
        String next = null;
        if (more && !items.isEmpty()) {
            var last = items.get(items.size() - 1);
            next = cursors.encode(last.submittedAt(), last.orderId(), binding);
        }
        access.success(grant, "SEARCH", "ORDERS", null, canonical, items.size(), null);
        return new OrderPage(items, next, more, clock.instant());
    }

    private Map<String, Object> filters(Map<String, String> input, Instant now) {
        var allowed =
                Set.of(
                        "orderId",
                        "accountReference",
                        "instrumentId",
                        "status",
                        "side",
                        "recordKind",
                        "rejectionStage",
                        "from",
                        "to",
                        "pageSize",
                        "cursor",
                        "queue");
        if (!allowed.containsAll(input.keySet())) bad("Unknown search filter");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("pageSize", integer(input.getOrDefault("pageSize", "50"), 1, 100));
        for (String key : List.of("orderId", "instrumentId"))
            if (present(input, key)) p.put(key, integer(input.get(key), 1, Integer.MAX_VALUE));
        for (String key :
                List.of(
                        "accountReference",
                        "status",
                        "side",
                        "recordKind",
                        "rejectionStage",
                        "queue")) if (present(input, key)) p.put(key, input.get(key));
        String kind = input.getOrDefault("recordKind", "SELF_DIRECTED");
        if (!Set.of("SELF_DIRECTED", "LEGACY").contains(kind)) bad("Invalid record kind");
        p.put("recordKind", kind);
        validateChoice(input, "side", Set.of("BUY", "SELL"));
        validateChoice(input, "rejectionStage", Set.of("ADMISSION", "EXECUTION"));
        validateChoice(input, "queue", Set.of("REJECTED", "DELAYED", "RETRYING"));
        validateChoice(
                input,
                "status",
                kind.equals("LEGACY")
                        ? Set.of("PENDING", "APPROVED", "EXECUTED", "REJECTED")
                        : Set.of("ACCEPTED", "EXECUTING", "FILLED", "REJECTED"));
        if (present(input, "accountReference")
                && !input.get("accountReference").matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}"))
            bad("Use an exact approved account reference");
        if (kind.equals("LEGACY")) {
            if (!p.containsKey("orderId")
                    || present(input, "cursor")
                    || present(input, "from")
                    || present(input, "to")
                    || present(input, "queue")
                    || present(input, "rejectionStage"))
                bad(
                        "Legacy records require an exact order reference; precise submission time"
                                + " is unavailable");
        } else if (!p.containsKey("orderId") || present(input, "from") || present(input, "to")) {
            try {
                Instant from =
                        present(input, "from")
                                ? Instant.parse(input.get("from"))
                                : now.truncatedTo(ChronoUnit.DAYS).minus(7, ChronoUnit.DAYS);
                Instant to =
                        present(input, "to")
                                ? Instant.parse(input.get("to"))
                                : now.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
                if (!from.isBefore(to)
                        || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0)
                    bad("Choose a positive submission-time range of at most 31 days");
                p.put("from", from);
                p.put("to", to);
            } catch (java.time.format.DateTimeParseException e) {
                bad("Use ISO-8601 UTC timestamps");
            }
        }
        return p;
    }

    private boolean present(Map<String, String> input, String key) {
        return input.get(key) != null && !input.get(key).isBlank();
    }

    private void validateChoice(Map<String, String> input, String key, Set<String> options) {
        if (present(input, key) && !options.contains(input.get(key))) bad("Invalid " + key);
    }

    public static int integer(String value, int min, int max) {
        try {
            int n = Integer.parseInt(value);
            if (n < min || n > max) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid numeric limit or reference");
        }
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public Investigation detail(UUID context, int id) {
        Access grant = access.require(context, "EVIDENCE_READ");
        var result = snapshot(grant, id, 100);
        access.success(grant, "INVESTIGATE", "ORDER", Integer.toString(id), "", 1, null);
        return result;
    }

    public record ExportSnapshot(
            Investigation evidence,
            EvidencePage policyHistory,
            ReconciliationView reconciliation,
            String scopeVersion) {}

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public ExportSnapshot exportSnapshot(UUID context, int id) {
        var grant = access.require(context, "EVIDENCE_EXPORT");
        var source = authorizedOrder(grant, id);
        authorizedOrder(access.require(context, "EVIDENCE_READ"), id);
        var evidence = snapshot(grant, id, EVIDENCE_LIMIT);
        var history = db.history(evidence.order().instrumentId(), EVIDENCE_LIMIT + 1, 0);
        var historyRows =
                history.stream()
                        .limit(EVIDENCE_LIMIT)
                        .map(
                                raw -> {
                                    var copy = new LinkedHashMap<>(raw);
                                    var fields =
                                            Set.of(
                                                    "ticker",
                                                    "name",
                                                    "currency",
                                                    "active",
                                                    "providerSymbol",
                                                    "venue",
                                                    "quantityStep",
                                                    "maxQuantity",
                                                    "maxNotional",
                                                    "quoteMaxAgeSeconds",
                                                    "buyAllowed",
                                                    "sellAllowed",
                                                    "revision");
                                    copy.put(
                                            "beforeValues",
                                            parseAllowed(copy.get("beforeValues"), fields));
                                    copy.put(
                                            "afterValues",
                                            parseAllowed(copy.get("afterValues"), fields));
                                    return evidence(copy);
                                })
                        .toList();
        ReconciliationView reconciliation =
                new ReconciliationView(
                        null,
                        null,
                        "NOT_INCLUDED",
                        "Separate reconciliation entitlement required.",
                        List.of(),
                        false,
                        0);
        if (access.has(
                grant, "RECONCILIATION_READ", ((Number) source.get("clientId")).intValue())) {
            var run = reconciliations.run(null);
            if (run != null) {
                var parameters = grant.parameters();
                parameters.put("id", run.get("run_id"));
                parameters.put("order", id);
                var findings = reconciliations.related(parameters);
                reconciliation =
                        new ReconciliationView(
                                (UUID) run.get("run_id"),
                                ((Timestamp) run.get("checked_at")).toInstant(),
                                "SCOPED_NOT_CERTIFIED",
                                "Only findings explicitly linked to this order in the latest stored"
                                        + " run; absence does not certify the account or full run.",
                                findings.stream()
                                        .limit(EVIDENCE_LIMIT)
                                        .map(this::evidence)
                                        .toList(),
                                findings.size() > EVIDENCE_LIMIT,
                                0);
            } else
                reconciliation =
                        new ReconciliationView(
                                null, null, "UNAVAILABLE", "No stored run.", List.of(), false, 0);
        }
        access.success(grant, "EXPORT_SNAPSHOT", "ORDER", Integer.toString(id), "", 1, null);
        return new ExportSnapshot(
                evidence,
                new EvidencePage(historyRows, 0, history.size() > EVIDENCE_LIMIT),
                reconciliation,
                access.exportScope(grant));
    }

    /** Called inside the caller's repeatable-read transaction, including export generation. */
    private Investigation snapshot(Access grant, int id, int limit) {
        // Never accept a supplied Access record as independent authorization.
        grant = access.require(grant.context(), grant.permission());
        var source = authorizedOrder(grant, id);
        var sections = new LinkedHashMap<String, EvidencePage>();
        for (String section : SECTIONS)
            sections.put(section, readSection(grant, id, section, 0, EVIDENCE_LIMIT));
        var findings = OperationsEvidenceChecks.check(row(source), sections);
        String status =
                "SELF_DIRECTED".equals(source.get("recordKind"))
                        ? (!findings.isEmpty()
                                ? "INCOMPLETE"
                                : Set.of("ACCEPTED", "EXECUTING").contains(source.get("status"))
                                        ? "PENDING"
                                        : "COMPLETE_FOR_INTERNAL_BOOKING")
                        : "INCOMPLETE_LEGACY";
        if (limit < EVIDENCE_LIMIT)
            sections.replaceAll(
                    (name, page) ->
                            new EvidencePage(
                                    page.items().stream().limit(limit).toList(),
                                    0,
                                    page.hasMore() || page.items().size() > limit));
        int client = ((Number) source.get("clientId")).intValue();
        return new Investigation(
                row(source),
                source.get("submittedActor") == null
                        ? "NOT_RECORDED"
                        : "user:" + source.get("submittedActor"),
                parseAllowed(source.get("policySnapshot"), POLICY_FIELDS),
                normalize(db.currentPolicy(((Number) source.get("instrumentId")).intValue())),
                clock.instant(),
                status,
                findings,
                sections,
                access.has(grant, "EVIDENCE_EXPORT", client),
                "Lifecycle uses its recorded sequence. Equal timestamps across sections do not"
                        + " prove causal order.",
                "Older decisions may lack rule inputs, software version and correlation. Current"
                        + " balances are not historical balances.");
    }

    public Map<String, Object> authorizedOrder(Access grant, int id) {
        grant = access.require(grant.context(), grant.permission());
        if (id < 1) bad("Order reference must be positive");
        var p = grant.parameters();
        p.put("id", id);
        var source = db.order(p);
        if (source == null) access.notFound(grant, Integer.toString(id));
        return source;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public EvidencePage section(UUID context, int id, String section, int page) {
        var grant = access.require(context, "EVIDENCE_READ");
        authorizedOrder(grant, id);
        if (!SECTIONS.contains(section) || page < 0 || page > 1000)
            bad("Invalid evidence section or page");
        var result = readSection(grant, id, section, page, 100);
        access.success(
                grant,
                "EVIDENCE_PAGE",
                "ORDER",
                Integer.toString(id),
                section + ":" + page,
                result.items().size(),
                null);
        return result;
    }

    private EvidencePage readSection(Access grant, int id, String section, int page, int limit) {
        var p = grant.parameters();
        p.put("id", id);
        p.put("section", section);
        p.put("limit", limit + 1);
        p.put("offset", page * limit);
        var records = db.section(p);
        return new EvidencePage(
                records.stream().limit(limit).map(this::evidence).toList(),
                page,
                records.size() > limit);
    }

    public OrderRow row(Map<String, Object> source) {
        var copy = new LinkedHashMap<>(source);
        copy.remove("clientId");
        copy.remove("policySnapshot");
        copy.remove("submittedActor");
        return json.convertValue(normalize(copy), OrderRow.class);
    }

    public EvidenceRow evidence(Map<String, Object> source) {
        var values = new LinkedHashMap<>(source);
        String reference = Objects.toString(values.remove("reference"), "");
        String time = Objects.toString(normalize(values.remove("recordedAt")), null);
        if (values.containsKey("quote"))
            values.put("quote", parseAllowed(values.get("quote"), QUOTE_FIELDS));
        if (values.containsKey("decisionContext"))
            values.put(
                    "decisionContext",
                    parseAllowed(
                            values.get("decisionContext"),
                            Set.of(
                                    "schemaVersion",
                                    "checks",
                                    "acceptedPolicy",
                                    "currentControl",
                                    "resources",
                                    "quoteSide",
                                    "rounding",
                                    "serviceActor",
                                    "softwareVersion",
                                    "correlationId",
                                    "reservationReference",
                                    "observedAt")));
        return new EvidenceRow(reference, time, castMap(normalize(values)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    public Object parseAllowed(Object raw, Set<String> fields) {
        if (raw == null) return "NOT_RECORDED";
        try {
            JsonNode tree = json.readTree(raw.toString());
            if (!tree.isObject()) return "NOT_RECORDED";
            var result = new LinkedHashMap<String, Object>();
            fields.stream()
                    .sorted()
                    .forEach(
                            field -> {
                                if (tree.has(field)) result.put(field, jsonValue(tree.get(field)));
                            });
            return result;
        } catch (Exception e) {
            return "UNREADABLE_RETAINED_EVIDENCE";
        }
    }

    private Object jsonValue(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isNumber()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isTextual()) return node.asText();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(n -> list.add(jsonValue(n)));
            return list;
        }
        var result = new LinkedHashMap<String, Object>();
        node.fields().forEachRemaining(e -> result.put(e.getKey(), jsonValue(e.getValue())));
        return result;
    }

    public Object normalize(Object value) {
        if (value instanceof Timestamp time) return time.toInstant().toString();
        if (value instanceof OffsetDateTime time) return time.toInstant().toString();
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        if (value instanceof UUID id) return id.toString();
        if (value instanceof Map<?, ?> values) {
            var result = new LinkedHashMap<String, Object>();
            values.forEach((k, v) -> result.put(k.toString(), normalize(v)));
            return result;
        }
        return value;
    }
}
