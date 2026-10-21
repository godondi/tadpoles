package com.neueda.leap.service;

import com.neueda.leap.dto.OperationsDtos.*;

import java.math.BigDecimal;
import java.util.*;

/** Completeness describes retained links, never an inference from today's balances. */
public final class OperationsEvidenceChecks {
    private OperationsEvidenceChecks() {}

    public static List<Finding> check(OrderRow order, Map<String, EvidencePage> sections) {
        List<Finding> findings = new ArrayList<>();
        if (!"SELF_DIRECTED".equals(order.recordKind())) {
            findings.add(
                    new Finding(
                            "LEGACY_COVERAGE",
                            "Legacy submission timezone and internal booking evidence are"
                                    + " unavailable."));
            return findings;
        }
        sections.forEach(
                (name, page) -> {
                    if (page.hasMore())
                        findings.add(
                                new Finding(
                                        "SECTION_LIMIT",
                                        name
                                                + " exceeds the snapshot limit; inspect paginated"
                                                + " evidence."));
                });
        var events = sections.get("events").items();
        var decisions = sections.get("decisions").items();
        var fills = sections.get("fills").items();
        var reservations = sections.get("reservations").items();
        var postings = sections.get("postings").items();
        var quotes = sections.get("quotes").items();
        var notifications = sections.get("notifications").items();
        if (events.stream()
                .noneMatch(
                        e ->
                                order.status().equals(e.values().get("toState"))
                                        && Long.toString(order.version())
                                                .equals(
                                                        Objects.toString(
                                                                e.values().get("sequence")))))
            findings.add(
                    new Finding(
                            "STATE_EVENT_MISSING",
                            "No matching event for the current state/version."));
        long expected = 1;
        for (var event : events) {
            if (!Long.toString(expected++)
                    .equals(Objects.toString(event.values().get("sequence")))) {
                findings.add(
                        new Finding(
                                "EVENT_SEQUENCE_GAP", "Lifecycle sequences are not contiguous."));
                break;
            }
        }
        for (var event : events)
            if (notifications.stream().noneMatch(n -> n.reference().equals(event.reference()))) {
                findings.add(
                        new Finding(
                                "OUTBOX_FACT_MISSING",
                                "A lifecycle event has no corresponding retained notification"
                                        + " fact."));
                break;
            }
        if (decisions.stream().noneMatch(d -> "ADMISSION".equals(d.values().get("stage"))))
            findings.add(
                    new Finding(
                            "ADMISSION_DECISION_MISSING", "The admission decision is missing."));
        for (var decision : decisions) {
            Object quote = decision.values().get("quoteId");
            if (quote != null
                    && quotes.stream().noneMatch(q -> q.reference().equals(quote.toString())))
                findings.add(
                        new Finding(
                                "DECISION_QUOTE_MISSING",
                                "A retained decision references a missing quote."));
        }
        if ("FILLED".equals(order.status())) {
            if (fills.size() != 1)
                findings.add(new Finding("FILL_MISSING", "Expected exactly one internal fill."));
            if (reservations.stream().noneMatch(r -> "CONSUMED".equals(r.values().get("state"))))
                findings.add(
                        new Finding(
                                "RESERVATION_NOT_CONSUMED",
                                "The fill has no consumed reservation."));
            for (var fill : fills) {
                String quote = Objects.toString(fill.values().get("quoteId"));
                if (quotes.stream().noneMatch(q -> q.reference().equals(quote)))
                    findings.add(
                            new Finding(
                                    "FILL_QUOTE_MISSING",
                                    "The fill's execution quote is missing."));
                if (events.stream()
                        .noneMatch(
                                e ->
                                        fill.reference()
                                                        .equals(
                                                                Objects.toString(
                                                                        e.values().get("fillId")))
                                                && "FILLED".equals(e.values().get("toState"))))
                    findings.add(
                            new Finding(
                                    "FILL_EVENT_MISSING", "No terminal event links this fill."));
                BigDecimal gross = new BigDecimal(fill.values().get("gross").toString());
                BigDecimal quantity = new BigDecimal(fill.values().get("quantity").toString());
                for (String kind : List.of("CASH", "POSITION")) {
                    BigDecimal expectedDelta =
                            kind.equals("CASH")
                                    ? (order.side().equals("BUY") ? gross.negate() : gross)
                                    : (order.side().equals("BUY") ? quantity : quantity.negate());
                    long matching =
                            postings.stream()
                                    .filter(
                                            p ->
                                                    kind.equals(p.values().get("kind"))
                                                            && fill.reference()
                                                                    .equals(
                                                                            Objects.toString(
                                                                                    p.values()
                                                                                            .get(
                                                                                                    "fillId")))
                                                            && Objects.equals(
                                                                    fill.values()
                                                                            .get("postingGroup"),
                                                                    p.values().get("postingGroup"))
                                                            && new BigDecimal(
                                                                                    p.values()
                                                                                            .get(
                                                                                                    "delta")
                                                                                            .toString())
                                                                            .compareTo(
                                                                                    expectedDelta)
                                                                    == 0)
                                    .count();
                    if (matching != 1)
                        findings.add(
                                new Finding(
                                        "POSTING_MISMATCH",
                                        kind
                                                + " signed delta or fill/posting-group linkage is"
                                                + " missing or inconsistent."));
                }
            }
            if (postings.size() != 2)
                findings.add(
                        new Finding("POSTING_COUNT", "Expected exactly two financial postings."));
        } else if ("REJECTED".equals(order.status())) {
            if (!fills.isEmpty() || !postings.isEmpty())
                findings.add(
                        new Finding(
                                "REJECTION_POSTED", "A rejected order has fill/posting evidence."));
            if (decisions.stream()
                    .noneMatch(
                            d ->
                                    Boolean.FALSE.equals(d.values().get("passed"))
                                            && Objects.equals(
                                                    order.rejectionStage(), d.values().get("stage"))
                                            && Objects.equals(
                                                    order.rejectionCode(), d.values().get("code"))))
                findings.add(
                        new Finding(
                                "REJECTION_DECISION_MISSING",
                                "No matching terminal rejection decision."));
            if (order.acceptedAt() != null
                    && reservations.stream()
                            .noneMatch(r -> "RELEASED".equals(r.values().get("state"))))
                findings.add(
                        new Finding(
                                "RESERVATION_NOT_RELEASED",
                                "An accepted rejection has no released reservation."));
            if (order.acceptedAt() == null && !reservations.isEmpty())
                findings.add(
                        new Finding(
                                "ADMISSION_RESERVATION",
                                "A never-accepted rejection unexpectedly has a reservation."));
        } else {
            if (reservations.stream().noneMatch(r -> "ACTIVE".equals(r.values().get("state"))))
                findings.add(
                        new Finding(
                                "ACTIVE_RESERVATION_MISSING",
                                "Pending work has no active reservation."));
            if (order.deadline() == null)
                findings.add(
                        new Finding("JOB_MISSING", "Pending work has no persisted execution job."));
        }
        return List.copyOf(findings);
    }
}
