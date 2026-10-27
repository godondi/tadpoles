package com.neueda.leap.service;

import static com.neueda.leap.trading.TradingData.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.OperationsDtos.*;
import com.neueda.leap.mapper.OperationsExportMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.zip.*;

/**
 * Small synchronous cases are stored as immutable database artifacts, never in a public directory.
 */
@Service
public class OperationsExportService {
    private final OperationsAccessService access;
    private final OperationsOrderService orders;
    private final OperationsExportMapper db;
    private final ObjectMapper json;
    private final Clock clock;
    private final int lifetime;
    private final org.springframework.transaction.support.TransactionTemplate transaction;

    public OperationsExportService(
            OperationsAccessService access,
            OperationsOrderService orders,
            OperationsExportMapper db,
            ObjectMapper json,
            Clock clock,
            org.springframework.transaction.PlatformTransactionManager transactions,
            @Value("${operations.export-seconds:0}") int lifetime) {
        this.access = access;
        this.orders = orders;
        this.db = db;
        this.json = json;
        this.clock = clock;
        this.lifetime = lifetime;
        this.transaction =
                new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.transaction.setTimeout(5);
    }

    public ExportReceipt create(UUID context, String key, int orderId) {
        var grant = access.require(context, "EVIDENCE_EXPORT");
        if (lifetime < 60 || lifetime > 86400)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Export expiry policy is not configured");
        if (key == null || !key.matches("[A-Za-z0-9._-]{8,80}"))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Use an 8–80 character idempotency key");
        orders.authorizedOrder(grant, orderId);
        orders.authorizedOrder(access.require(context, "EVIDENCE_READ"), orderId);
        UUID previous = existing(grant.actor(), key);
        if (previous != null) {
            var existing = authorized(context, previous);
            if (((Number) existing.get("order_id")).intValue() != orderId)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Idempotency key belongs to another request");
            access.success(grant, "EXPORT_REPLAY", "EXPORT", previous.toString(), "", 1, previous);
            return receipt(existing);
        }
        var snapshot = orders.exportSnapshot(context, orderId);
        var evidence = snapshot.evidence();
        if (evidence.sections().values().stream().anyMatch(EvidencePage::hasMore)
                || snapshot.policyHistory().hasMore()
                || snapshot.reconciliation().hasMore())
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Evidence exceeds the single-case limit; use paginated investigation and"
                            + " escalate");
        UUID id = UUID.randomUUID();
        var files = new LinkedHashMap<String, byte[]>();
        String summary =
                "Order "
                        + orderId
                        + "\nInternal booking evidence\nStatus: "
                        + evidence.order().status()
                        + "\nCompleteness: "
                        + evidence.evidenceStatus()
                        + "\nSnapshot: "
                        + evidence.snapshotAt()
                        + "\n"
                        + "Exact decimals are strings. Legacy unavailable facts remain marked.\n"
                        + "This package is not proof of external exchange execution or custody"
                        + " settlement.\n"
                        + "Policy history is included. Related reconciliation is included only when"
                        + " separately entitled; coverage is explicit.\n";
        files.put("summary.txt", summary.getBytes(StandardCharsets.UTF_8));
        files.put("evidence.json", encode(evidence));
        files.put("policy-history.json", encode(snapshot.policyHistory()));
        files.put("reconciliation.json", encode(snapshot.reconciliation()));
        int evidenceRecords =
                evidence.sections().values().stream().mapToInt(page -> page.items().size()).sum();
        Map<String, Integer> recordCounts =
                Map.of(
                        "summary.txt",
                        1,
                        "evidence.json",
                        evidenceRecords,
                        "policy-history.json",
                        snapshot.policyHistory().items().size(),
                        "reconciliation.json",
                        snapshot.reconciliation().items().size());
        var entries = new ArrayList<Object>();
        files.forEach(
                (name, bytes) ->
                        entries.add(
                                map(
                                        "path",
                                        name,
                                        "bytes",
                                        bytes.length,
                                        "recordCount",
                                        recordCounts.get(name),
                                        "sha256",
                                        OperationsAccessService.digest(bytes))));
        int records = recordCounts.values().stream().mapToInt(Integer::intValue).sum();
        var manifest =
                map(
                        "exportId",
                        id,
                        "schemaVersion",
                        1,
                        "generatorVersion",
                        "operations-v1",
                        "generatedAt",
                        clock.instant(),
                        "contextId",
                        context,
                        "sourceOrderId",
                        orderId,
                        "orderVersion",
                        evidence.order().version(),
                        "snapshotAt",
                        evidence.snapshotAt(),
                        "classification",
                        "CONFIDENTIAL_OPERATIONS",
                        "redactionPolicyVersion",
                        "scoped-case-v1",
                        "recordCount",
                        records,
                        "files",
                        entries,
                        "digestAlgorithm",
                        "SHA-256",
                        "encoding",
                        "UTF-8",
                        "completeness",
                        evidence.findings(),
                        "integrityNote",
                        "Digests verify bytes against this manifest; no independent signature or"
                                + " anchor is configured.");
        files.put("manifest.json", encode(manifest));
        long uncompressedBytes = files.values().stream().mapToLong(bytes -> bytes.length).sum();
        if (uncompressedBytes > 50_000_000)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Case exceeds the uncompressed evidence limit");
        byte[] archive = zip(files);
        if (archive.length > 5_000_000)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Case exceeds the five megabyte archive limit");
        String digest = OperationsAccessService.digest(archive);
        Instant expires = clock.instant().plusSeconds(lifetime);
        return transaction.execute(
                status -> {
                    var current = access.require(context, "EVIDENCE_EXPORT");
                    orders.authorizedOrder(current, orderId);
                    orders.authorizedOrder(access.require(context, "EVIDENCE_READ"), orderId);
                    if (!access.exportScope(current).equals(snapshot.scopeVersion()))
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "Scope changed during export; start again");
                    db.lockKey(current.actor(), key);
                    UUID raced = existing(current.actor(), key);
                    if (raced != null) {
                        var existing = authorized(context, raced);
                        if (((Number) existing.get("order_id")).intValue() != orderId)
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Idempotency key belongs to another request");
                        access.success(
                                current, "EXPORT_REPLAY", "EXPORT", raced.toString(), "", 1, raced);
                        return receipt(existing);
                    }
                    db.save(
                            map(
                                    "id",
                                    id,
                                    "actor",
                                    grant.actor(),
                                    "context",
                                    context,
                                    "order",
                                    orderId,
                                    "key",
                                    key,
                                    "hash",
                                    OperationsAccessService.digest(orderId + "|" + context),
                                    "scope",
                                    snapshot.scopeVersion(),
                                    "snapshot",
                                    evidence.snapshotAt(),
                                    "expires",
                                    expires,
                                    "version",
                                    evidence.order().version(),
                                    "digest",
                                    digest,
                                    "archive",
                                    archive));
                    access.success(
                            grant,
                            "EXPORT_GENERATED",
                            "ORDER",
                            Integer.toString(orderId),
                            "single-order-json-zip",
                            records,
                            id);
                    return new ExportReceipt(
                            id,
                            "READY",
                            evidence.snapshotAt(),
                            expires,
                            evidence.order().version(),
                            digest);
                });
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public ExportReceipt status(UUID context, UUID id) {
        var value = authorized(context, id);
        var grant = access.require(context, "EVIDENCE_EXPORT");
        access.success(grant, "EXPORT_STATUS", "EXPORT", id.toString(), "", 1, id);
        return receipt(value);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public byte[] download(UUID context, UUID id) {
        var metadata = authorized(context, id);
        byte[] bytes = (byte[]) db.archive(id).get("archive");
        if (!OperationsAccessService.digest(bytes).equals(metadata.get("digest")))
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Stored export integrity check failed");
        var grant = access.require(context, "EVIDENCE_EXPORT");
        // Records authorization to transfer bytes, not a claim that the browser completed a
        // transfer.
        access.success(grant, "DOWNLOAD_AUTHORIZED", "EXPORT", id.toString(), "", 1, id);
        return bytes;
    }

    private UUID existing(int actor, String key) {
        String id = db.existing(actor, key);
        return id == null ? null : UUID.fromString(id);
    }

    private Map<String, Object> authorized(UUID context, UUID id) {
        var grant = access.require(context, "EVIDENCE_EXPORT");
        access.require(context, "EVIDENCE_READ");
        var value = db.metadata(id);
        if (value == null
                || !Objects.equals(value.get("context_id"), context)
                || ((Number) value.get("requester")).intValue() != grant.actor()
                || !access.exportScope(grant).equals(value.get("scope_version")))
            access.notFound(grant, id.toString());
        orders.authorizedOrder(grant, ((Number) value.get("order_id")).intValue());
        orders.authorizedOrder(
                access.require(context, "EVIDENCE_READ"),
                ((Number) value.get("order_id")).intValue());
        if (!time(value.get("expires_at")).isAfter(clock.instant())) {
            access.deny(grant, "EXPORT", id.toString());
            throw new ResponseStatusException(
                    HttpStatus.GONE, "Export expired; request a new package");
        }
        return value;
    }

    private ExportReceipt receipt(Map<String, Object> value) {
        return new ExportReceipt(
                (UUID) value.get("export_id"),
                "READY",
                time(value.get("snapshot_at")),
                time(value.get("expires_at")),
                ((Number) value.get("order_version")).longValue(),
                value.get("digest").toString());
    }

    private Instant time(Object value) {
        return value instanceof Timestamp t ? t.toInstant() : Instant.parse(value.toString());
    }

    private byte[] encode(Object value) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encode export", e);
        }
    }

    private byte[] zip(Map<String, byte[]> files) {
        try (var output = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var file : files.entrySet()) {
                var entry = new ZipEntry(file.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot materialize export", e);
        }
    }

    @Transactional
    public Map<String, Object> association(UUID context, UUID id) {
        var value = authorized(context, id);
        byte[] bytes = (byte[]) db.archive(id).get("archive");
        if (!OperationsAccessService.digest(bytes).equals(value.get("digest")))
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Stored export integrity check failed");
        return map(
                "exportId",
                id,
                "orderId",
                value.get("order_id"),
                "digest",
                value.get("digest"),
                "expiresAt",
                value.get("expires_at"));
    }
}
