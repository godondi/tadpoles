package com.neueda.leap.controller;

import com.neueda.leap.domain.Client;
import com.neueda.leap.dto.ClientResponseDto;
import com.neueda.leap.service.ClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ClientController {
    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping({"/client/{id}", "/clients/{id}"})
    public ClientResponseDto getClient(@PathVariable Integer id) {
        Client client = clientService.getClient(id);
        return ClientResponseDto.fromEntity(client);
    }
}


