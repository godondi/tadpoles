package com.neueda.leap.controller;

import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.dto.ClientTradeListResponseDto;
import com.neueda.leap.dto.ClientTradeResponseDto;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.ClientTradeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients/{clientId}/trades")
public class ClientTradeController {
    private final AppUserService appUserService;
    private final ClientTradeService clientTradeService;

    public ClientTradeController(AppUserService appUserService, ClientTradeService clientTradeService) {
        this.appUserService = appUserService;
        this.clientTradeService = clientTradeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT')")
    public ClientTradeListResponseDto listTrades(@PathVariable Integer clientId, @AuthenticationPrincipal Jwt jwt) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        return ClientTradeListResponseDto.fromEntities(clientTradeService.listClientTrades(clientId));
    }

    @GetMapping("/{tradeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT')")
    public ClientTradeResponseDto getTrade(
            @PathVariable Integer clientId,
            @PathVariable Integer tradeId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        ClientTrade trade = clientTradeService.getTrade(clientId, tradeId);
        return ClientTradeResponseDto.fromEntity(trade);
    }
}
