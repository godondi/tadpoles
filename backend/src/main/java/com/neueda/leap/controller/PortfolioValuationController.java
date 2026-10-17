package com.neueda.leap.controller;

import com.neueda.leap.dto.PortfolioValuationDto;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.impl.PortfolioValuationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PortfolioValuationController {
    private final PortfolioValuationService valuations;
    private final AppUserService users;

    public PortfolioValuationController(
            PortfolioValuationService valuations, AppUserService users) {
        this.valuations = valuations;
        this.users = users;
    }

    @GetMapping("/api/workspace/client/portfolio/valuation")
    @PreAuthorize("hasRole('CLIENT')")
    public PortfolioValuationDto value(@AuthenticationPrincipal Jwt jwt) {
        Integer clientId = SecurityRoleSupport.resolveCurrentUser(jwt, users).getClientId();
        if (clientId == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Complete your client onboarding first");
        }
        return valuations.value(clientId);
    }
}
