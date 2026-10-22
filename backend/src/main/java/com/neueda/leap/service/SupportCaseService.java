package com.neueda.leap.service;

import static com.neueda.leap.trading.TradingData.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.mapper.OperationsAccessMapper;
import com.neueda.leap.mapper.SupportCaseMapper;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

@Service
public class SupportCaseService {
    static final Set<String> CATEGORIES =
            Set.of(
                    "ORDER_QUESTION",
                    "ACCOUNT_REVIEW",
                    "HISTORICAL_RECORD",
                    "TRADING_AVAILABILITY",
                    "OTHER");
    static final Set<String> STATES = Set.of("OPEN", "IN_REVIEW", "AWAITING_CLIENT", "RESOLVED");
    private final SupportCaseMapper db;
    private final OperationsAccessMapper grants;
    private final OperationsAccessService access;
    private final AppUserService users;
    private final SessionService sessions;
    private final Clock clock;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public SupportCaseService(
            SupportCaseMapper db,
            OperationsAccessMapper grants,
            OperationsAccessService access,
            AppUserService users,
            SessionService sessions,
            Clock clock,
            com.fasterxml.jackson.databind.ObjectMapper json) {
        this.db = db;
        this.grants = grants;
        this.access = access;
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
        this.json = json;
    }

    Map<String, Object> identity(boolean staff, String permission) {
        int actor;
        Integer client = null;
        if (staff) {
            actor = access.actor();
            if (grants.grants(actor, permission, clock.instant()).isEmpty()) {
                access.deny(auditAccess(actor), "CASE", null);
                throw error(HttpStatus.FORBIDDEN, "Case permission unavailable");
            }
        } else {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof Jwt jwt)
                    || jwt.getExpiresAt() == null
                    || !jwt.getExpiresAt().isAfter(clock.instant())
                    || !sessions.valid(jwt))
                throw error(HttpStatus.UNAUTHORIZED, "Valid client session required");
            var user =
                    jwt.getClaim("userId") instanceof Number id
                            ? users.getUser(id.intValue())
                            : users.getUserByUsername(jwt.getSubject());
            if (user == null
                    || !Boolean.TRUE.equals(user.getEnabled())
                    || user.getRoles() == null
                    || !user.getRoles().contains("CLIENT")
                    || user.getClientId() == null)
                throw error(HttpStatus.FORBIDDEN, "Client account required");
            actor = user.getUserId();
            client = user.getClientId();
        }
        return map(
                "actor",
                actor,
                "client",
                client,
                "staff",
                staff,
                "permission",
                permission,
                "now",
                clock.instant());
    }

    OperationsAccessService.Access auditAccess(int actor) {
        return new OperationsAccessService.Access(actor, null, "CASE", "CASE", clock.instant());
    }

    void audit(Map<String, Object> p, String action, String reference, int count) {
        access.success(
                auditAccess(integer(p, "actor")), action, "CASE", reference, "", count, null);
    }

    void missing(Map<String, Object> p) {
        access.deny(auditAccess(integer(p, "actor")), "CASE", null);
        throw error(HttpStatus.NOT_FOUND, "Case not found");
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    static void fields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject())
            throw error(HttpStatus.BAD_REQUEST, "Object required");
        body.fieldNames()
                .forEachRemaining(
                        name -> {
                            if (!allowed.contains(name))
                                throw error(HttpStatus.BAD_REQUEST, "Unsupported case field");
                        });
    }

    static String text(JsonNode body, String field, int min, int max, boolean multiline) {
        var value = body.get(field);
        if (value == null || !value.isTextual())
            throw error(HttpStatus.BAD_REQUEST, "Invalid " + field);
        String result = value.asText().strip();
        if (result.length() < min
                || result.length() > max
                || value.asText()
                        .chars()
                        .anyMatch(
                                c ->
                                        Character.isISOControl(c)
                                                && !(multiline && (c == '\n' || c == '\t'))))
            throw error(HttpStatus.BAD_REQUEST, "Invalid " + field);
        return result;
    }

    static void key(String key) {
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,128}"))
            throw error(HttpStatus.BAD_REQUEST, "A stable request key is required");
    }

    Map<String, Object> summary(Map<String, Object> c) {
        return map(
                "reference",
                c.get("reference"),
                "orderId",
                c.get("order_id"),
                "category",
                c.get("category"),
                "subject",
                c.get("subject"),
                "status",
                c.get("status"),
                "version",
                c.get("version"),
                "createdAt",
                c.get("created_at"),
                "updatedAt",
                c.get("updated_at"),
                "detailUrl",
                "/app/support/" + c.get("reference"));
    }

    @Transactional
    public Map<String, Object> create(String key, JsonNode body) {
        var p = identity(false, "CASE_READ");
        key(key);
        fields(body, Set.of("category", "subject", "description", "orderId"));
        String category = text(body, "category", 1, 40, false),
                subject = text(body, "subject", 5, 120, false),
                description = text(body, "description", 10, 4000, true);
        if (!CATEGORIES.contains(category)) throw error(HttpStatus.BAD_REQUEST, "Invalid category");
        Integer order = null;
        if (body.hasNonNull("orderId")) {
            if (!body.get("orderId").isIntegralNumber()
                    || !body.get("orderId").canConvertToInt()
                    || body.get("orderId").asInt() < 1)
                throw error(HttpStatus.BAD_REQUEST, "Invalid order reference");
            order = body.get("orderId").asInt();
        }
        String hash =
                OperationsAccessService.digest(
                        category.length()
                                + ":"
                                + category
                                + subject.length()
                                + ":"
                                + subject
                                + description.length()
                                + ":"
                                + description
                                + ":"
                                + order);
        p.putAll(
                map(
                        "key",
                        key,
                        "hash",
                        hash,
                        "order",
                        order,
                        "category",
                        category,
                        "subject",
                        subject));
        db.lockOwner(integer(p, "client"));
        if (order != null && !db.ownedOrder(order, integer(p, "client"))) missing(p);
        var existing = db.byRequest(p);
        if (existing != null) {
            if (!hash.equals(existing.get("request_hash")))
                throw error(HttpStatus.CONFLICT, "Request key already used for different content");
            audit(p, "CASE_RECOVERED", string(existing, "reference"), 1);
            return summary(existing);
        }
        UUID id = UUID.randomUUID();
        String reference = "CASE-" + UUID.randomUUID();
        p.putAll(
                map(
                        "id",
                        id,
                        "reference",
                        reference,
                        "version",
                        1,
                        "kind",
                        "CREATED",
                        "role",
                        "CLIENT",
                        "body",
                        description,
                        "previous",
                        null,
                        "status",
                        "OPEN"));
        db.create(p);
        db.event(p);
        audit(p, "CASE_CREATED", reference, 1);
        return summary(db.byRequest(p));
    }

    @Transactional
    public Map<String, Object> byRequest(String key) {
        var p = identity(false, "CASE_READ");
        key(key);
        p.put("key", key);
        var c = db.byRequest(p);
        if (c == null) missing(p);
        if (c.get("order_id") != null
                && !db.ownedOrder(integer(c, "order_id"), integer(p, "client"))) missing(p);
        audit(p, "CASE_RECOVERED", string(c, "reference"), 1);
        return summary(c);
    }

    void page(Map<String, Object> p, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw error(HttpStatus.BAD_REQUEST, "Invalid page");
        p.put("limit", size);
        p.put("offset", (long) page * size);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> list(
            boolean staff, String status, String search, int page, int size) {
        var p = identity(staff, "CASE_READ");
        page(p, page, size);
        if (status != null && !STATES.contains(status))
            throw error(HttpStatus.BAD_REQUEST, "Invalid case status");
        if (search == null
                || search.length() > 120
                || search.chars().anyMatch(Character::isISOControl))
            throw error(HttpStatus.BAD_REQUEST, "Invalid search");
        p.putAll(map("status", status, "search", search.strip()));
        var rows = db.list(p).stream().map(this::summary).toList();
        long total = db.count(p);
        audit(p, "CASE_LIST", null, rows.size());
        return map("items", rows, "total", total, "page", page, "pageSize", size);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> detail(boolean staff, String reference, int page, int size) {
        var p = identity(staff, "CASE_READ");
        page(p, page, size);
        p.put("reference", reference);
        var c = db.detail(p);
        if (c == null) missing(p);
        var result = summary(c);
        result.put("events", db.events(p));
        result.put("page", page);
        result.put("pageSize", size);
        result.put("totalEvents", c.get("version"));
        audit(p, "CASE_READ", reference, 1);
        return result;
    }

    @Transactional
    public Map<String, Object> write(
            boolean staff, String reference, String action, String key, JsonNode body) {
        var p = identity(staff, staff ? "CASE_UPDATE" : "CASE_READ");
        key(key);
        fields(
                body,
                action.equals("TRANSITION")
                        ? Set.of("body", "expectedVersion", "status")
                        : Set.of("body", "expectedVersion"));
        String prose = text(body, "body", 10, 4000, true);
        if (!body.path("expectedVersion").isIntegralNumber()
                || !body.path("expectedVersion").canConvertToLong()
                || body.path("expectedVersion").asLong() < 1)
            throw error(HttpStatus.BAD_REQUEST, "Expected case version required");
        long expected = body.path("expectedVersion").asLong();
        String target = action.equals("TRANSITION") ? text(body, "status", 1, 24, false) : null;
        String hash =
                OperationsAccessService.digest(
                        action + ":" + expected + ":" + target + ":" + prose);
        p.putAll(map("reference", reference, "key", key, "hash", hash, "body", prose));
        var visible = db.detail(p);
        if (visible == null) missing(p);
        if (!action.equals("NOTE")) db.lockOwner(integer(visible, "client_id"));
        var c = db.lock(p);
        if (c == null) missing(p);
        if (staff
                && !access.has(
                        auditAccess(integer(p, "actor")), "CASE_READ", integer(c, "client_id")))
            missing(p);
        p.putAll(map("id", c.get("case_id"), "client", c.get("client_id")));
        var prior = db.request(p);
        if (prior != null) {
            if (!hash.equals(prior.get("request_hash")))
                throw error(HttpStatus.CONFLICT, "Request key already used for different content");
            audit(p, "CASE_WRITE_RECOVERED", reference, 1);
            try {
                return json.readValue(
                        string(prior, "receipt"),
                        new com.fasterxml.jackson.core.type.TypeReference<
                                Map<String, Object>>() {});
            } catch (java.io.IOException failure) {
                throw error(HttpStatus.SERVICE_UNAVAILABLE, "Retained receipt unavailable");
            }
        }
        if (expected != number(c, "version"))
            throw error(
                    HttpStatus.CONFLICT, "Case changed. Refresh before submitting new activity");
        String previous = string(c, "status"), next = previous;
        Map<String, Object> receipt;
        if (action.equals("NOTE") && staff) {
            p.put("note", UUID.randomUUID());
            db.note(p);
            receipt = map("reference", reference, "recorded", true);
        } else {
            if (action.equals("REOPEN") && !staff) {
                if (!previous.equals("RESOLVED"))
                    throw error(HttpStatus.CONFLICT, "Only resolved cases can be reopened");
                next = "IN_REVIEW";
            } else if (action.equals("TRANSITION") && staff) {
                Set<String> allowed =
                        switch (previous) {
                            case "OPEN" -> Set.of("IN_REVIEW", "AWAITING_CLIENT", "RESOLVED");
                            case "IN_REVIEW" -> Set.of("AWAITING_CLIENT", "RESOLVED");
                            case "AWAITING_CLIENT" -> Set.of("IN_REVIEW", "RESOLVED");
                            default -> Set.of();
                        };
                if (!allowed.contains(target))
                    throw error(HttpStatus.CONFLICT, "Invalid case transition");
                next = target;
            } else if (action.equals("REPLY")) {
                if (previous.equals("RESOLVED"))
                    throw error(HttpStatus.CONFLICT, "Reopen the resolved case before replying");
                if (!staff && previous.equals("AWAITING_CLIENT")) next = "IN_REVIEW";
            } else throw error(HttpStatus.BAD_REQUEST, "Unsupported case action");
            long version = number(c, "version") + 1;
            p.putAll(
                    map(
                            "version",
                            version,
                            "status",
                            next,
                            "previous",
                            previous,
                            "role",
                            staff ? "OPERATIONS" : "CLIENT",
                            "kind",
                            action));
            db.advance(p);
            db.event(p);
            receipt = map("reference", reference, "version", version, "status", next);
        }
        try {
            p.put("receipt", json.writeValueAsString(receipt));
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException(failure);
        }
        db.saveRequest(p);
        audit(p, "CASE_" + action, reference, 1);
        return receipt;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> notes(String reference, int page, int size) {
        var p = identity(true, "CASE_READ");
        page(p, page, size);
        p.put("reference", reference);
        if (db.detail(p) == null) missing(p);
        var rows = db.notes(p);
        long count = db.noteCount(p);
        audit(p, "CASE_NOTES_READ", reference, rows.size());
        return map("items", rows, "total", count, "page", page, "pageSize", size);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> updates(long cursor) {
        var p = identity(false, "CASE_READ");
        if (cursor < 0) throw error(HttpStatus.BAD_REQUEST, "Invalid update cursor");
        long latest = db.latest(integer(p, "client"));
        boolean reset = cursor > latest;
        p.put("cursor", reset ? 0 : cursor);
        if (cursor > 0 && !reset && !db.ownedEvent(p))
            throw error(HttpStatus.BAD_REQUEST, "Invalid update cursor");
        var rows = db.updates(p);
        var items = rows.stream().limit(100).toList();
        long next =
                items.isEmpty() ? (reset ? 0 : cursor) : number(items.get(items.size() - 1), "id");
        long unread = db.unread(p);
        audit(p, "CASE_UPDATES", null, items.size());
        return map(
                "items",
                items,
                "cursor",
                next,
                "hasMore",
                rows.size() > 100,
                "reset",
                reset,
                "unread",
                unread);
    }

    @Transactional
    public Map<String, Object> markRead(JsonNode body) {
        var p = identity(false, "CASE_READ");
        fields(body, Set.of("cursor"));
        if (!body.path("cursor").isIntegralNumber()
                || !body.path("cursor").canConvertToLong()
                || body.path("cursor").asLong() < 0)
            throw error(HttpStatus.BAD_REQUEST, "Invalid update cursor");
        long cursor = body.path("cursor").asLong();
        p.put("cursor", cursor);
        if (cursor != 0 && !db.ownedEvent(p)) missing(p);
        db.markRead(p);
        audit(p, "CASE_UPDATES_READ", null, 0);
        return map("unread", db.unread(p));
    }

    @Transactional
    public Map<String, Object> recoverWrite(boolean staff, String reference, String key) {
        var p = identity(staff, "CASE_READ");
        key(key);
        p.put("reference", reference);
        var c = db.detail(p);
        if (c == null) missing(p);
        p.putAll(map("id", c.get("case_id"), "key", key));
        var receipt = db.request(p);
        if (receipt == null) missing(p);
        audit(p, "CASE_WRITE_RECOVERED", reference, 1);
        try {
            return json.readValue(
                    string(receipt, "receipt"),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (java.io.IOException failure) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Retained receipt unavailable");
        }
    }
}
