package com.neueda.leap.controller;

import com.neueda.leap.dto.WorkspaceDtos.*;
import com.neueda.leap.service.*;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('CLIENT')")
public class WorkspaceClientController {
    private final WorkspaceReadService reads;
    private final AppUserService users;
    private final TradingService trading;

    public WorkspaceClientController(
            WorkspaceReadService reads, AppUserService users, TradingService trading) {
        this.reads = reads;
        this.users = users;
        this.trading = trading;
    }

    private int clientId(Jwt jwt) {
        Integer id = SecurityRoleSupport.resolveCurrentUser(jwt, users).getClientId();
        if (id == null)
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Complete your client onboarding first");
        return id;
    }

    @GetMapping("/workspace/client/portfolio")
    public Object portfolio(@AuthenticationPrincipal Jwt jwt) {
        int client = clientId(jwt);
        return trading.portfolio(client, reads.portfolio(client));
    }

    @GetMapping("/orders")
    public Object orders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) Integer instrumentId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String rejectionStage) {
        int client = clientId(jwt);
        return trading.orders(
                client,
                rejectionStage == null
                        ? reads.orders(client, page, pageSize, status, side, instrumentId, from, to)
                        : reads.orders(
                                client,
                                page,
                                pageSize,
                                status,
                                side,
                                instrumentId,
                                from,
                                to,
                                rejectionStage));
    }

    @GetMapping("/orders/{id}")
    public Object order(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        int client = clientId(jwt);
        return trading.detail(client, id, reads.order(client, id));
    }

    @GetMapping("/workspace/client/instruments")
    public Object instruments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String assetClass) {
        return trading.catalog(
                clientId(jwt), reads.instruments(page, pageSize, search, assetClass));
    }

    @GetMapping("/workspace/client/trading-availability")
    public Object availability(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer instrumentId) {
        return trading.availability(clientId(jwt), instrumentId);
    }

    @PostMapping("/orders/preview")
    public Object preview(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody com.fasterxml.jackson.databind.JsonNode body) {
        return trading.preview(clientId(jwt), com.neueda.leap.trading.TradingRules.command(body));
    }

    @PostMapping("/orders")
    public org.springframework.http.ResponseEntity<?> submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody com.fasterxml.jackson.databind.JsonNode body) {
        if (!trading.enabled() && key == null)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Trading is not enabled. No order has been accepted or recorded.");
        var user = SecurityRoleSupport.resolveCurrentUser(jwt, users);
        var receipt =
                trading.submit(
                        clientId(jwt),
                        user.getUserId(),
                        key,
                        com.neueda.leap.trading.TradingRules.command(body));
        String state = (String) receipt.get("status");
        int code =
                "ADMISSION".equals(receipt.get("rejectionStage"))
                        ? 422
                        : java.util.Set.of("ACCEPTED", "EXECUTING").contains(state) ? 202 : 200;
        if (code == 422) {
            receipt.put("timestamp", java.time.OffsetDateTime.now());
            receipt.put("error", "Unprocessable Entity");
            receipt.put("httpStatus", 422);
            receipt.put("path", "/api/orders");
            receipt.put("correlationId", java.util.UUID.randomUUID().toString());
        }
        return org.springframework.http.ResponseEntity.status(code)
                .header("Location", "/api/orders/" + receipt.get("orderId"))
                .body(receipt);
    }

    @GetMapping("/orders/by-request/{key}")
    public Object byRequest(@AuthenticationPrincipal Jwt jwt, @PathVariable String key) {
        return trading.byRequest(clientId(jwt), key);
    }

    @GetMapping("/orders/{id}/events")
    public Object events(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable int id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return trading.events(clientId(jwt), id, page, pageSize);
    }

    // Authenticated resumable cursor-polling transport. Every request revalidates session and
    // ownership.
    @GetMapping("/workspace/client/order-events/stream")
    public Object changes(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") long cursor) {
        return trading.changes(clientId(jwt), cursor);
    }
}
