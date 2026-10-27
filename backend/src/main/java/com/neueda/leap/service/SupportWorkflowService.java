package com.neueda.leap.service;

import static com.neueda.leap.service.SupportCaseService.*;
import static com.neueda.leap.trading.TradingData.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.neueda.leap.exception.WorkflowException;
import com.neueda.leap.mapper.*;
import com.neueda.leap.service.support.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SupportWorkflowService {
    private final SupportCaseService cases;
    private final SupportCaseMapper publicCases;
    private final SupportWorkflowMapper db;
    private final SupportCategoryRegistry categories;
    private final OperationsAccessService access;
    private final OperationsAccessMapper contexts;
    private final OperationsOrderService orders;
    private final TradingMapper trading;
    private final ProviderHealthService provider;
    private final ObjectMapper json;

    public SupportWorkflowService(
            SupportCaseService cases,
            SupportCaseMapper publicCases,
            SupportWorkflowMapper db,
            SupportCategoryRegistry categories,
            OperationsAccessService access,
            OperationsOrderService orders,
            TradingMapper trading,
            ProviderHealthService provider,
            ObjectMapper json,
            OperationsAccessMapper contexts) {
        this.cases = cases;
        this.publicCases = publicCases;
        this.db = db;
        this.categories = categories;
        this.access = access;
        this.orders = orders;
        this.trading = trading;
        this.provider = provider;
        this.json = json;
        this.contexts = contexts;
    }

    void enabled() {
        if (!categories.enabled() || !db.ready())
            throw problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WORKFLOW_DISABLED",
                    "Support workflow is not enabled or ready");
    }

    Map<String, Object> authorize(String reference, boolean write) {
        enabled();
        var p = cases.identity(true, write ? "CASE_UPDATE" : "CASE_READ");
        p.put("reference", reference);
        var c = publicCases.detail(p);
        if (c == null || !has(p, "CASE_READ", integer(c, "client_id"))) cases.missing(p);
        if (write) {
            publicCases.lockOwner(integer(c, "client_id"));
            c = publicCases.lock(p);
            if (c == null) cases.missing(p);
        }
        p.putAll(map("id", c.get("case_id"), "client", c.get("client_id"), "case", c));
        return p;
    }

    boolean has(Map<String, Object> p, String permission, int client) {
        return access.has(cases.auditAccess(integer(p, "actor")), permission, client);
    }

    Map<String, Object> workflow(Map<String, Object> p, boolean lock) {
        var result = lock ? db.lock((UUID) p.get("id")) : db.workflow((UUID) p.get("id"));
        if (result == null)
            throw problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WORKFLOW_DISABLED",
                    "Case workflow is unavailable");
        return result;
    }

    List<Map<String, Object>> tasks(Map<String, Object> p, Map<String, Object> w) {
        var saved = db.tasks((UUID) p.get("id"), number(w, "checklist_revision"));
        Map<String, Map<String, Object>> byCode = new HashMap<>();
        saved.forEach(t -> byCode.put(string(t, "code"), t));
        return categories.definition(string(w, "effective_category")).tasks().stream()
                .map(
                        t ->
                                byCode.getOrDefault(
                                        t.code(),
                                        map(
                                                "code", t.code(), "label", t.label(), "state",
                                                "PENDING")))
                .toList();
    }

    @Transactional
    public Map<String, Object> queue(
            String status,
            String search,
            int page,
            int size,
            Integer assignee,
            String priority,
            String category,
            String view,
            boolean overdueOnly) {
        if (!categories.enabled()) {
            if (assignee != null
                    || priority != null
                    || category != null
                    || view != null
                    || overdueOnly)
                throw problem(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "WORKFLOW_DISABLED",
                        "Private queue filters require the enabled workflow");
            var legacy = cases.list(true, status, search, page, size);
            legacy.put("workflowEnabled", false);
            return legacy;
        }
        enabled();
        var p = cases.identity(true, "CASE_READ");
        cases.page(p, page, size);
        String queueView = view == null ? (status == null ? "ACTIONABLE" : "ALL") : view;
        if ((status != null && !STATES.contains(status))
                || search == null
                || search.length() > 120
                || search.chars().anyMatch(Character::isISOControl)
                || (assignee != null && assignee < 1)
                || (priority != null && !Set.of("LOW", "NORMAL", "HIGH").contains(priority))
                || !Set.of(
                                "ACTIONABLE",
                                "ALL",
                                "UNASSIGNED",
                                "MINE",
                                "AWAITING_CLIENT",
                                "AWAITING_REVIEW",
                                "RESOLVED")
                        .contains(queueView))
            throw error(HttpStatus.BAD_REQUEST, "Invalid private queue filter");
        if (category != null) categories.definition(category);
        p.putAll(
                map(
                        "status",
                        status,
                        "search",
                        search.strip(),
                        "assignee",
                        assignee,
                        "priority",
                        priority,
                        "category",
                        category,
                        "view",
                        queueView,
                        "overdueOnly",
                        overdueOnly));
        var items =
                db.queue(p).stream()
                        .map(
                                c -> {
                                    var row = cases.summary(c);
                                    row.putAll(
                                            map(
                                                    "assignee",
                                                    c.get("assignee"),
                                                    "assigneeLabel",
                                                    c.get("assignee_label"),
                                                    "priority",
                                                    c.get("priority"),
                                                    "nextAction",
                                                    c.get("next_action"),
                                                    "dueAt",
                                                    c.get("due_at"),
                                                    "effectiveCategory",
                                                    c.get("effective_category"),
                                                    "workflowVersion",
                                                    c.get("workflow_version")));
                                    return row;
                                })
                        .toList();
        long total = db.queueCount(p);
        cases.audit(p, "CASE_WORKFLOW_QUEUE", null, items.size());
        return map(
                "items",
                items,
                "total",
                total,
                "page",
                page,
                "pageSize",
                size,
                "workflowEnabled",
                true);
    }

    @Transactional
    public Map<String, Object> read(String reference, int page) {
        var p = authorize(reference, false);
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "Invalid workflow page");
        var c = caseRow(p);
        var w = workflow(p, false);
        boolean update = has(p, "CASE_UPDATE", integer(p, "client"));
        String status = string(c, "status");
        var transitions =
                update
                        ? switch (status) {
                            case "OPEN" -> List.of("IN_REVIEW", "AWAITING_CLIENT");
                            case "IN_REVIEW" -> List.of("AWAITING_CLIENT");
                            case "AWAITING_CLIENT" -> List.of("IN_REVIEW");
                            default -> List.of();
                        }
                        : List.of();
        var checklist = tasks(p, w);
        var result =
                map(
                        "reference",
                        reference,
                        "publicVersion",
                        c.get("version"),
                        "status",
                        status,
                        "originalCategory",
                        c.get("category"),
                        "effectiveCategory",
                        w.get("effective_category"),
                        "workflowVersion",
                        w.get("version"),
                        "checklistRevision",
                        w.get("checklist_revision"),
                        "assignee",
                        w.get("assignee"),
                        "priority",
                        w.get("priority"),
                        "nextAction",
                        w.get("next_action"),
                        "dueAt",
                        w.get("due_at"),
                        "updatedAt",
                        w.get("updated_at"),
                        "responseTarget",
                        "Response target not configured",
                        "intake",
                        decode(db.intakeJson((UUID) p.get("id"))),
                        "tasks",
                        checklist,
                        "definitions",
                        categories.definition(string(w, "effective_category")),
                        "actions",
                        map(
                                "reply",
                                update && !status.equals("RESOLVED"),
                                "note",
                                update,
                                "transitions",
                                transitions,
                                "workflow",
                                update && !status.equals("RESOLVED"),
                                "resolve",
                                update && !status.equals("RESOLVED")),
                        "diagnosticsAllowed",
                        map(
                                "account",
                                has(p, "ACCOUNT_DIAGNOSTICS", integer(p, "client")),
                                "historical",
                                has(p, "RECONCILIATION_READ", integer(p, "client")),
                                "order",
                                has(p, "EVIDENCE_READ", integer(p, "client"))),
                        "referrals",
                        db.referrals((UUID) p.get("id")),
                        "mandatoryReferralPending",
                        db.pendingReferral((UUID) p.get("id")),
                        "history",
                        db.history((UUID) p.get("id"), page * 20),
                        "historyPage",
                        page);
        cases.audit(p, "CASE_WORKBENCH_READ", reference, 1);
        return result;
    }

    @Transactional
    public Map<String, Object> assignees(String reference) {
        var p = authorize(reference, false);
        var rows = db.assignees(p);
        cases.audit(p, "CASE_ASSIGNEES_READ", reference, rows.size());
        return map("items", rows, "limit", 100);
    }

    @Transactional
    public Map<String, Object> exports(String reference, int page) {
        var p = authorize(reference, false);
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "Invalid export page");
        var rows = db.exports((UUID) p.get("id"), page * 20);
        cases.audit(p, "CASE_EXPORTS_READ", reference, Math.min(20, rows.size()));
        return map(
                "items",
                rows.stream().limit(20).toList(),
                "page",
                page,
                "hasMore",
                rows.size() > 20,
                "note",
                "Metadata only. Download still requires the original role, requester, valid context"
                        + " and live evidence grants.");
    }

    @Transactional
    public Map<String, Object> diagnostics(
            String reference, String kind, String evidence, UUID context) {
        var p = authorize(reference, false);
        int client = integer(p, "client");
        if (evidence == null) {
            var intake = decode(db.intakeJson((UUID) p.get("id")));
            Object retained =
                    switch (kind) {
                        case "account" -> intake.get("movementId");
                        case "historical" -> intake.get("exceptionId");
                        case "availability" -> intake.get("instrumentId");
                        case "order" ->
                                caseRow(p).get("order_id") != null
                                        ? caseRow(p).get("order_id")
                                        : intake.get("relatedOrderId");
                        default -> null;
                    };
            evidence = Objects.toString(retained, null);
        }
        Map<String, Object> result;
        switch (kind) {
            case "account" -> {
                require(p, "ACCOUNT_DIAGNOSTICS");
                var positions = db.positions(client);
                var pending = db.pendingOrders(client);
                result =
                        map(
                                "account",
                                db.account(client),
                                "positions",
                                positions.stream().limit(50).toList(),
                                "positionsTruncated",
                                positions.size() > 50,
                                "pendingOrders",
                                pending.stream().limit(50).toList(),
                                "pendingOrdersTruncated",
                                pending.size() > 50);
                if (evidence != null) {
                    UUID movement = uuid(evidence);
                    if (!db.ownedMovement(movement, client)) cases.missing(p);
                    result.put("movement", db.movement(client, movement));
                }
            }
            case "historical" -> {
                require(p, "RECONCILIATION_READ");
                long id = positiveReference(evidence);
                result = db.historical(client, id);
                if (result == null) cases.missing(p);
                result.put("facts", decode(string(result, "facts")));
                result.put(
                        "completeness",
                        "Retained source snapshot only; completeness is not established");
                result.put("currency", "Unknown unless explicitly recorded; no USD inferred");
                result.put(
                        "liveTradingEffect",
                        "Historical quarantined facts do not establish usable funds or positions");
            }
            case "order" -> {
                int id = positiveInt(evidence);
                if (!publicCases.ownedOrder(id, client) || !matchesOrder(caseRow(p), p, id))
                    cases.missing(p);
                var grant = access.require(context, "EVIDENCE_READ");
                if (!access.has(grant, "EVIDENCE_READ", client)
                        || grant.managedClient() == null
                        || grant.managedClient() != client
                        || !Objects.equals(
                                contexts.binding(context).get("managed_case_id"), p.get("id")))
                    cases.missing(p);
                result =
                        map(
                                "evidence",
                                orders.detail(context, id),
                                "note",
                                "Internal execution evidence is not proof of external venue"
                                        + " execution or settlement.");
            }
            case "availability" -> {
                require(p, "ACCOUNT_DIAGNOSTICS");
                Integer instrument = evidence == null ? null : positiveInt(evidence);
                if (instrument != null && !db.instrument(instrument)) cases.missing(p);
                var policy = instrument == null ? null : trading.policy(instrument);
                result =
                        map(
                                "account",
                                db.account(client),
                                "policyStatus",
                                instrument == null
                                        ? "NOT_APPLICABLE: account-wide diagnostic; no instrument"
                                                + " reported"
                                        : policy == null
                                                ? "UNAVAILABLE: no current instrument policy"
                                                : "CURRENT",
                                "policy",
                                policy == null
                                        ? null
                                        : map(
                                                "instrumentId",
                                                policy.instrumentId(),
                                                "version",
                                                policy.version(),
                                                "active",
                                                policy.active(),
                                                "enabled",
                                                policy.enabled(),
                                                "buyAllowed",
                                                policy.buyAllowed(),
                                                "sellAllowed",
                                                policy.sellAllowed(),
                                                "session",
                                                policy.session(),
                                                "currency",
                                                policy.currency(),
                                                "quoteMaxAgeSeconds",
                                                policy.quoteMaxAgeSeconds()),
                                "provider",
                                provider.health(),
                                "quoteEligibility",
                                "UNKNOWN: no diagnostic quote or test trade was requested",
                                "scope",
                                "Current controls, not a reconstruction of the reported historical"
                                        + " attempt");
            }
            default -> throw error(HttpStatus.BAD_REQUEST, "Unknown diagnostic kind");
        }
        result.put("observedAt", p.get("now"));
        cases.audit(p, "CASE_DIAGNOSTICS_READ", reference, 1);
        return result;
    }

    @Transactional
    public Map<String, Object> command(String reference, String action, String key, JsonNode body) {
        var p = authorize(reference, true);
        key(key);
        Set<String> allowed = new HashSet<>(Set.of("expectedWorkflowVersion"));
        allowed.addAll(
                switch (action) {
                    case "assignment" -> Set.of("assignee");
                    case "classification" -> Set.of("category", "reason");
                    case "priority" -> Set.of("priority", "reason");
                    case "next-action" -> Set.of("nextAction", "dueAt");
                    case "tasks" ->
                            Set.of(
                                    "code",
                                    "state",
                                    "reason",
                                    "evidenceKind",
                                    "evidenceReference",
                                    "contextId");
                    case "resolution" -> Set.of("code", "body", "expectedVersion", "contextId");
                    default -> throw error(HttpStatus.BAD_REQUEST, "Unknown workflow action");
                });
        fields(body, allowed);
        long expected = positive(body, "expectedWorkflowVersion");
        p.putAll(
                map(
                        "key",
                        key,
                        "hash",
                        OperationsAccessService.digest(action + ":" + encode(body)),
                        "kind",
                        action.toUpperCase(Locale.ROOT).replace('-', '_')));
        var w = workflow(p, true);
        var prior = db.request(p);
        if (prior != null) {
            if (!Objects.equals(prior.get("request_hash"), p.get("hash")))
                throw problem(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_CONFLICT",
                        "Request key already used for different content");
            cases.audit(p, "CASE_WORKFLOW_RECOVERED", reference, 1);
            return decode(string(prior, "receipt"));
        }
        if (expected != number(w, "version"))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_CONFLICT",
                    "Workflow changed; refresh before submitting a new command");
        if ("RESOLVED".equals(caseRow(p).get("status")))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_CONFLICT",
                    "Reopen the case before changing its workflow");
        p.putAll(
                map(
                        "workflowVersion",
                        expected + 1,
                        "category",
                        w.get("effective_category"),
                        "assignee",
                        w.get("assignee"),
                        "priority",
                        w.get("priority"),
                        "nextAction",
                        w.get("next_action"),
                        "dueAt",
                        instant(w, "due_at"),
                        "revision",
                        w.get("checklist_revision")));
        Map<String, Object> facts;
        switch (action) {
            case "assignment" -> {
                if (!body.has("assignee"))
                    throw error(HttpStatus.BAD_REQUEST, "Assignee is required");
                Integer assignee =
                        body.get("assignee").isNull() ? null : positiveInt(body.get("assignee"));
                p.put("assignee", assignee);
                if (assignee != null && !db.eligible(p))
                    throw problem(
                            HttpStatus.CONFLICT,
                            "WORKFLOW_CONFLICT",
                            "Assignee no longer has eligible account access");
                facts = map("assignee", assignee);
            }
            case "classification" -> {
                String category = text(body, "category", 1, 40, false),
                        reason = text(body, "reason", 10, 2000, true);
                categories.definition(category);
                if (category.equals(w.get("effective_category")))
                    throw problem(
                            HttpStatus.CONFLICT,
                            "WORKFLOW_CONFLICT",
                            "Case already has this effective category");
                p.put("category", category);
                p.put("revision", number(w, "checklist_revision") + 1);
                facts =
                        map(
                                "previousCategory",
                                w.get("effective_category"),
                                "category",
                                category,
                                "reason",
                                reason,
                                "revision",
                                p.get("revision"));
            }
            case "priority" -> {
                String priority = text(body, "priority", 1, 10, false),
                        reason = text(body, "reason", 10, 2000, true);
                if (!Set.of("LOW", "NORMAL", "HIGH").contains(priority))
                    throw error(HttpStatus.BAD_REQUEST, "Invalid priority");
                p.put("priority", priority);
                facts = map("priority", priority, "reason", reason);
            }
            case "next-action" -> {
                String next = text(body, "nextAction", 10, 500, true);
                Instant due = null;
                if (body.hasNonNull("dueAt")) {
                    try {
                        due = Instant.parse(text(body, "dueAt", 1, 40, false));
                    } catch (DateTimeParseException failure) {
                        throw error(HttpStatus.BAD_REQUEST, "Use an ISO UTC due time");
                    }
                    if (!due.isAfter((Instant) p.get("now")))
                        throw error(HttpStatus.BAD_REQUEST, "Due time must be in the future");
                }
                p.putAll(map("nextAction", next, "dueAt", due));
                facts = map("nextAction", next, "dueAt", due);
            }
            case "tasks" -> facts = task(p, w, body);
            case "resolution" -> facts = resolve(p, w, body);
            default -> throw new IllegalStateException("Unvalidated action");
        }
        p.put("facts", encode(facts));
        db.advance(p);
        db.event(p);
        var receipt =
                map(
                        "reference",
                        reference,
                        "workflowVersion",
                        p.get("workflowVersion"),
                        "action",
                        action,
                        "publicVersion",
                        caseRow(p).get("version"));
        if (action.equals("resolution")) receipt.put("publicVersion", p.get("publicVersion"));
        p.put("receipt", encode(receipt));
        db.receipt(p);
        cases.audit(p, "CASE_WORKFLOW_" + p.get("kind"), reference, 1);
        return receipt;
    }

    Map<String, Object> task(Map<String, Object> p, Map<String, Object> w, JsonNode body) {
        String code = text(body, "code", 1, 64, false),
                state = text(body, "state", 1, 24, false),
                reason = text(body, "reason", 10, 2000, true);
        var template =
                categories.definition(string(w, "effective_category")).tasks().stream()
                        .filter(t -> t.code().equals(code))
                        .findFirst()
                        .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Unknown checklist task"));
        if (!Set.of("PENDING", "COMPLETED", "UNAVAILABLE", "NOT_APPLICABLE").contains(state))
            throw error(HttpStatus.BAD_REQUEST, "Invalid checklist state");
        String
                kind =
                        body.hasNonNull("evidenceKind")
                                ? text(body, "evidenceKind", 1, 32, false)
                                : null,
                reference =
                        body.hasNonNull("evidenceReference")
                                ? text(body, "evidenceReference", 1, 200, false)
                                : null;
        if ((kind == null) != (reference == null))
            throw error(
                    HttpStatus.BAD_REQUEST,
                    "Evidence kind and reference must be supplied together");
        if (kind != null) verifyEvidence(p, kind, reference, context(body));
        if (state.equals("COMPLETED")) {
            String required =
                    switch (code) {
                        case "ORDER_EVIDENCE" -> "ORDER";
                        case "ACCOUNT_RESOURCES",
                                        "ACCOUNT_DEPENDENCIES",
                                        "AVAILABILITY_ACCOUNT",
                                        "AVAILABILITY_CONTROLS" ->
                                "ACCOUNT";
                        case "HISTORY_SOURCE", "HISTORY_PROVENANCE" -> "HISTORICAL";
                        default -> null;
                    };
            if (required != null && !required.equals(kind))
                throw problem(
                        HttpStatus.CONFLICT,
                        "WORKFLOW_INCOMPLETE",
                        "This task requires separately authorized " + required + " evidence");
        }
        p.putAll(
                map(
                        "code",
                        code,
                        "label",
                        template.label(),
                        "state",
                        state,
                        "reason",
                        reason,
                        "evidenceKind",
                        kind,
                        "evidenceReference",
                        reference));
        db.task(p);
        return map(
                "revision",
                p.get("revision"),
                "code",
                code,
                "state",
                state,
                "reason",
                reason,
                "evidenceKind",
                kind,
                "evidenceReference",
                reference);
    }

    Map<String, Object> resolve(Map<String, Object> p, Map<String, Object> w, JsonNode body) {
        var c = caseRow(p);
        long expected = positive(body, "expectedVersion");
        if (expected != number(c, "version"))
            throw problem(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Public case changed");
        if (p.get("assignee") == null || !db.eligible(p))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_INCOMPLETE",
                    "An eligible Operations owner is required");
        String code = text(body, "code", 1, 80, false), prose = text(body, "body", 10, 4000, true);
        if (!categories.definition(string(w, "effective_category")).resolutions().contains(code))
            throw error(HttpStatus.BAD_REQUEST, "Invalid category resolution");
        var checklist = tasks(p, w);
        boolean insufficient =
                code.equals("INSUFFICIENT_RETAINED_EVIDENCE")
                        || code.equals("DUPLICATE_NOT_CONFIRMED");
        if (code.equals("INSUFFICIENT_RETAINED_EVIDENCE")) require(p, "RECONCILIATION_READ");
        for (var task : checklist) {
            String state = string(task, "state"), taskCode = string(task, "code");
            if ("PENDING".equals(state) || ("UNAVAILABLE".equals(state) && !insufficient))
                throw problem(
                        HttpStatus.CONFLICT,
                        "WORKFLOW_INCOMPLETE",
                        "Finish the category checklist or retain an authorized"
                                + " insufficient-evidence disposition");
            if (Set.of(
                                    "ORDER_EVIDENCE",
                                    "ACCOUNT_RESOURCES",
                                    "ACCOUNT_DEPENDENCIES",
                                    "HISTORY_SOURCE",
                                    "HISTORY_PROVENANCE",
                                    "AVAILABILITY_ACCOUNT",
                                    "AVAILABILITY_CONTROLS")
                            .contains(taskCode)
                    && "NOT_APPLICABLE".equals(state)
                    && !insufficient)
                throw problem(
                        HttpStatus.CONFLICT,
                        "WORKFLOW_INCOMPLETE",
                        "Required source evidence cannot be marked not applicable for this"
                                + " disposition");
            if (task.get("evidenceKind") != null)
                verifyEvidence(
                        p,
                        string(task, "evidenceKind"),
                        string(task, "evidenceReference"),
                        context(body));
        }
        if (db.pendingReferral((UUID) p.get("id")))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_INCOMPLETE",
                    "Mandatory Compliance disposition is pending");
        if (code.equals("POLICY_CHANGE_VERIFIED")
                && checklist.stream().noneMatch(t -> "POLICY_CHANGE".equals(t.get("evidenceKind"))))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_INCOMPLETE",
                    "A separately audited policy change is required");
        if (code.equals("DUPLICATE_CASE_LINKED")
                && checklist.stream()
                        .noneMatch(t -> "DUPLICATE_CASE".equals(t.get("evidenceKind"))))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_INCOMPLETE",
                    "An independently authorized duplicate case is required");
        if (code.startsWith("REFERRED_")
                && checklist.stream()
                        .noneMatch(t -> "EXTERNAL_REFERENCE".equals(t.get("evidenceKind")))
                && db.referrals((UUID) p.get("id")).stream()
                        .noneMatch(r -> r.get("disposition") != null))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_INCOMPLETE",
                    "Retain an attributable specialist follow-up reference and owner");
        if (code.equals("CLIENT_SETUP_COMPLETED")) {
            require(p, "ACCOUNT_DIAGNOSTICS");
            if (!trading.onboarded(integer(p, "client")) || !trading.hasBank(integer(p, "client")))
                throw problem(
                        HttpStatus.CONFLICT,
                        "WORKFLOW_INCOMPLETE",
                        "Client onboarding or bank prerequisite is still unavailable");
        }
        if (Set.of("EXPLAINED_REJECTION", "SERVICE_RECOVERED").contains(code)) {
            var source =
                    checklist.stream()
                            .filter(t -> "ORDER".equals(t.get("evidenceKind")))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            problem(
                                                    HttpStatus.CONFLICT,
                                                    "WORKFLOW_INCOMPLETE",
                                                    "A separately authorized retained order outcome"
                                                            + " is required"));
            var order =
                    orders.detail(context(body), positiveInt(string(source, "evidenceReference")))
                            .order();
            String required = code.equals("EXPLAINED_REJECTION") ? "REJECTED" : "FILLED";
            if (!required.equals(order.status()))
                throw problem(
                        HttpStatus.CONFLICT,
                        "WORKFLOW_INCOMPLETE",
                        "The retained order does not establish the selected disposition");
            var intake = decode(db.intakeJson((UUID) p.get("id")));
            if (intake.get("instrumentId") instanceof Number instrument
                    && instrument.intValue() != order.instrumentId())
                throw problem(
                        HttpStatus.CONFLICT,
                        "WORKFLOW_INCOMPLETE",
                        "Recovery evidence must match the reported instrument");
        }
        p.putAll(
                map(
                        "publicVersion",
                        expected + 1,
                        "version",
                        expected + 1,
                        "previous",
                        c.get("status"),
                        "status",
                        "RESOLVED",
                        "role",
                        "OPERATIONS",
                        "body",
                        prose,
                        "resolution",
                        UUID.randomUUID(),
                        "code",
                        code,
                        "checklist",
                        encode(checklist)));
        publicCases.advance(p);
        String kind = string(p, "kind");
        p.put("kind", "TRANSITION");
        publicCases.event(p);
        p.put("kind", kind);
        db.resolution(p);
        return map("code", code, "publicVersion", expected + 1, "checklist", checklist);
    }

    void verifyEvidence(Map<String, Object> p, String kind, String reference, UUID context) {
        int client = integer(p, "client");
        switch (kind) {
            case "ACCOUNT" -> {
                require(p, "ACCOUNT_DIAGNOSTICS");
                if (!Integer.toString(client).equals(reference)
                        && !Objects.equals(p.get("reference"), reference)) cases.missing(p);
            }
            case "MOVEMENT" -> {
                require(p, "ACCOUNT_DIAGNOSTICS");
                if (!db.ownedMovement(uuid(reference), client)) cases.missing(p);
            }
            case "HISTORICAL" -> {
                require(p, "RECONCILIATION_READ");
                if (!db.ownedException(positiveReference(reference), client)) cases.missing(p);
            }
            case "ORDER" -> {
                int order = positiveInt(reference);
                if (!publicCases.ownedOrder(order, client) || !matchesOrder(caseRow(p), p, order))
                    cases.missing(p);
                var grant = access.require(context, "EVIDENCE_READ");
                if (grant.managedClient() == null
                        || grant.managedClient() != client
                        || !access.has(grant, "EVIDENCE_READ", client)
                        || !Objects.equals(
                                contexts.binding(context).get("managed_case_id"), p.get("id")))
                    cases.missing(p);
                orders.detail(context, order);
            }
            case "POLICY_CHANGE" -> {
                require(p, "RECONCILIATION_READ");
                var intake = decode(db.intakeJson((UUID) p.get("id")));
                if (!(intake.get("instrumentId") instanceof Number instrument)
                        || db.policyChange(uuid(reference), instrument.intValue()) == null)
                    cases.missing(p);
            }
            case "DUPLICATE_CASE" -> {
                var target = new HashMap<>(p);
                target.put("reference", reference);
                target.put("permission", "CASE_READ");
                var duplicate = publicCases.detail(target);
                if (duplicate == null || reference.equals(p.get("reference"))) cases.missing(p);
            }
            case "EXTERNAL_REFERENCE" -> {
                if (reference.length() < 8)
                    throw error(
                            HttpStatus.BAD_REQUEST,
                            "An attributable follow-up reference is required");
            }
            default -> throw error(HttpStatus.BAD_REQUEST, "Unknown evidence kind");
        }
    }

    boolean matchesOrder(Map<String, Object> c, Map<String, Object> p, int order) {
        if (c.get("order_id") != null) return integer(c, "order_id") == order;
        var intake = decode(db.intakeJson((UUID) p.get("id")));
        return !(intake.get("relatedOrderId") instanceof Number id) || id.intValue() == order;
    }

    void require(Map<String, Object> p, String permission) {
        if (!has(p, permission, integer(p, "client"))) {
            access.deny(cases.auditAccess(integer(p, "actor")), "CASE_DIAGNOSTICS", null);
            throw error(
                    HttpStatus.FORBIDDEN, "Independent " + permission + " permission is required");
        }
    }

    void casesAudit(Map<String, Object> p, String action, String reference, int count) {
        cases.audit(p, action, reference, count);
    }

    @Transactional
    public Map<String, Object> recover(String reference, String key) {
        var p = authorize(reference, true);
        key(key);
        p.put("key", key);
        workflow(p, true);
        var prior = db.request(p);
        if (prior == null) cases.missing(p);
        cases.audit(p, "CASE_WORKFLOW_RECOVERED", reference, 1);
        return decode(string(prior, "receipt"));
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> caseRow(Map<String, Object> p) {
        return (Map<String, Object>) p.get("case");
    }

    String encode(Object value) {
        try {
            Object canonical =
                    value instanceof JsonNode node ? json.convertValue(node, Object.class) : value;
            return json.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(canonical);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Workflow serialization failed", failure);
        }
    }

    Map<String, Object> decode(String value) {
        if (value == null) return Map.of();
        try {
            return json.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Retained workflow data is invalid", failure);
        }
    }

    static long positive(JsonNode body, String field) {
        var value = body.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1)
            throw error(HttpStatus.BAD_REQUEST, "Positive " + field + " is required");
        return value.longValue();
    }

    static int positiveInt(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1)
            throw error(HttpStatus.BAD_REQUEST, "Invalid numeric reference");
        return value.intValue();
    }

    static long positiveReference(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}"))
            throw error(HttpStatus.BAD_REQUEST, "Invalid evidence reference");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid evidence reference");
        }
    }

    static int positiveInt(String value) {
        long id = positiveReference(value);
        if (id > Integer.MAX_VALUE)
            throw error(HttpStatus.BAD_REQUEST, "Invalid evidence reference");
        return (int) id;
    }

    static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid UUID reference");
        }
    }

    static UUID context(JsonNode body) {
        return body.hasNonNull("contextId") ? uuid(text(body, "contextId", 36, 36, false)) : null;
    }

    static WorkflowException problem(HttpStatus status, String code, String message) {
        return new WorkflowException(status, code, message);
    }
}
