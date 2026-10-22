package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.neueda.leap.service.SupportCaseService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/updates")
@PreAuthorize("hasRole('CLIENT')")
public class SupportUpdateController {
    private final SupportCaseService cases;

    public SupportUpdateController(SupportCaseService cases) {
        this.cases = cases;
    }

    @GetMapping
    public Object updates(@RequestParam(defaultValue = "0") long cursor) {
        return cases.updates(cursor);
    }

    @PostMapping("/read")
    public Object read(@RequestBody JsonNode body) {
        return cases.markRead(body);
    }
}
