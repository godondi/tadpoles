package com.neueda.leap.controller;

import com.neueda.leap.dto.FinanceSummaryResponseDto;
import com.neueda.leap.service.FinanceSummaryService;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Fail closed until approved scopes and genuine evidence/reporting sources are configured. */
@RestController
@RequestMapping("/api")
public class StaffWorkspaceController {
    private final FinanceSummaryService financeSummaryService;

    public StaffWorkspaceController(FinanceSummaryService financeSummaryService) {
        this.financeSummaryService = financeSummaryService;
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
    @PreAuthorize("hasRole('FINANCE') and !hasRole('CLIENT')")
    public FinanceSummaryResponseDto financeSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return financeSummaryService.summary(from, to);
    }

    private void unavailable(String message) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
