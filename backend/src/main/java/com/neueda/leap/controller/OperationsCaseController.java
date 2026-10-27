package com.neueda.leap.controller;

import com.neueda.leap.service.SupportCaseService;
import com.neueda.leap.service.SupportWorkflowService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operations/cases")
@PreAuthorize("hasRole('TRADING_OPS') and !hasRole('CLIENT')")
public class OperationsCaseController {
    private final SupportCaseService cases;
    private final SupportWorkflowService workflow;

    public OperationsCaseController(SupportCaseService cases, SupportWorkflowService workflow) {
        this.cases = cases;
        this.workflow = workflow;
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer assignee,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String view,
            @RequestParam(defaultValue = "false") boolean overdueOnly) {
        return workflow.queue(
                status, search, page, pageSize, assignee, priority, category, view, overdueOnly);
    }

    @GetMapping("/{reference}")
    public Object detail(
            @PathVariable String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return cases.detail(true, reference, page, pageSize);
    }

    @PostMapping("/{reference}/replies")
    public Object replies(
            @PathVariable String reference,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody com.fasterxml.jackson.databind.JsonNode body) {
        return cases.write(true, reference, "REPLY", key, body);
    }

    @PostMapping("/{reference}/notes")
    public Object notes(
            @PathVariable String reference,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody com.fasterxml.jackson.databind.JsonNode body) {
        return cases.write(true, reference, "NOTE", key, body);
    }

    @PostMapping("/{reference}/transitions")
    public Object transitions(
            @PathVariable String reference,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody com.fasterxml.jackson.databind.JsonNode body) {
        return cases.write(true, reference, "TRANSITION", key, body);
    }

    @GetMapping("/{reference}/notes")
    public Object notes(
            @PathVariable String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return cases.notes(reference, page, pageSize);
    }

    @GetMapping("/{reference}/requests/{key}")
    public Object recoverWrite(@PathVariable String reference, @PathVariable String key) {
        return cases.recoverWrite(true, reference, key);
    }
}
