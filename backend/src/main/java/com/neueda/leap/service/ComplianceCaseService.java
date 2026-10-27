package com.neueda.leap.service;

import static com.neueda.leap.service.SupportCaseService.*;
import static com.neueda.leap.service.SupportWorkflowService.*;
import static com.neueda.leap.trading.TradingData.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.mapper.*;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

@Service
public class ComplianceCaseService {
    private static final Map<String, List<String>> TRANSITIONS =
            Map.of(
                    "QUEUED",
                    List.of("IN_REVIEW"),
                    "IN_REVIEW",
                    List.of("NEEDS_INFORMATION", "DECIDED"),
                    "NEEDS_INFORMATION",
                    List.of("IN_REVIEW"),
                    "DECIDED",
                    List.of("CLOSED", "IN_REVIEW"),
                    "CLOSED",
                    List.of("IN_REVIEW"));
    private final SupportWorkflowService workflow;
    private final SupportWorkflowMapper workflows;
    private final SupportCaseMapper publicCases;
    private final ComplianceCaseMapper db;
    private final ComplianceAccessMapper grants;
    private final ComplianceAccessService access;
    private final Clock clock;

    public ComplianceCaseService(
            SupportWorkflowService workflow,
            SupportWorkflowMapper workflows,
            SupportCaseMapper publicCases,
            ComplianceCaseMapper db,
            ComplianceAccessMapper grants,
            ComplianceAccessService access,
            Clock clock) {
        this.workflow = workflow;
        this.workflows = workflows;
        this.publicCases = publicCases;
        this.db = db;
        this.grants = grants;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public Object reviewers(String reference) {
        var p = workflow.authorize(reference, true);
        var rows = db.reviewers(p);
        workflow.casesAudit(p, "CASE_REVIEWERS_READ", reference, rows.size());
        return map("items", rows, "limit", 100);
    }

    @Transactional
    public Object refer(String reference, String key, JsonNode body) {
        var p = workflow.authorize(reference, true);
        key(key);
        fields(body, Set.of("expectedWorkflowVersion", "reviewer", "reasonCode", "summary"));
        long expected = positive(body, "expectedWorkflowVersion");
        p.putAll(
                map(
                        "key",
                        key,
                        "hash",
                        OperationsAccessService.digest("referral:" + workflow.encode(body))));
        var w = workflow.workflow(p, true);
        var prior = workflows.request(p);
        if (prior != null) {
            if (!Objects.equals(prior.get("request_hash"), p.get("hash")))
                throw problem(
                        HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Request key already used");
            workflow.casesAudit(p, "CASE_REFERRAL_RECOVERED", reference, 1);
            return workflow.decode(string(prior, "receipt"));
        }
        if (expected != number(w, "version"))
            throw problem(HttpStatus.CONFLICT, "WORKFLOW_CONFLICT", "Workflow changed");
        if ("RESOLVED".equals(workflow.caseRow(p).get("status")))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_CONFLICT",
                    "Reopen the case before referring it");
        p.put("assignee", w.get("assignee"));
        if (p.get("assignee") == null || !workflows.eligible(p))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_INCOMPLETE",
                    "Assign an eligible Operations owner first");
        p.put("reviewer", positiveInt(body.path("reviewer")));
        if (!db.eligible(p))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_CONFLICT",
                    "Reviewer lacks independent eligible Compliance grants");
        String reason = text(body, "reasonCode", 1, 24, false);
        if (!Set.of("POLICY", "INTEGRITY", "ELIGIBILITY", "PROVENANCE").contains(reason))
            throw error(HttpStatus.BAD_REQUEST, "Invalid referral reason");
        UUID id = UUID.randomUUID();
        p.putAll(
                map(
                        "investigation",
                        id,
                        "reviewReference",
                        "REVIEW-" + id,
                        "referral",
                        UUID.randomUUID(),
                        "order",
                        workflow.caseRow(p).get("order_id"),
                        "reasonCode",
                        reason,
                        "summary",
                        text(body, "summary", 10, 2000, true),
                        "reviewVersion",
                        1,
                        "reviewKind",
                        "CREATED",
                        "reviewFacts",
                        workflow.encode(
                                map(
                                        "reasonCode",
                                        reason,
                                        "reviewer",
                                        p.get("reviewer"),
                                        "sourceReference",
                                        reference))));
        db.create(p);
        db.link(p);
        db.event(p);
        p.putAll(
                map(
                        "workflowVersion",
                        expected + 1,
                        "category",
                        w.get("effective_category"),
                        "priority",
                        w.get("priority"),
                        "nextAction",
                        w.get("next_action"),
                        "dueAt",
                        instant(w, "due_at"),
                        "revision",
                        w.get("checklist_revision"),
                        "kind",
                        "REFERRAL_CREATED",
                        "facts",
                        workflow.encode(
                                map(
                                        "reference",
                                        p.get("reviewReference"),
                                        "reviewer",
                                        p.get("reviewer")))));
        workflows.advance(p);
        workflows.event(p);
        var receipt =
                map(
                        "reference",
                        reference,
                        "investigationReference",
                        p.get("reviewReference"),
                        "workflowVersion",
                        expected + 1,
                        "status",
                        "QUEUED");
        p.put("receipt", workflow.encode(receipt));
        workflows.receipt(p);
        workflow.casesAudit(p, "CASE_REFERRAL_CREATED", reference, 1);
        return receipt;
    }

    Map<String, Object> identity(String permission) {
        workflow.enabled();
        int actor = access.actor();
        if (!access.policy().configured())
            throw error(
                    HttpStatus.SERVICE_UNAVAILABLE, "Compliance access policy is not configured");
        var now = clock.instant();
        var identities = grants.grants(actor, permission, now);
        if (identities.isEmpty()) {
            access.deny(
                    new ComplianceAccessService.Access(actor, null, permission, "NONE", now),
                    "INVESTIGATION",
                    null);
            throw error(
                    HttpStatus.FORBIDDEN,
                    "Independent Compliance investigation permission required");
        }
        return map("actor", actor, "permission", permission, "now", now);
    }

    Map<String, Object> authorize(String reference, boolean write) {
        var p = identity(write ? "INVESTIGATION_UPDATE" : "INVESTIGATION_READ");
        p.put("reference", reference);
        var c = db.detail(p);
        if (c == null
                || !grants.has(
                        integer(p, "actor"),
                        "INVESTIGATION_READ",
                        integer(c, "client_id"),
                        clock.instant())) missing(p);
        if (write) {
            publicCases.lockOwner(integer(c, "client_id"));
            db.lockCase((UUID) c.get("case_id"));
            workflows.lock((UUID) c.get("case_id"));
            c = db.lock(p);
            if (c == null) missing(p);
        }
        p.putAll(
                map(
                        "investigation",
                        c.get("investigation_id"),
                        "id",
                        c.get("case_id"),
                        "client",
                        c.get("client_id"),
                        "case",
                        c));
        return p;
    }

    void missing(Map<String, Object> p) {
        access.notFound(
                new ComplianceAccessService.Access(
                        integer(p, "actor"),
                        null,
                        string(p, "permission"),
                        "CASE",
                        (java.time.Instant) p.get("now")),
                null);
    }

    void audit(Map<String, Object> p, String action, int count) {
        access.success(
                new ComplianceAccessService.Access(
                        integer(p, "actor"),
                        null,
                        string(p, "permission"),
                        "CASE",
                        (java.time.Instant) p.get("now")),
                action,
                "INVESTIGATION",
                string(p, "reference"),
                "",
                count,
                null);
    }

    Map<String, Object> summary(Map<String, Object> c) {
        return map(
                "reference",
                c.get("reference"),
                "status",
                c.get("status"),
                "version",
                c.get("version"),
                "reviewer",
                c.get("reviewer"),
                "reasonCode",
                c.get("reason_code"),
                "summary",
                c.get("summary"),
                "orderId",
                c.get("order_id"),
                "createdAt",
                c.get("created_at"),
                "updatedAt",
                c.get("updated_at"));
    }

    @Transactional
    public Object list(String status, int page) {
        var p = identity("INVESTIGATION_READ");
        if (status != null && !TRANSITIONS.containsKey(status))
            throw error(HttpStatus.BAD_REQUEST, "Invalid review status");
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "Invalid page");
        p.putAll(map("status", status, "offset", page * 20));
        var rows = db.list(p).stream().map(this::summary).toList();
        long total = db.count(p);
        audit(p, "INVESTIGATION_LIST", rows.size());
        return map("items", rows, "total", total, "page", page, "pageSize", 20);
    }

    @Transactional
    public Object detail(String reference, int page) {
        var p = authorize(reference, false);
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "Invalid page");
        p.put("offset", page * 20);
        var c = workflow.caseRow(p);
        var reviewerParameters = new HashMap<>(p);
        reviewerParameters.put("reviewer", c.get("reviewer"));
        var transitions = db.eligible(reviewerParameters)
                ? TRANSITIONS.get(string(c, "status")).stream()
                    .filter(target -> !target.equals("DECIDED")
                            || integer(c, "reviewer") == integer(p, "actor")).toList()
                : List.<String>of();
        var result = summary(c);
        result.putAll(
                map(
                        "disposition",
                        c.get("disposition"),
                        "findings",
                        c.get("findings"),
                        "clientSafeExplanation",
                        c.get("client_safe_explanation"),
                        "history",
                        db.history(p),
                        "historyPage",
                        page,
                        "reviewers",
                        db.reviewers(p),
                        "actions",
                        grants.has(
                                        integer(p, "actor"),
                                        "INVESTIGATION_UPDATE",
                                        integer(p, "client"),
                                        clock.instant())
                                ? map(
                                        "assign",
                                        true,
                                        "transitions",
                                        transitions)
                                : map("assign", false, "transitions", List.of())));
        audit(p, "INVESTIGATION_READ", 1);
        return result;
    }

    @Transactional
    public Object command(String reference, String key, JsonNode body) {
        var p = authorize(reference, true);
        key(key);
        fields(
                body,
                Set.of(
                        "expectedVersion",
                        "action",
                        "reviewer",
                        "status",
                        "reason",
                        "disposition",
                        "clientSafeExplanation"));
        long expected = positive(body, "expectedVersion");
        String action = text(body, "action", 1, 24, false),
                reason = text(body, "reason", 10, 4000, true);
        if (!Set.of("ASSIGN", "TRANSITION").contains(action))
            throw error(HttpStatus.BAD_REQUEST, "Invalid review action");
        p.putAll(map("key", key, "hash", OperationsAccessService.digest(workflow.encode(body))));
        var prior = db.request(p);
        if (prior != null) {
            if (!Objects.equals(prior.get("request_hash"), p.get("hash")))
                throw problem(
                        HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Request key already used");
            audit(p, "INVESTIGATION_RECOVERED", 1);
            return workflow.decode(string(prior, "receipt"));
        }
        var c = workflow.caseRow(p);
        if (expected != number(c, "version"))
            throw problem(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Investigation changed");
        p.putAll(
                map(
                        "reviewVersion",
                        expected + 1,
                        "status",
                        c.get("status"),
                        "reviewer",
                        c.get("reviewer"),
                        "disposition",
                        c.get("disposition"),
                        "findings",
                        c.get("findings"),
                        "clientSafeExplanation",
                        c.get("client_safe_explanation"),
                        "reviewKind",
                        action));
        if (action.equals("ASSIGN")) {
            if (body.has("status") || body.has("disposition") || body.has("clientSafeExplanation"))
                throw error(HttpStatus.BAD_REQUEST, "Assignment cannot change review conclusions");
            p.put("reviewer", positiveInt(body.path("reviewer")));
        } else {
            if (body.has("reviewer"))
                throw error(HttpStatus.BAD_REQUEST, "Use an explicit assignment action");
            String target = text(body, "status", 1, 24, false);
            if (!TRANSITIONS.get(string(c, "status")).contains(target))
                throw problem(
                        HttpStatus.CONFLICT,
                        "VERSION_CONFLICT",
                        "Invalid investigation transition");
            p.put("status", target);
            if (target.equals("DECIDED")) {
                if (integer(c, "reviewer") != integer(p, "actor"))
                    throw error(
                            HttpStatus.FORBIDDEN, "Only the assigned eligible reviewer may decide");
                p.put("disposition", text(body, "disposition", 3, 100, false));
                p.put("findings", reason);
                p.put(
                        "clientSafeExplanation",
                        body.hasNonNull("clientSafeExplanation")
                                ? text(body, "clientSafeExplanation", 10, 4000, true)
                                : null);
            } else if (body.has("disposition") || body.has("clientSafeExplanation"))
                throw error(HttpStatus.BAD_REQUEST, "Conclusions require a decision transition");
        }
        if (!db.eligible(p))
            throw problem(
                    HttpStatus.CONFLICT,
                    "WORKFLOW_CONFLICT",
                    "Assigned reviewer is no longer eligible");
        p.put(
                "reviewFacts",
                workflow.encode(
                        map(
                                "reason",
                                reason,
                                "reviewer",
                                p.get("reviewer"),
                                "status",
                                p.get("status"),
                                "disposition",
                                p.get("disposition"),
                                "clientSafeExplanation",
                                p.get("clientSafeExplanation"))));
        db.advance(p);
        db.event(p);
        var receipt =
                map(
                        "reference",
                        reference,
                        "version",
                        expected + 1,
                        "status",
                        p.get("status"),
                        "reviewer",
                        p.get("reviewer"));
        p.put("receipt", workflow.encode(receipt));
        db.receipt(p);
        audit(p, "INVESTIGATION_" + action, 1);
        return receipt;
    }

    @Transactional
    public Object recover(String reference, String key) {
        var p = authorize(reference, true);
        key(key);
        p.put("key", key);
        var prior = db.request(p);
        if (prior == null) missing(p);
        audit(p, "INVESTIGATION_RECOVERED", 1);
        return workflow.decode(string(prior, "receipt"));
    }
}
