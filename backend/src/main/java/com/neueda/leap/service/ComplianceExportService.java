package com.neueda.leap.service;

import static com.neueda.leap.trading.TradingData.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.ComplianceDtos.*;
import com.neueda.leap.exception.WorkflowException;
import com.neueda.leap.mapper.ComplianceExportMapper;
import com.neueda.leap.service.ComplianceAccessService.Access;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.zip.*;

@Service
public class ComplianceExportService {
    private final ComplianceAccessService access;
    private final ComplianceEvidenceService evidence;
    private final ComplianceExportMapper db;
    private final ObjectMapper json;
    private final Clock clock;
    private final int lifetime;

    public ComplianceExportService(
            ComplianceAccessService access,
            ComplianceEvidenceService evidence,
            ComplianceExportMapper db,
            ObjectMapper json,
            Clock clock,
            @Value("${compliance.export-seconds:0}") int lifetime) {
        this.access = access;
        this.evidence = evidence;
        this.db = db;
        this.json = json;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public ExportRecord create(UUID context, String key, ExportRequest request) {
        Access grant = access.require(context, "EXPORT_CREATE");
        requireExportConfiguration();
        validateKey(key);
        if (request == null || request.orderId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order ID must be positive");
        }
        try {
            db.lockKey(grant.actor(), key);
        } catch (DataAccessException failure) {
            throw unavailable();
        }
        String previous;
        try {
            previous = db.existing(grant.actor(), key);
        } catch (DataAccessException failure) {
            throw unavailable();
        }
        if (previous != null) {
            UUID previousId = parseReference(previous);
            Map<String, Object> metadata = metadata(previousId);
            if (metadata == null || integer(metadata, "orderId") != request.orderId()) {
                throw new WorkflowException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_CONFLICT",
                        "Idempotency key belongs to another request");
            }
            authorizeExport(grant, metadata, previousId);
            ExportRecord replay = record(metadata, grant);
            access.success(
                    grant, "EXPORT_REPLAY", "EXPORT", previous, key, 1, parseReference(previous));
            return replay;
        }

        ComplianceEvidenceService.ExportSnapshot snapshot =
                evidence.snapshotForExport(context, request.orderId());
        if (snapshot.detail().sections().stream().anyMatch(EvidenceSection::hasMore)) {
            throw new WorkflowException(
                    HttpStatus.CONFLICT,
                    "EVIDENCE_LIMIT",
                    "Evidence exceeds the single-case export limit; use paginated review");
        }
        byte[] evidenceBytes = encode(snapshot.detail());
        Map<String, Integer> sectionCounts = new LinkedHashMap<>();
        for (EvidenceSection section : snapshot.detail().sections()) {
            sectionCounts.put(section.name(), section.items().size());
        }
        String contentDigest = ComplianceAccessService.digest(evidenceBytes);
        Map<String, Object> manifest =
                map(
                        "schemaVersion",
                        "compliance-v1",
                        "sourceSnapshot",
                        snapshot.detail().snapshotAt(),
                        "sectionCounts",
                        sectionCounts,
                        "contentDigest",
                        contentDigest,
                        "signatureStatus",
                        "NOT_SUPPLIED",
                        "anchorStatus",
                        "NOT_SUPPLIED",
                        "verification",
                        "PASS",
                        "integrityNote",
                        "Digest checks content consistency only; no independent signature or anchor"
                                + " is configured.");
        byte[] manifestBytes = encode(manifest);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("evidence.json", evidenceBytes);
        files.put("manifest.json", manifestBytes);
        files.put(
                "limitations.txt",
                String.join("\n", snapshot.detail().limitations())
                        .getBytes(StandardCharsets.UTF_8));
        byte[] archive = zip(files);
        requireArchiveLimit(archive);
        Instant now = clock.instant();
        Instant expires = now.plusSeconds(lifetime);
        UUID exportId = UUID.randomUUID();
        String digest = ComplianceAccessService.digest(archive);
        try {
            db.save(
                    map(
                            "id",
                            exportId,
                            "actor",
                            grant.actor(),
                            "context",
                            context,
                            "order",
                            request.orderId(),
                            "key",
                            key,
                            "hash",
                            ComplianceAccessService.digest(
                                    request.orderId()
                                            + "|"
                                            + grant.scopeVersion()
                                            + "|"
                                            + snapshot.detail().snapshotAt()),
                            "scope",
                            snapshot.scopeVersion(),
                            "snapshot",
                            snapshot.detail().snapshotAt(),
                            "created",
                            now,
                            "expires",
                            expires,
                            "state",
                            "READY",
                            "digest",
                            digest,
                            "manifest",
                            jsonText(manifest),
                            "archive",
                            archive));
        } catch (DataAccessException failure) {
            throw unavailable();
        }
        if (grant.investigation() != null)
            db.associate(
                    map(
                            "investigation",
                            grant.investigation(),
                            "id",
                            exportId,
                            "actor",
                            grant.actor(),
                            "now",
                            now));
        Map<String, Object> saved = metadata(exportId);
        ExportRecord result = record(saved, grant);
        access.success(grant, "EXPORT_CREATED", "EXPORT", exportId.toString(), key, 1, exportId);
        return result;
    }

