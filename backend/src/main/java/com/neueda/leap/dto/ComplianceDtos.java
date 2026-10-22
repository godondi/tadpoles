package com.neueda.leap.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit, redacted contracts for the Compliance workspace. */
public final class ComplianceDtos {
    private ComplianceDtos() {}

    public record AccessPolicy(
            boolean configured, List<String> purposes, String policyVersion, String scopeLabel) {}

    public record ContextRequest(String purposeCode, String caseReference) {}

    public record AccessContext(
            UUID contextId,
            String purposeCode,
            String caseReference,
            String scopeLabel,
            List<String> permissions,
            Instant expiresAt,
            String policyVersion) {}

    public record OrderSummary(
            int orderId,
            String clientReference,
            String instrument,
            String side,
            String quantity,
            String unit,
            String status,
            Instant submittedAt,
            Instant acceptedAt,
            Instant outcomeAt,
            String executionMode,
            String recordKind,
            String evidenceCompleteness) {}

    public record SearchPage(
            List<OrderSummary> items,
            int page,
            int pageSize,
            long total,
            String scope,
            Instant asOf) {}

    public record EvidenceRecord(
            String reference,
            Long sequence,
            String actor,
            Instant occurredAt,
            Map<String, Object> values) {}

    public record EvidenceSection(
            String name,
            List<EvidenceRecord> items,
            int page,
            int pageSize,
            long total,
            boolean hasMore,
            String coverage,
            String source,
            Instant asOf,
            String completeness) {}

    public record Finding(String code, String explanation, String status) {}

    public record Investigation(
            OrderSummary summary,
            String authenticatedActor,
            String accountReference,
            Map<String, Object> acceptedPolicy,
            Map<String, Object> currentPolicy,
            Instant snapshotAt,
            List<EvidenceSection> sections,
            List<Finding> findings,
            boolean exportAllowed,
            List<String> limitations) {}

    public record ExportRecord(
            String requestReference,
            String scope,
            String actor,
            Instant createdAt,
            Instant asOf,
            String purposeReference,
            String state,
            Instant expiresAt,
            Manifest manifest,
            List<String> authorizedActions) {}

    public record ExportPage(
            List<ExportRecord> items,
            int page,
            int pageSize,
            long total,
            String scope,
            Instant asOf) {}

    public record ExportRequest(int orderId) {}

    public record Manifest(
            String schemaVersion,
            Instant sourceSnapshot,
            Map<String, Integer> sectionCounts,
            String contentDigest,
            String signatureStatus,
            String anchorStatus,
            String verification) {}
}
