package com.neueda.leap.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Staff contracts deliberately exclude profiles, banking details and authentication material. */
public final class OperationsDtos {
    private OperationsDtos() {}

    public record ContextRequest(String purposeCode, String caseReference, String referenceType) {}

    public record AccessContext(
            UUID contextId,
            Instant expiresAt,
            String purposeCode,
            String referenceType,
            String caseReference) {}

    public record AccessPolicy(boolean configured, List<String> purposes, String policyVersion) {}

    public record OrderRow(
            int orderId,
            String accountReference,
            int instrumentId,
            String ticker,
            String side,
            String quantity,
            String currency,
            String recordKind,
            String executionMode,
            String status,
            Instant submittedAt,
            Instant acceptedAt,
            Instant updatedAt,
            long version,
            String rejectionStage,
            String rejectionCode,
            String reason,
            String evidenceStatus,
            String queueCategory,
            Instant deadline,
            Instant nextAttempt,
            Instant leaseUntil,
            int attempts) {}

    public record OrderPage(
            List<OrderRow> items, String nextCursor, boolean hasMore, Instant retrievedAt) {}

    public record EvidenceRow(String reference, String recordedAt, Map<String, Object> values) {}

    public record EvidencePage(List<EvidenceRow> items, int page, boolean hasMore) {}

    public record Finding(String code, String explanation) {}

    public record Investigation(
            OrderRow order,
            String submittedActor,
            Object acceptedPolicy,
            Object currentPolicy,
            Instant snapshotAt,
            String evidenceStatus,
            List<Finding> findings,
            Map<String, EvidencePage> sections,
            boolean exportAllowed,
            String orderingNote,
            String historicalContext) {}

    public record ExportRequest(int orderId) {}

    public record ExportReceipt(
            UUID exportId,
            String state,
            Instant snapshotAt,
            Instant expiresAt,
            long orderVersion,
            String digest) {}

    public record ReconciliationView(
            UUID runId,
            Instant checkedAt,
            String status,
            String coverage,
            List<EvidenceRow> items,
            boolean hasMore,
            int page) {}
}
