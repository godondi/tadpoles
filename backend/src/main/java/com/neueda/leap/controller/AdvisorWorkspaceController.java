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
@RequestMapping("/api/workspace/advisor")
@PreAuthorize("hasRole('ADVISOR')")
public class AdvisorWorkspaceController {
    private final AdvisorWorkspaceService workspace;
    private final AppUserService users;

    public AdvisorWorkspaceController(AdvisorWorkspaceService workspace, AppUserService users) {
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

    @GetMapping("/clients")
    public Page<AdvisorClient> clients(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "") String search) {
        return workspace.clients(userId(jwt), page, pageSize, search);
    }

    @GetMapping("/clients/{clientId}/portfolio")
    public Portfolio portfolio(@AuthenticationPrincipal Jwt jwt, @PathVariable int clientId) {
        return workspace.portfolio(userId(jwt), clientId);
    }

    @GetMapping("/clients/{clientId}/guidance")
    public GuidanceResponse guidance(@AuthenticationPrincipal Jwt jwt, @PathVariable int clientId) {
        return workspace.guidance(userId(jwt), clientId);
    }

    @PutMapping("/clients/{clientId}/guidance/{instrumentId}")
    public HoldingGuidance saveGuidance(@AuthenticationPrincipal Jwt jwt, @PathVariable int clientId,
            @PathVariable int instrumentId, @RequestBody GuidanceRequest request) {
        return workspace.saveGuidance(userId(jwt), clientId, instrumentId, request);
    }

    @GetMapping("/clients/{clientId}/recommendations")
    public RecommendationsResponse recommendations(@AuthenticationPrincipal Jwt jwt, @PathVariable int clientId) {
        return workspace.recommendations(userId(jwt), clientId);
    }

    @PostMapping("/clients/{clientId}/recommendations")
    @ResponseStatus(HttpStatus.CREATED)
    public Recommendation createRecommendation(@AuthenticationPrincipal Jwt jwt, @PathVariable int clientId,
            @RequestBody RecommendationRequest request) {
        return workspace.createRecommendation(userId(jwt), clientId, request);
    }

    @PatchMapping("/clients/{clientId}/recommendations/{recommendationId}")
    public Recommendation updateRecommendation(@AuthenticationPrincipal Jwt jwt, @PathVariable int clientId,
            @PathVariable int recommendationId, @RequestBody RecommendationUpdateRequest request) {
        return workspace.updateRecommendation(userId(jwt), clientId, recommendationId, request);
    }

    @DeleteMapping("/clients/{clientId}/recommendations/{recommendationId}")
    public ArchiveResponse archiveRecommendation(@AuthenticationPrincipal Jwt jwt, @PathVariable int clientId,
            @PathVariable int recommendationId, @RequestBody ArchiveRequest request) {
        return workspace.archiveRecommendation(userId(jwt), clientId, recommendationId, request);
    }

    @GetMapping("/instruments")
    public Page<Instrument> instruments(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(defaultValue = "") String search) {
        return workspace.instruments(userId(jwt), page, pageSize, search);
    }
}