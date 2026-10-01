package com.neueda.leap.controller;

import com.neueda.leap.domain.TradeSuggestion;
import com.neueda.leap.dto.CreateTradeSuggestionRequestDto;
import com.neueda.leap.dto.TradeSuggestionListResponseDto;
import com.neueda.leap.dto.TradeSuggestionResponseDto;
import com.neueda.leap.dto.UpdateTradeSuggestionRequestDto;
import com.neueda.leap.service.TradeSuggestionService;
import org.springframework.http.HttpStatus;
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
    private final TradeSuggestionService tradeSuggestionService;

    public TradeSuggestionController(TradeSuggestionService tradeSuggestionService) {
        this.tradeSuggestionService = tradeSuggestionService;
    }

    @PostMapping("/advisors/{advisorId}/clients/{clientId}/trade-suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    public TradeSuggestionResponseDto createTradeSuggestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer advisorId,
            @PathVariable Integer clientId,
            @RequestBody CreateTradeSuggestionRequestDto request
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADVISOR");
        TradeSuggestion suggestion = tradeSuggestionService.createTradeSuggestion(advisorId, clientId, request);
        return TradeSuggestionResponseDto.fromEntity(suggestion);
    }

    @GetMapping("/clients/{clientId}/trade-suggestions")
    public TradeSuggestionListResponseDto listClientTradeSuggestions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer clientId
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN", "AUDITOR", "ANALYST", "ADVISOR", "CLIENT");
        return TradeSuggestionListResponseDto.fromEntities(tradeSuggestionService.listClientTradeSuggestions(clientId));
    }

    @GetMapping("/trade-suggestions/{suggestionId}")
    public TradeSuggestionResponseDto getTradeSuggestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer suggestionId
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN", "AUDITOR", "ANALYST", "ADVISOR", "CLIENT");
        TradeSuggestion suggestion = tradeSuggestionService.getTradeSuggestion(suggestionId);
        return TradeSuggestionResponseDto.fromEntity(suggestion);
    }

    @PatchMapping("/trade-suggestions/{suggestionId}")
    public TradeSuggestionResponseDto updateTradeSuggestion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer suggestionId,
            @RequestBody UpdateTradeSuggestionRequestDto request
    ) {
        SecurityRoleSupport.requireAnyRole(jwt, "CLIENT");
        TradeSuggestion suggestion = tradeSuggestionService.updateTradeSuggestion(suggestionId, request);
        return TradeSuggestionResponseDto.fromEntity(suggestion);
    }
}

