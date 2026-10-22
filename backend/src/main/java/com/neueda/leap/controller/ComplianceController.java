package com.neueda.leap.controller;

import com.neueda.leap.dto.ComplianceDtos.*;
import com.neueda.leap.service.*;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/compliance")
@PreAuthorize("hasRole('COMPLIANCE')")
public class ComplianceController {
    private final ComplianceAccessService access;
    private final ComplianceEvidenceService evidence;
    private final ComplianceExportService exports;

    public ComplianceController(
            ComplianceAccessService access,
            ComplianceEvidenceService evidence,
            ComplianceExportService exports) {
        this.access = access;
        this.evidence = evidence;
        this.exports = exports;
    }

    @GetMapping("/access-policy")
    public ResponseEntity<AccessPolicy> policy() {
        return privateResponse(access.policy());
    }

    @PostMapping("/access-contexts")
    public ResponseEntity<AccessContext> context(@RequestBody ContextRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(access.create(request));
    }

    @GetMapping("/investigations")
    public ResponseEntity<SearchPage> search(
            @RequestHeader(value = "X-Compliance-Context", required = false) String context,
            @RequestParam Map<String, String> filters) {
        return privateResponse(evidence.search(parseContext(context), filters));
    }

    @GetMapping("/orders/{id}/evidence")
    public ResponseEntity<Investigation> detail(
            @RequestHeader(value = "X-Compliance-Context", required = false) String context,
            @PathVariable int id) {
        return privateResponse(evidence.detail(parseContext(context), id));
    }

    @GetMapping("/orders/{id}/evidence/{section}")
    public ResponseEntity<EvidenceSection> section(
            @RequestHeader(value = "X-Compliance-Context", required = false) String context,
            @PathVariable int id,
            @PathVariable String section,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        return privateResponse(
                evidence.section(parseContext(context), id, section, page, pageSize));
    }

    @GetMapping("/evidence")
    public ResponseEntity<ExportPage> history(
            @RequestHeader(value = "X-Compliance-Context", required = false) String context,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        return privateResponse(
                exports.history(parseContext(context), page, pageSize));
    }

    @PostMapping("/evidence/exports")
    public ResponseEntity<ExportRecord> createExport(
            @RequestHeader(value = "X-Compliance-Context", required = false) String context,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ExportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(exports.create(parseContext(context), key, request));
    }

    @GetMapping("/evidence/exports/{requestReference}")
    public ResponseEntity<ExportRecord> receipt(
            @RequestHeader(value = "X-Compliance-Context", required = false) String context,
            @PathVariable String requestReference) {
        return privateResponse(exports.receipt(parseContext(context), requestReference));
    }

    @GetMapping("/evidence/exports/{requestReference}/download")
    public ResponseEntity<byte[]> download(
            @RequestHeader(value = "X-Compliance-Context", required = false) String context,
            @PathVariable String requestReference) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"compliance-evidence-" + safeFilename(requestReference) + ".zip\"")
                .body(exports.download(parseContext(context), requestReference));
    }

    private static UUID parseContext(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Compliance context");
        }
    }

    private static String safeFilename(String value) {
        return value.matches("[A-Za-z0-9-]{8,80}") ? value : "request";
    }

    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
