package com.neueda.leap.controller;

import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.impl.DisplayCurrencyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class DisplayCurrencyController {
    private final DisplayCurrencyService display;
    private final AppUserService users;

    public DisplayCurrencyController(DisplayCurrencyService display, AppUserService users) {
        this.display = display;
        this.users = users;
    }

    @GetMapping("/api/workspace/client/display-currency")
    @PreAuthorize("hasRole('CLIENT')")
    public Object estimate(@AuthenticationPrincipal Jwt jwt, @RequestParam String currency) {
        Integer client = SecurityRoleSupport.resolveCurrentUser(jwt, users).getClientId();
        if (client == null) throw new IllegalArgumentException("Complete your profile");
        return display.estimate(client, currency);
    }
}
