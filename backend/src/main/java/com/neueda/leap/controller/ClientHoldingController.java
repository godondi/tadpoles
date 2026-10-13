package com.neueda.leap.controller;

import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.dto.ClientHoldingListResponseDto;
import com.neueda.leap.dto.ClientHoldingResponseDto;
import com.neueda.leap.dto.CreateClientHoldingRequestDto;
import com.neueda.leap.dto.UpdateClientHoldingRequestDto;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.ClientHoldingService;
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
@RequestMapping("/api/clients/{clientId}/holdings")
public class ClientHoldingController {
    private final AppUserService appUserService;
    private final ClientHoldingService clientHoldingService;

    public ClientHoldingController(AppUserService appUserService, ClientHoldingService clientHoldingService) {
        this.appUserService = appUserService;
        this.clientHoldingService = clientHoldingService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ClientHoldingListResponseDto listHoldings(@PathVariable Integer clientId, @AuthenticationPrincipal Jwt jwt) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        return ClientHoldingListResponseDto.fromEntities(clientHoldingService.listClientHoldings(clientId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public ClientHoldingResponseDto createHolding(
            @PathVariable Integer clientId,
            @RequestBody CreateClientHoldingRequestDto request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        ClientHolding holding = clientHoldingService.createHolding(clientId, request);
        return ClientHoldingResponseDto.fromEntity(holding);
    }

    @GetMapping("/{holdingId}")
    @PreAuthorize("hasRole('CLIENT')")
    public ClientHoldingResponseDto getHolding(
            @PathVariable Integer clientId,
            @PathVariable Integer holdingId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        ClientHolding holding = clientHoldingService.getHolding(clientId, holdingId);
        return ClientHoldingResponseDto.fromEntity(holding);
    }

    @PatchMapping("/{holdingId}")
    @PreAuthorize("hasRole('CLIENT')")
    public ClientHoldingResponseDto updateHolding(
            @PathVariable Integer clientId,
            @PathVariable Integer holdingId,
            @RequestBody UpdateClientHoldingRequestDto request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        ClientHolding holding = clientHoldingService.updateHolding(clientId, holdingId, request);
        return ClientHoldingResponseDto.fromEntity(holding);
    }
}
