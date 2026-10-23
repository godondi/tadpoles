package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.service.ComplianceCaseService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compliance/cases")
@PreAuthorize("hasRole('COMPLIANCE')")
public class ComplianceCaseController {
    private final ComplianceCaseService cases;

    public ComplianceCaseController(ComplianceCaseService cases) {
        this.cases = cases;
    }

    @GetMapping
    public Object list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page) {
        return cases.list(status, page);
    }

    @GetMapping("/{reference}")
    public Object detail(
            @PathVariable String reference, @RequestParam(defaultValue = "0") int page) {
        return cases.detail(reference, page);
    }

    @PostMapping("/{reference}/actions")
    public Object command(
            @PathVariable String reference,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode body) {
        return cases.command(reference, key, body);
    }

    @GetMapping("/{reference}/requests/{key}")
    public Object recover(@PathVariable String reference, @PathVariable String key) {
        return cases.recover(reference, key);
    }
}
