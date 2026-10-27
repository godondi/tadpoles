package com.neueda.leap.controller;

import com.neueda.leap.dto.AdvisorWorkspaceDtos.*;
import com.neueda.leap.service.AdvisorWorkspaceService;
import com.neueda.leap.service.AppUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workspace/client")
@PreAuthorize("hasRole('CLIENT')")
public class ClientAdvisoryController {
    private final AdvisorWorkspaceService workspace;
    private final AppUserService users;

    public ClientAdvisoryController(AdvisorWorkspaceService workspace, AppUserService users) {
        this.workspace = workspace;
        this.users = users;
    }

    private int userId(Jwt jwt) {
        var user = SecurityRoleSupport.resolveCurrentUser(jwt, users);
        if (user == null || user.getUserId() == null || user.getUserId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve authenticated user");
        }
        return user.getUserId();
    }

    @GetMapping("/advisor")
    public ClientAssignment advisor(@AuthenticationPrincipal Jwt jwt) {
        return workspace.clientAdvisor(userId(jwt));
    }

    @GetMapping("/advisory")
    public ClientAdvisory advisory(@AuthenticationPrincipal Jwt jwt) {
        return workspace.clientAdvisory(userId(jwt));
    }
}