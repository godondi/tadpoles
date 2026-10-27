package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.service.SupportWorkflowService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/operations/cases/{reference}")
@PreAuthorize("hasRole('TRADING_OPS') and !hasRole('CLIENT')")
public class SupportWorkflowController {
    private final SupportWorkflowService workflow;

    public SupportWorkflowController(SupportWorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/workbench")
    public Object read(@PathVariable String reference, @RequestParam(defaultValue = "0") int page) {
        return workflow.read(reference, page);
    }

    @GetMapping("/assignees")
    public Object assignees(@PathVariable String reference) {
        return workflow.assignees(reference);
    }

    @GetMapping("/diagnostics")
    public Object diagnostics(
            @PathVariable String reference,
            @RequestParam String kind,
            @RequestParam(required = false) String evidence,
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context) {
        return workflow.diagnostics(reference, kind, evidence, context);
    }

    @GetMapping("/exports")
    public Object exports(
            @PathVariable String reference, @RequestParam(defaultValue = "0") int page) {
        return workflow.exports(reference, page);
    }

    @PostMapping(
            "/workflow/{action:assignment|classification|priority|next-action|tasks|resolution}")
    public Object command(
            @PathVariable String reference,
            @PathVariable String action,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode body) {
        return workflow.command(reference, action, key, body);
    }

    @GetMapping("/workflow/requests/{key}")
    public Object recover(@PathVariable String reference, @PathVariable String key) {
        return workflow.recover(reference, key);
    }
}