    @Transactional
    public ExportPage history(UUID context, int page, int pageSize) {
        Access grant = access.require(context, "EVIDENCE_READ");
        int checkedPage = integer(page, 0, 1000);
        int checkedSize = integer(pageSize, 1, 100);
        Map<String, Object> parameters =
                map(
                        "actor",
                        grant.actor(),
                        "limit",
                        checkedSize,
                        "offset",
                        checkedPage * checkedSize,
                        "now",
                        grant.now());
        parameters.putAll(grant.parameters());
        List<ExportRecord> items;
        long total;
        try {
            items = db.history(parameters).stream().map(row -> record(row, grant)).toList();
            total = db.historyCount(parameters);
        } catch (DataAccessException failure) {
            throw unavailable();
        }
        access.success(
                grant,
                "EXPORT_HISTORY",
                "EXPORT",
                null,
                Integer.toString(checkedPage),
                items.size(),
                null);
        return new ExportPage(
                items, checkedPage, checkedSize, total, access.scopeLabel(), clock.instant());
    }

    @Transactional
    public ExportRecord receipt(UUID context, String requestReference) {
        Access grant = access.require(context, "EVIDENCE_READ");
        UUID id = parseReference(requestReference);
        Map<String, Object> row = metadata(id);
        authorizeExport(grant, row, id);
        ExportRecord result = record(row, grant);
        access.success(grant, "EXPORT_RECEIPT", "EXPORT", id.toString(), "", 1, id);
        return result;
    }

