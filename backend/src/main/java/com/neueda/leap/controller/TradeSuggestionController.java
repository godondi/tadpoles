package com.neueda.leap.controller;

import com.neueda.leap.domain.TradeSuggestion;
import com.neueda.leap.dto.CreateTradeSuggestionRequestDto;
import com.neueda.leap.dto.TradeSuggestionListResponseDto;
import com.neueda.leap.dto.TradeSuggestionResponseDto;
import com.neueda.leap.dto.UpdateTradeSuggestionRequestDto;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.TradeSuggestionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TradeSuggestionController {
    private final AppUserService appUserService;
    private final TradeSuggestionService tradeSuggestionService;

    public TradeSuggestionController(AppUserService appUserService, TradeSuggestionService tradeSuggestionService) {
        this.appUserService = appUserService;
        this.tradeSuggestionService = tradeSuggestionService;
    }

    @PostMapping("/advisors/{advisorId}/clients/{clientId}/trade-suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADVISOR')")
    public TradeSuggestionResponseDto createTradeSuggestion(
            @PathVariable Integer advisorId,
            @PathVariable Integer clientId,
            @RequestBody CreateTradeSuggestionRequestDto request
    ) {
        TradeSuggestion suggestion = tradeSuggestionService.createTradeSuggestion(advisorId, clientId, request);
        return TradeSuggestionResponseDto.fromEntity(suggestion);
    }

    @GetMapping("/clients/{clientId}/trade-suggestions")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'ANALYST', 'ADVISOR', 'CLIENT')")
    public TradeSuggestionListResponseDto listClientTradeSuggestions(
            @PathVariable Integer clientId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        return TradeSuggestionListResponseDto.fromEntities(tradeSuggestionService.listClientTradeSuggestions(clientId));
    }

    @GetMapping("/trade-suggestions/{suggestionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR', 'ANALYST', 'ADVISOR', 'CLIENT')")
    public TradeSuggestionResponseDto getTradeSuggestion(
            @PathVariable Integer suggestionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        TradeSuggestion suggestion = tradeSuggestionService.getTradeSuggestion(suggestionId);
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, suggestion.getClientId());
        return TradeSuggestionResponseDto.fromEntity(suggestion);
    }

    @PatchMapping("/trade-suggestions/{suggestionId}")
    @PreAuthorize("hasRole('CLIENT')")
    public TradeSuggestionResponseDto updateTradeSuggestion(
            @PathVariable Integer suggestionId,
            @RequestBody UpdateTradeSuggestionRequestDto request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        TradeSuggestion existingSuggestion = tradeSuggestionService.getTradeSuggestion(suggestionId);
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, existingSuggestion.getClientId());
        TradeSuggestion updatedSuggestion = tradeSuggestionService.updateTradeSuggestion(suggestionId, request);
        return TradeSuggestionResponseDto.fromEntity(updatedSuggestion);
    }
}
