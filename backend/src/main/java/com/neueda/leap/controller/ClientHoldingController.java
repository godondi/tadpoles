package com.neueda.leap.controller;

import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.dto.ClientHoldingListResponseDto;
import com.neueda.leap.dto.ClientHoldingResponseDto;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.ClientHoldingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT')")
    public ClientHoldingListResponseDto listHoldings(@PathVariable Integer clientId, @AuthenticationPrincipal Jwt jwt) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        return ClientHoldingListResponseDto.fromEntities(clientHoldingService.listClientHoldings(clientId));
    }

    @GetMapping("/{holdingId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT')")
    public ClientHoldingResponseDto getHolding(
            @PathVariable Integer clientId,
            @PathVariable Integer holdingId,
            @AuthenticationPrincipal Jwt jwt
        ) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        ClientHolding holding = clientHoldingService.getHolding(clientId, holdingId);
        return ClientHoldingResponseDto.fromEntity(holding);
    }
}