    @Transactional
    public byte[] download(UUID context, String requestReference) {
        Access grant = access.require(context, "EXPORT_DOWNLOAD");
        UUID id = parseReference(requestReference);
        Map<String, Object> row = metadata(id);
        authorizeExport(grant, row, id);
        Instant expires = instant(row, "expiresAt");
        if (expires == null || !expires.isAfter(clock.instant())) {
            throw new WorkflowException(HttpStatus.GONE, "EXPORT_EXPIRED", "Export has expired");
        }
        Map<String, Object> archive;
        try {
            archive = db.archive(id);
        } catch (DataAccessException failure) {
            throw unavailable();
        }
        if (archive == null || !(archive.get("archive") instanceof byte[] bytes)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Export artifact is unavailable");
        }
        String expected = string(row, "digest");
        if (!Objects.equals(expected, ComplianceAccessService.digest(bytes))) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Export integrity verification failed");
        }
        access.success(grant, "EXPORT_DOWNLOAD", "EXPORT", id.toString(), "", 1, id);
        return bytes;
    }

    static void requireArchiveLimit(byte[] archive) {
        if (archive.length > 5_000_000) {
            throw new WorkflowException(
                    HttpStatus.CONFLICT,
                    "ARCHIVE_LIMIT",
                    "Evidence exceeds the five megabyte archive limit");
        }
    }

    private void authorizeExport(Access grant, Map<String, Object> row, UUID id) {
        if (row == null || integer(row, "requester") != grant.actor()) {
            access.notFound(grant, id.toString());
        }
        int clientId = integer(row, "clientId");
        if ((grant.managedOrder() != null && grant.managedOrder() != integer(row, "orderId"))
                || (grant.investigation() != null
                        && !Objects.equals(grant.investigation(), row.get("investigationId")))
                || !access.has(grant, grant.permission(), clientId)
                || ("EXPORT_CREATE".equals(grant.permission())
                        && !access.has(grant, "EVIDENCE_READ", clientId))) {
            access.notFound(grant, id.toString());
        }
    }

    private Map<String, Object> metadata(UUID id) {
        Map<String, Object> row;
        try {
            row = db.metadata(id);
        } catch (DataAccessException failure) {
            throw unavailable();
        }
        if (row == null) return null;
        if (!row.containsKey("clientId")) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Export metadata is incomplete");
        }
        return row;
    }

    private ExportRecord record(Map<String, Object> row, Access grant) {
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Export not found");
        }
        int clientId = integer(row, "clientId");
        Instant expires = instant(row, "expiresAt");
        String state =
                expires != null && !expires.isAfter(clock.instant())
                        ? "EXPIRED"
                        : string(row, "state");
        List<String> actions = new ArrayList<>();
        if (!"EXPIRED".equals(state) && access.has(grant, "EXPORT_DOWNLOAD", clientId)) {
            actions.add("DOWNLOAD");
        }
        if (access.has(grant, "EVIDENCE_READ", clientId)) actions.add("RECOVER_RECEIPT");
        return new ExportRecord(
                string(row, "requestReference"),
                access.scopeLabel(),
                string(row, "actor"),
                instant(row, "createdAt"),
                instant(row, "asOf"),
                string(row, "purposeReference"),
                state,
                expires,
                manifest(row.get("manifest")),
                actions);
    }

    private Manifest manifest(Object raw) {
        if (raw == null) return null;
        try {
            Map<?, ?> value = json.readValue(raw.toString(), Map.class);
            Map<String, Integer> counts = new LinkedHashMap<>();
            Object rawCounts = value.get("sectionCounts");
            if (rawCounts instanceof Map<?, ?> map) {
                map.forEach(
                        (key, count) -> counts.put(key.toString(), ((Number) count).intValue()));
            }
            return new Manifest(
                    Objects.toString(value.get("schemaVersion"), ""),
                    Instant.parse(Objects.toString(value.get("sourceSnapshot"))),
                    counts,
                    Objects.toString(value.get("contentDigest"), ""),
                    Objects.toString(value.get("signatureStatus"), "NOT_SUPPLIED"),
                    Objects.toString(value.get("anchorStatus"), "NOT_SUPPLIED"),
                    Objects.toString(value.get("verification"), "UNKNOWN"));
        } catch (JsonProcessingException | DateTimeParseException | ClassCastException failure) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Export manifest is unavailable");
        }
    }

    private byte[] encode(Object value) {
        try {
            return json.writeValueAsBytes(value);
        } catch (JsonProcessingException failure) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Evidence serialization is unavailable");
        }
    }

    private static byte[] zip(Map<String, byte[]> files) {
        try (var output = new java.io.ByteArrayOutputStream();
                var zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (java.io.IOException failure) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Export archive is unavailable");
        }
    }

    private static UUID parseReference(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid export reference");
        }
    }

    private static void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._-]{8,80}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Use an 8–80 character idempotency key");
        }
    }

    private void requireExportConfiguration() {
        if (lifetime < 60 || lifetime > 86400) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Export expiry policy is not configured");
        }
    }

    private static int integer(int value, int min, int max) {
        if (value < min || value > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        }
        return value;
    }

    private static int integer(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Missing numeric export field: " + key);
        }
        return number.intValue();
    }

    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return Instant.parse(value.toString());
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private String jsonText(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Evidence serialization is unavailable");
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Compliance export store is unavailable");
    }
}
