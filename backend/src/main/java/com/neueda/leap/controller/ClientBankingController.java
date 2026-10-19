package com.neueda.leap.controller;

import com.fasterxml.jackson.databind.*;
import com.neueda.leap.dto.BankingDtos.*;
import com.neueda.leap.service.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workspace/client")
@PreAuthorize("hasRole('CLIENT')")
public class ClientBankingController {
    private final BankingService banking;
    private final AppUserService users;
    private final ObjectMapper json;

    public ClientBankingController(
            BankingService banking, AppUserService users, ObjectMapper json) {
        this.banking = banking;
        this.users = users;
        this.json = json;
    }

    private int client(Jwt jwt) {
        var id = SecurityRoleSupport.resolveCurrentUser(jwt, users).getClientId();
        if (id == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Complete your profile first");
        return id;
    }

    private int actor(Jwt jwt) {
        return SecurityRoleSupport.resolveCurrentUser(jwt, users).getUserId();
    }

    @GetMapping("/banks")
    public List<BankReference> banks(@AuthenticationPrincipal Jwt jwt) {
        return banking.banks(client(jwt));
    }

    @PostMapping("/banks")
    @ResponseStatus(HttpStatus.CREATED)
    public BankReference add(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        return banking.save(client(jwt), actor(jwt), null, bankRequest(body));
    }

    @PutMapping("/banks/{id}")
    public BankReference edit(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody JsonNode body) {
        return banking.save(client(jwt), actor(jwt), id, bankRequest(body));
    }

    @DeleteMapping("/banks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam long version) {
        banking.remove(client(jwt), actor(jwt), id, version);
    }

    @PostMapping("/cash-movements")
    public Movement move(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode body) {
        fields(body, Set.of("bankId", "direction", "amount"));
        if (!body.path("amount").isTextual())
            throw new IllegalArgumentException("Amount must be an exact decimal string");
        return banking.move(client(jwt), actor(jwt), key, convert(body, MovementRequest.class));
    }

    @GetMapping("/cash-movements/by-request/{key}")
    public Movement recover(@AuthenticationPrincipal Jwt jwt, @PathVariable String key) {
        return banking.byRequest(client(jwt), key);
    }

    @GetMapping("/cash-movements")
    public List<Movement> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return banking.history(client(jwt), page, pageSize);
    }

    private BankRequest bankRequest(JsonNode body) {
        fields(
                body,
                Set.of("nickname", "routingNumber", "accountNumber", "defaultAccount", "version"));
        for (String field : List.of("nickname", "routingNumber", "accountNumber"))
            if (body.hasNonNull(field) && !body.get(field).isTextual())
                throw new IllegalArgumentException(
                        "Bank fields must be strings; preserve leading zeros");
        return convert(body, BankRequest.class);
    }

    private void fields(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject())
            throw new IllegalArgumentException("An object is required");
        body.fieldNames()
                .forEachRemaining(
                        f -> {
                            if (!allowed.contains(f))
                                throw new IllegalArgumentException("Unsupported field: " + f);
                        });
    }

    private <T> T convert(JsonNode body, Class<T> type) {
        try {
            return json.treeToValue(body, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid request fields");
        }
    }
}
