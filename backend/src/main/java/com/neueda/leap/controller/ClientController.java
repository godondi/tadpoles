package com.neueda.leap.controller;

import com.neueda.leap.domain.Client;
import com.neueda.leap.dto.ClientBalanceResponseDto;
import com.neueda.leap.dto.ClientListResponseDto;
import com.neueda.leap.dto.ClientResponseDto;
import com.neueda.leap.dto.CreateClientRequestDto;
import com.neueda.leap.dto.UpdateClientRequestDto;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.ClientService;
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
public class ClientController {
    private final AppUserService appUserService;
    private final ClientService clientService;

    public ClientController(AppUserService appUserService, ClientService clientService) {
        this.appUserService = appUserService;
        this.clientService = clientService;
    }

    @GetMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientListResponseDto listClients() {
        return ClientListResponseDto.fromEntities(clientService.listClients());
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ClientResponseDto createClient(@RequestBody CreateClientRequestDto request) {
        Client client = clientService.createClient(request);
        return ClientResponseDto.fromEntity(client);
    }

    @GetMapping("/clients/{clientId}")
    @PreAuthorize("isAuthenticated()")
    public ClientResponseDto getClient(@PathVariable Integer clientId, @AuthenticationPrincipal Jwt jwt) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        Client client = clientService.getClient(clientId);
        return ClientResponseDto.fromEntity(client);
    }

    @PatchMapping("/clients/{clientId}")
    @PreAuthorize("isAuthenticated()")
    public ClientResponseDto updateClient(
            @PathVariable Integer clientId,
            @RequestBody UpdateClientRequestDto request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        Client client = clientService.updateClient(clientId, request);
        return ClientResponseDto.fromEntity(client);
    }

    @GetMapping("/clients/{clientId}/balance")
    @PreAuthorize("isAuthenticated()")
    public ClientBalanceResponseDto getClientBalance(@PathVariable Integer clientId, @AuthenticationPrincipal Jwt jwt) {
        SecurityRoleSupport.requireClientOwnership(jwt, appUserService, clientId);
        return new ClientBalanceResponseDto(clientId, clientService.getClientBalance(clientId));
    }
}