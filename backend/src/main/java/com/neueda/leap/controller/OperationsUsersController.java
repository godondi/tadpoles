package com.neueda.leap.controller;

import com.neueda.leap.dto.CreateUserRequestDto;
import com.neueda.leap.dto.CreateUserResponseDto;
import com.neueda.leap.mapper.OperationsUserMapper;
import com.neueda.leap.service.UserService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/operations/users")
@PreAuthorize("hasRole('TRADING_OPS') and !hasRole('CLIENT')")
public class OperationsUsersController {
    private final OperationsUserMapper users;
    private final UserService userService;

    public OperationsUsersController(OperationsUserMapper users, UserService userService) {
        this.users = users;
        this.userService = userService;
    }

    @GetMapping
    public List<Map<String,Object>> clients(@RequestParam(required=false) String search) {
        return users.clients(search == null ? "" : search.trim());
    }

    @PatchMapping("/{clientId}/trade-access")
    @Transactional
    public Map<String,Object> tradeAccess(@PathVariable int clientId, @RequestBody AccessChange change,
                                         @AuthenticationPrincipal Jwt jwt) {
        if (change == null || change.revoke() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose revoke or restore access.");
        int count;
        Instant until = null;
        String action;
        if (!change.revoke()) { count = users.unrevoke(clientId); action = "RESTORE"; }
        else {
            if (change.amount() != null || change.unit() != null) {
                if (change.amount() == null || change.amount() < 1 || change.unit() == null)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide a valid timed revocation.");
                String normalizedUnit = change.unit().toUpperCase();
                int max = switch (normalizedUnit) { case "HOURS" -> 8760; case "DAYS" -> 365; case "MONTHS" -> 12; default -> 0; };
                if (change.amount() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Timed revocation cannot exceed one year.");
                ChronoUnit unit = switch (change.unit().toUpperCase()) {
                    case "HOURS" -> ChronoUnit.HOURS;
                    case "DAYS" -> ChronoUnit.DAYS;
                    case "MONTHS" -> ChronoUnit.MONTHS;
                    default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit must be hours, days, or months.");
                };
                until = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)
                        .plus(change.amount(), unit).toInstant();
            }
            count = users.revoke(clientId, until, change.reason());
            action = until == null ? "REVOKE_INDEFINITE" : "REVOKE_TIMED";
        }
        if (count != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client account not found.");
        String actor = jwt == null ? "unknown" : (jwt.getSubject() == null ? String.valueOf(jwt.getClaim("userId")) : jwt.getSubject());
        users.recordChange(clientId, actor, action, until, change.revoke() ? change.reason() : null);
        return Map.of("clientId", clientId, "updated", true);
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponseDto createStaff(@RequestBody CreateUserRequestDto request) {
        if (request == null || request.roleType() == null || !List.of("ANALYST", "ADVISOR", "COMPLIANCE", "FINANCE").contains(request.roleType().toUpperCase()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Operations can create Analyst, Advisor, Compliance, or Finance accounts.");
        return userService.registerUser(request);
    }

    public record AccessChange(Boolean revoke, Integer amount, String unit, String reason) {}
}
