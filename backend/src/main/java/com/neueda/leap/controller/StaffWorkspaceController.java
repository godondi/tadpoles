package com.neueda.leap.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Fail closed until approved scopes and genuine evidence/reporting sources are configured. */
@RestController
@RequestMapping("/api")
public class StaffWorkspaceController {
    @GetMapping({
        "/compliance/investigations",
        "/compliance/orders/{id}/evidence",
        "/compliance/evidence"
    })
    @PreAuthorize("hasRole('COMPLIANCE')")
    public void compliance() {
        unavailable(
                "Investigation evidence is not available yet. Approved access scopes, purposes and"
                        + " evidence policies must be configured. Exports are disabled.");
    }

    @GetMapping({
        "/analytics/summary",
        "/analytics/trends",
        "/analytics/instruments",
        "/analytics/freshness"
    })
    @PreAuthorize("hasRole('ANALYST')")
    public void analytics() {
        unavailable(
                "Reports are not available yet. The separate reporting source and approved metrics"
                        + " have not been connected. Data freshness is unavailable.");
    }

    @GetMapping("/finance/summary")
    @PreAuthorize("hasRole('FINANCE')")
    public void finance() {
        unavailable(
                "Period totals and reporting are not available yet. Operational reconciliation is"
                        + " available in the Reconciliation workspace.");
    }

    private void unavailable(String message) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
