package com.neueda.leap.controller;

import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.dto.ClientHoldingListResponseDto;
import com.neueda.leap.dto.ClientHoldingResponseDto;
import com.neueda.leap.dto.CreateClientHoldingRequestDto;
import com.neueda.leap.dto.UpdateClientHoldingRequestDto;
import com.neueda.leap.service.ClientHoldingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final ClientHoldingService clientHoldingService;

    public ClientHoldingController(ClientHoldingService clientHoldingService) {
        this.clientHoldingService = clientHoldingService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ClientHoldingListResponseDto listHoldings(@PathVariable Integer clientId) {
        return ClientHoldingListResponseDto.fromEntities(clientHoldingService.listClientHoldings(clientId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ClientHoldingResponseDto createHolding(
            @PathVariable Integer clientId,
            @RequestBody CreateClientHoldingRequestDto request
    ) {
        ClientHolding holding = clientHoldingService.createHolding(clientId, request);
        return ClientHoldingResponseDto.fromEntity(holding);
    }

    @GetMapping("/{holdingId}")
    @PreAuthorize("isAuthenticated()")
    public ClientHoldingResponseDto getHolding(
            @PathVariable Integer clientId,
            @PathVariable Integer holdingId
    ) {
        ClientHolding holding = clientHoldingService.getHolding(clientId, holdingId);
        return ClientHoldingResponseDto.fromEntity(holding);
    }

    @PatchMapping("/{holdingId}")
    @PreAuthorize("isAuthenticated()")
    public ClientHoldingResponseDto updateHolding(
            @PathVariable Integer clientId,
            @PathVariable Integer holdingId,
            @RequestBody UpdateClientHoldingRequestDto request
    ) {
        ClientHolding holding = clientHoldingService.updateHolding(clientId, holdingId, request);
        return ClientHoldingResponseDto.fromEntity(holding);
    }
}
