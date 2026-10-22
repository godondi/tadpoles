package com.neueda.leap.controller;

import com.neueda.leap.dto.OperationsDtos.*;
import com.neueda.leap.service.*;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/operations")
@PreAuthorize("hasRole('TRADING_OPS') and !hasRole('CLIENT')")
public class OperationsOrderController {
    private final OperationsAccessService access;
    private final OperationsOrderService orders;
    private final OperationsExportService exports;
    private final OperationsReconciliationService reconciliation;

    public OperationsOrderController(
            OperationsAccessService access,
            OperationsOrderService orders,
            OperationsExportService exports,
            OperationsReconciliationService reconciliation) {
        this.access = access;
        this.orders = orders;
        this.exports = exports;
        this.reconciliation = reconciliation;
    }

    @GetMapping("/access-policy")
    public AccessPolicy policy() {
        return access.policy();
    }

    @PostMapping("/access-contexts")
    public AccessContext context(@RequestBody ContextRequest request) {
        return access.create(request);
    }

    @GetMapping({"/orders", "/exceptions"})
    public OrderPage orders(
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @RequestParam Map<String, String> filters) {
        return orders.search(context, filters);
    }

    @GetMapping("/orders/{id}")
    public Investigation detail(
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @PathVariable int id) {
        return orders.detail(context, id);
    }

    @GetMapping(
            "/orders/{id}/{section:events|decisions|quotes|attempts|reservations|fills|postings|notifications}")
    public EvidencePage section(
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @PathVariable int id,
            @PathVariable String section,
            @RequestParam(defaultValue = "0") int page) {
        return orders.section(context, id, section, page);
    }

    @PostMapping("/evidence-exports")
    public ExportReceipt createExport(
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody ExportRequest request) {
        return exports.create(context, key, request.orderId());
    }

    @GetMapping("/evidence-exports/{id}")
    public ExportReceipt export(
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @PathVariable UUID id) {
        return exports.status(context, id);
    }

    @GetMapping("/evidence-exports/{id}/download")
    public ResponseEntity<byte[]> download(
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"order-evidence-" + id + ".zip\"")
                .cacheControl(CacheControl.noStore())
                .body(exports.download(context, id));
    }

    @GetMapping("/reconciliations/latest")
    public ReconciliationView latest(
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @RequestParam(defaultValue = "0") int page) {
        return reconciliation.read(context, null, page);
    }

    @GetMapping({"/reconciliations/{id}", "/reconciliations/{id}/exceptions"})
    public ReconciliationView run(
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page) {
        return reconciliation.read(context, id, page);
    }

    @GetMapping("/migration-exceptions")
    public EvidencePage migration(
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @RequestParam(defaultValue = "0") int page) {
        return reconciliation.migration(context, page);
    }
}
