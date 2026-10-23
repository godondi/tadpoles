package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.service.SupportCaseService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/cases")
@PreAuthorize("hasRole('CLIENT')")
public class SupportCaseController {
    private final SupportCaseService cases;

    public SupportCaseController(SupportCaseService cases) {
        this.cases = cases;
    }

    @PostMapping
    public Object create(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode body) {
        return cases.create(key, body);
    }

    @GetMapping("/intake-policy")
    public Object intakePolicy() {
        return cases.intakePolicy();
    }

    @GetMapping("/by-request/{key}")
    public Object byRequest(@PathVariable String key) {
        return cases.byRequest(key);
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return cases.list(false, status, search, page, pageSize);
    }

    @GetMapping("/{reference}")
    public Object detail(
            @PathVariable String reference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return cases.detail(false, reference, page, pageSize);
    }

    @PostMapping("/{reference}/replies")
    public Object replies(
            @PathVariable String reference,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody com.fasterxml.jackson.databind.JsonNode body) {
        return cases.write(false, reference, "REPLY", key, body);
    }

    @PostMapping("/{reference}/reopen")
    public Object reopen(
            @PathVariable String reference,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody com.fasterxml.jackson.databind.JsonNode body) {
        return cases.write(false, reference, "REOPEN", key, body);
    }

    @GetMapping("/{reference}/requests/{key}")
    public Object recoverWrite(@PathVariable String reference, @PathVariable String key) {
        return cases.recoverWrite(false, reference, key);
    }
}
