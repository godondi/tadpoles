package com.neueda.leap.service;

import static com.neueda.leap.trading.TradingData.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.ComplianceDtos.*;
import com.neueda.leap.mapper.ComplianceEvidenceMapper;
import com.neueda.leap.service.ComplianceAccessService.Access;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ComplianceEvidenceService {
    private static final Set<String> STATUSES =
            Set.of("SUBMITTED", "ACCEPTED", "REJECTED", "FILLED", "CANCELLED");
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
                    "sellAllowed",
                    "revision");

    private final ComplianceEvidenceMapper db;
    private final ComplianceAccessService access;
    private final ObjectMapper json;
    private final Clock clock;

    public ComplianceEvidenceService(
            ComplianceEvidenceMapper db,
            ComplianceAccessService access,
            ObjectMapper json,
            Clock clock) {
        this.db = db;
        this.access = access;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public SearchPage search(UUID context, Map<String, String> input) {
        try {
            Access grant = access.require(context, "EVIDENCE_READ");
            Map<String, Object> filters = filters(input, grant.now());
            Map<String, Object> parameters = parameters(grant, filters);
            long total = db.count(parameters);
            List<OrderSummary> items =
                    db.orders(parameters).stream().map(ComplianceEvidenceService::summary).toList();
            access.success(
                    grant,
                    "SEARCH",
                    "ORDERS",
                    null,
                    new TreeMap<>(input).toString(),
                    items.size(),
                    null);
            return new SearchPage(
                    items,
                    (Integer) filters.get("page"),
                    (Integer) filters.get("pageSize"),
                    total,
                    access.scopeLabel(),
                    clock.instant());
        } catch (DataAccessException failure) {
            throw unavailable();
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public Investigation detail(UUID context, int id) {
        try {
            Access grant = access.require(context, "EVIDENCE_READ");
            Investigation result = snapshot(grant, id, 25);
            access.success(grant, "INVESTIGATE", "ORDER", Integer.toString(id), "", 1, null);
            return result;
        } catch (DataAccessException failure) {
            throw unavailable();
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public EvidenceSection section(UUID context, int id, String section, int page, int pageSize) {
        try {
            Access grant = access.require(context, "EVIDENCE_READ");
            validateSection(section);
            int checkedPage = integer(Integer.toString(page), 0, 1000);
            int checkedSize = integer(Integer.toString(pageSize), 1, 100);
            authorizedOrder(grant, id);
            EvidenceSection result =
                    readSection(grant, id, section, checkedPage, checkedSize, clock.instant());
            access.success(
                    grant,
                    "SECTION",
                    "ORDER",
                    Integer.toString(id),
                    section + ":" + checkedPage,
                    result.items().size(),
                    null);
            return result;
        } catch (DataAccessException failure) {
            throw unavailable();
        }
    }

    public record ExportSnapshot(Investigation detail, String scopeVersion, int clientId) {}

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public ExportSnapshot snapshotForExport(UUID context, int id) {
        try {
            Access exportGrant = access.require(context, "EXPORT_CREATE");
            Access readGrant = access.require(context, "EVIDENCE_READ");
            Map<String, Object> source = authorizedOrder(readGrant, id);
            int clientId = integer(source, "clientId");
            List<EvidenceSection> sections = new ArrayList<>();
            Instant snapshotAt = clock.instant();
            for (String section : ComplianceEvidenceMapper.SECTIONS) {
                sections.add(readSection(readGrant, id, section, 0, 2000, snapshotAt));
            }
            Investigation result =
                    investigation(
                            source,
                            sections,
                            snapshotAt,
                            readGrant,
                            clientId,
                            2000);
            access.success(
                    exportGrant,
                    "EXPORT_SNAPSHOT",
                    "ORDER",
                    Integer.toString(id),
                    "",
                    sections.stream().mapToInt(section -> section.items().size()).sum(),
                    null);
            return new ExportSnapshot(result, exportGrant.scopeVersion(), clientId);
        } catch (DataAccessException failure) {
            throw unavailable();
        }
    }

    public Map<String, Object> authorizedOrder(Access grant, int id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order ID must be positive");
        }
        Map<String, Object> parameters = parameters(grant, Map.of("id", id));
        Map<String, Object> row = db.order(parameters);
        if (row == null) access.notFound(grant, Integer.toString(id));
        return row;
    }

    private Investigation snapshot(Access grant, int id, int pageSize) {
        Map<String, Object> source = authorizedOrder(grant, id);
        int clientId = integer(source, "clientId");
        Instant snapshotAt = clock.instant();
        List<EvidenceSection> sections = new ArrayList<>();
        for (String section : ComplianceEvidenceMapper.SECTIONS) {
            sections.add(readSection(grant, id, section, 0, pageSize, snapshotAt));
        }
        return investigation(source, sections, snapshotAt, grant, clientId, pageSize);
    }

    private Investigation investigation(
            Map<String, Object> source,
            List<EvidenceSection> sections,
            Instant snapshotAt,
            Access grant,
            int clientId,
            int pageSize) {
        List<Finding> findings = findings(source, sections, grant, pageSize);
        List<String> limitations = new ArrayList<>();
        if ("LEGACY".equals(string(source, "recordKind"))) {
            limitations.add("Historical record is partial; unavailable legacy facts remain unknown.");
        }
        if (sections.stream().anyMatch(EvidenceSection::hasMore)) {
            limitations.add("Some evidence sections require independent pagination.");
        }
        if (findings.stream().anyMatch(finding -> "UNKNOWN".equals(finding.status()))) {
            limitations.add("One or more retained evidence sources are incomplete.");
        }
        return new Investigation(
                summary(source),
                access.actorLabel(grant),
                Objects.toString(source.get("clientReference"), "Not recorded"),
                parsePolicy(source.get("policySnapshot")),
                currentPolicy(source),
                snapshotAt,
                sections,
                findings,
                access.has(grant, "EXPORT_CREATE", clientId),
                limitations);
    }

    private List<Finding> findings(
            Map<String, Object> source,
            List<EvidenceSection> sections,
            Access grant,
            int pageSize) {
        List<Finding> findings = new ArrayList<>();
        if ("LEGACY".equals(string(source, "recordKind"))) {
            findings.add(
                    new Finding(
                            "LEGACY_RECORD",
                            "Legacy record lacks the complete self-directed evidence contract.",
                            "UNKNOWN"));
        }
        EvidenceSection events = find(sections, "events");
        EvidenceSection decisions = find(sections, "decisions");
        EvidenceSection quotes = find(sections, "quotes");
        if (events.total() == 0) {
            findings.add(
                    new Finding(
                            "LIFECYCLE_MISSING",
                            "No retained lifecycle event was found for this order.",
                            "UNKNOWN"));
        }
        if (source.get("submittedAt") == null) {
            findings.add(
                    new Finding(
                            "SUBMISSION_TIME_MISSING",
                            "The submitted timestamp was not retained.",
                            "UNKNOWN"));
        }
        if (decisions.items().stream()
                .anyMatch(
                        item ->
                                "Not recorded".equals(
                                        item.values().get("decisionContext")))) {
            findings.add(
                    new Finding(
                            "DECISION_CONTEXT_MISSING",
                            "A decision was retained without its decision context.",
                            "UNKNOWN"));
        }
        Map<String, Object> findingParameters =
                parameters(
                        grant,
                        map(
                                "orderId",
                                integer(source, "orderId"),
                                "limit",
                                Math.min(pageSize, 100)));
        for (Map<String, Object> row : db.findings(findingParameters)) {
            String kind =
                    Objects.toString(row.get("kind"), "UNSPECIFIED")
                            .toUpperCase(Locale.ROOT)
                            .replaceAll("[^A-Z0-9_]", "_");
            findings.add(
                    new Finding(
                            "RECONCILIATION_" + kind,
                            "A retained reconciliation finding is linked to this order.",
                            "UNKNOWN"));
        }
        if ("SELF_DIRECTED".equals(string(source, "recordKind"))
                && "FILLED".equals(string(source, "status"))
                && find(sections, "fills").total() == 0) {
            findings.add(
                    new Finding(
                            "FILL_MISSING",
                            "The order is marked filled but no retained fill was found.",
                            "FAIL"));
        }
        if ("SELF_DIRECTED".equals(string(source, "recordKind"))
                && ("ACCEPTED".equals(string(source, "status"))
                        || "FILLED".equals(string(source, "status")))
                && quotes.total() == 0) {
            findings.add(
                    new Finding(
                            "QUOTE_MISSING",
                            "No retained provider quote observation was found.",
                            "UNKNOWN"));
        }
        if (source.get("acceptedAt") == null
                && "SELF_DIRECTED".equals(string(source, "recordKind"))
                && !"REJECTED".equals(string(source, "status"))) {
            findings.add(
                    new Finding(
                            "ACCEPTANCE_TIME_MISSING",
                            "The accepted timestamp was not retained.",
                            "NOT_EVALUATED"));
        }
        return findings;
    }

    private EvidenceSection readSection(
            Access grant, int id, String section, int page, int pageSize, Instant asOf) {
        validateSection(section);
        Map<String, Object> parameters =
                parameters(
                        grant,
                        map(
                                "id",
                                id,
                                "section",
                                section,
                                "limit",
                                pageSize,
                                "offset",
                                page * pageSize,
                                "count",
                                false));
        List<Map<String, Object>> rows = db.section(parameters);
        parameters.put("count", true);
        long total = db.sectionCount(parameters);
        List<EvidenceRecord> items = rows.stream().map(this::evidence).toList();
        return new EvidenceSection(
                section,
                items,
                page,
                pageSize,
                total,
                (long) (page + 1) * pageSize < total,
                coverage(section),
                source(section),
                asOf,
                completeness(section, items, total));
    }

    private Map<String, Object> parameters(Access grant, Map<String, Object> values) {
        Map<String, Object> parameters = new LinkedHashMap<>(values);
        parameters.put("actor", grant.actor());
        parameters.put("permission", "EVIDENCE_READ");
        parameters.put("now", grant.now());
        return parameters;
    }

    private Map<String, Object> filters(Map<String, String> input, Instant now) {
        Set<String> allowed =
                Set.of("orderId", "from", "to", "clientReference", "instrument", "status", "page", "pageSize");
        if (input == null || !allowed.containsAll(input.keySet())) {
            bad("Unknown investigation filter");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("page", integer(input.get("page"), 0, 1000));
        values.put("pageSize", integer(input.get("pageSize"), 1, 100));
        if (present(input, "orderId")) values.put("orderId", integer(input.get("orderId"), 1, Integer.MAX_VALUE));
        if (present(input, "clientReference")) {
            String value = input.get("clientReference");
            if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) bad("Invalid client reference");
            values.put("clientReference", value);
        }
        if (present(input, "instrument")) {
            String value = input.get("instrument");
            if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) bad("Invalid instrument");
            values.put("instrument", value);
        }
        if (present(input, "status")) {
            if (!STATUSES.contains(input.get("status"))) bad("Invalid status");
            values.put("status", input.get("status"));
        }
        if (present(input, "from") || present(input, "to")) {
            Instant from = parseInstant(input.get("from"), now.minus(31, ChronoUnit.DAYS));
            Instant to = parseInstant(input.get("to"), now);
            if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
                bad("Choose a UTC time range of at most 31 days");
            }
            values.put("from", from);
            values.put("to", to);
        }
        return values;
    }

    private static Instant parseInstant(String value, Instant fallback) {
        if (!present(value)) return fallback;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException failure) {
            bad("Use ISO-8601 UTC timestamps");
            return fallback;
        }
    }

    private static OrderSummary summary(Map<String, Object> row) {
        return new OrderSummary(
                integer(row, "orderId"),
                string(row, "clientReference"),
                string(row, "instrument"),
                string(row, "side"),
                string(row, "quantity"),
                string(row, "unit"),
                string(row, "status"),
                instant(row, "submittedAt"),
                instant(row, "acceptedAt"),
                instant(row, "outcomeAt"),
                string(row, "executionMode"),
                string(row, "recordKind"),
                string(row, "evidenceCompleteness"));
    }

    private EvidenceRecord evidence(Map<String, Object> row) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (Set.of("reference", "sequence", "actor", "occurredAt").contains(entry.getKey())) continue;
            if ("facts".equals(entry.getKey()) || "decisionContext".equals(entry.getKey())) {
                values.putAll(safeJson(entry.getKey(), entry.getValue()));
            } else {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        return new EvidenceRecord(
                string(row, "reference"),
                row.get("sequence") == null ? null : number(row, "sequence"),
                string(row, "actor"),
                instant(row, "occurredAt"),
                values);
    }

    private Map<String, Object> safeJson(String source, Object raw) {
        if (raw == null) return Map.of(source, "Not recorded");
        try {
            JsonNode node = json.readTree(raw.toString());
            Set<String> allowed =
                    "facts".equals(source)
                            ? Set.of(
                                    "stage",
                                    "side",
                                    "price",
                                    "currency",
                                    "source",
                                    "providerTime",
                                    "receivedAt",
                                    "stale",
                                    "usedForFill",
                                    "metadataTime")
                            : Set.of("schemaVersion", "policyVersion", "quoteReference", "ruleCode");
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields()
                    .forEachRemaining(
                            entry -> {
                                if (allowed.contains(entry.getKey()) && entry.getValue().isValueNode()) {
                                    result.put(entry.getKey(), scalar(entry.getValue()));
                                }
                            });
            if (result.isEmpty()) result.put(source, "Redacted or unavailable");
            return result;
        } catch (JsonProcessingException failure) {
            return Map.of(source, "Redacted or unavailable");
        }
    }

    private static Object scalar(JsonNode node) {
        if (node.isNumber()) return node.asText();
        if (node.isBoolean()) return node.booleanValue();
        return node.asText();
    }

    private Map<String, Object> parsePolicy(Object raw) {
        if (raw == null) return null;
        try {
            JsonNode node = json.readTree(raw.toString());
            Map<String, Object> result = new LinkedHashMap<>();
            node.fields()
                    .forEachRemaining(
                            entry -> {
                                if (POLICY_FIELDS.contains(entry.getKey()) && entry.getValue().isValueNode()) {
                                    result.put(entry.getKey(), scalar(entry.getValue()));
                                }
                            });
            return result;
        } catch (JsonProcessingException failure) {
            return Map.of("status", "UNAVAILABLE");
        }
    }

    private Map<String, Object> currentPolicy(Map<String, Object> source) {
        Map<String, Object> policy = db.currentPolicy(integer(source, "instrumentId"));
        if (policy == null) return null;
        return new LinkedHashMap<>(policy);
    }

    private static EvidenceSection find(List<EvidenceSection> sections, String name) {
        return sections.stream()
                .filter(section -> section.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String completeness(String section, List<EvidenceRecord> items, long total) {
        if (total == 0) return "NOT_EVALUATED";
        if ("decisions".equals(section)
                && items.stream()
                        .allMatch(
                                item ->
                                        item.values().containsKey("decisionContext")
                                                && !"Not recorded"
                                                        .equals(
                                                                item.values()
                                                                        .get("decisionContext")))) {
            return "PASS";
        }
        if ("events".equals(section)) return "PASS";
        return "NOT_EVALUATED";
    }

    private static String coverage(String section) {
        return switch (section) {
            case "events" -> "Recorded lifecycle transitions retained";
            case "decisions" -> "Acceptance decisions and policy references retained";
            case "quotes" -> "Provider observations retained";
            case "attempts" -> "Execution attempts retained";
            case "reservations" -> "Reservation outcomes retained";
            case "fills" -> "Booked fills retained";
            case "postings" -> "Linked internal cash and position postings retained";
            case "notifications" -> "Retained notification facts; external delivery not asserted";
            default -> "Fixed evidence projection";
        };
    }

    private static String source(String section) {
        return switch (section) {
            case "events" -> "order_events";
            case "decisions" -> "order_rule_decisions";
            case "quotes" -> "order_quote_observations";
            case "attempts" -> "order_execution_attempts";
            case "reservations" -> "order_reservations";
            case "fills" -> "order_fills";
            case "postings" -> "cash_journal/position_journal";
            case "notifications" -> "outbox_events";
            default -> "retained_evidence";
        };
    }

    private static void validateSection(String section) {
        if (!ComplianceEvidenceMapper.SECTIONS.contains(section)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown evidence section");
        }
    }

    private static int integer(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Missing numeric database field: " + key);
        }
        return number.intValue();
    }

    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Missing numeric database field: " + key);
        }
        return number.longValue();
    }

    private static int integer(String value, int min, int max) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination or order ID");
        }
    }

    private static boolean present(Map<String, String> input, String key) {
        return input.get(key) != null && !input.get(key).isBlank();
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Compliance data source is unavailable");
    }
}
