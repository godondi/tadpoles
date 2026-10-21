package com.neueda.leap.controller;

import com.neueda.leap.dto.InstrumentPolicyRequest;
import com.neueda.leap.service.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/workspace/operations/instruments")
@PreAuthorize("hasRole('TRADING_OPS') and !hasRole('CLIENT')")
public class InstrumentPolicyController {
    private final InstrumentPolicyService policies;
    private final AppUserService users;
    private final OperationsPolicyHistoryService history;

    public InstrumentPolicyController(
            InstrumentPolicyService policies,
            AppUserService users,
            OperationsPolicyHistoryService history) {
        this.policies = policies;
        this.users = users;
        this.history = history;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {
        return policies.list(search, page);
    }

    @GetMapping("/test")
    public Map<String, Object> test(@RequestParam String symbol) {
        return policies.test(symbol);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable int id) {
        return policies.get(id);
    }

    @GetMapping("/{id}/history")
    public Object history(@PathVariable int id, @RequestParam(defaultValue = "0") int page) {
        return history.history(id, page);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> add(
            @AuthenticationPrincipal Jwt jwt, @RequestBody InstrumentPolicyRequest r) {
        return policies.save(
                null, SecurityRoleSupport.resolveCurrentUser(jwt, users).getUserId(), r);
    }

    @PutMapping("/{id}")
    public Map<String, Object> save(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable int id,
            @RequestBody InstrumentPolicyRequest r) {
        return policies.save(id, SecurityRoleSupport.resolveCurrentUser(jwt, users).getUserId(), r);
    }
}
