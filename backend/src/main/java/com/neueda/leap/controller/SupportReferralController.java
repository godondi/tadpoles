package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.service.ComplianceCaseService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operations/cases/{reference}")
@PreAuthorize("hasRole('TRADING_OPS') and !hasRole('CLIENT')")
public class SupportReferralController {
    private final ComplianceCaseService cases;

    public SupportReferralController(ComplianceCaseService cases) {
        this.cases = cases;
    }

    @GetMapping("/reviewers")
    public Object reviewers(@PathVariable String reference) {
        return cases.reviewers(reference);
    }

    @PostMapping("/referrals")
    public Object refer(
            @PathVariable String reference,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody JsonNode body) {
        return cases.refer(reference, key, body);
    }
}
