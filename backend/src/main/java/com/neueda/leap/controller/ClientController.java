package com.neueda.leap.controller;

import com.neueda.leap.domain.Client;
import com.neueda.leap.dto.ClientBalanceResponseDto;
import com.neueda.leap.dto.ClientListResponseDto;
import com.neueda.leap.dto.ClientResponseDto;
import com.neueda.leap.dto.CreateClientRequestDto;
import com.neueda.leap.dto.UpdateClientRequestDto;
import com.neueda.leap.service.ClientService;
import org.springframework.http.HttpStatus;
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
    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/clients")
    public ClientListResponseDto listClients() {
        return ClientListResponseDto.fromEntities(clientService.listClients());
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponseDto createClient(@RequestBody CreateClientRequestDto request) {
        Client client = clientService.createClient(request);
        return ClientResponseDto.fromEntity(client);
    }

    @GetMapping("/clients/{clientId}")
    public ClientResponseDto getClient(@PathVariable Integer clientId) {
        Client client = clientService.getClient(clientId);
        return ClientResponseDto.fromEntity(client);
    }

    @PatchMapping("/clients/{clientId}")
    public ClientResponseDto updateClient(
            @PathVariable Integer clientId,
            @RequestBody UpdateClientRequestDto request
    ) {
        Client client = clientService.updateClient(clientId, request);
        return ClientResponseDto.fromEntity(client);
    }

    @GetMapping("/clients/{clientId}/balance")
    public ClientBalanceResponseDto getClientBalance(@PathVariable Integer clientId) {
        return new ClientBalanceResponseDto(clientId, clientService.getClientBalance(clientId));
    }
}
