package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.service.SupportExportService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@PreAuthorize("hasRole('TRADING_OPS') and !hasRole('CLIENT')")
public class SupportExportController {
    private final SupportExportService exports;

    public SupportExportController(SupportExportService exports) {
        this.exports = exports;
    }

    @PostMapping("/api/operations/cases/{reference}/exports")
    public Object associate(
            @PathVariable String reference,
            @RequestHeader(value = "X-Operations-Context", required = false) UUID context,
            @RequestBody JsonNode body) {
        return exports.associate(reference, context, body);
    }
}
