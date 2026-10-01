package com.neueda.leap.controller;

import com.neueda.leap.domain.ClientSubscription;
import com.neueda.leap.dto.ClientSubscriptionListResponseDto;
import com.neueda.leap.dto.ClientSubscriptionResponseDto;
import com.neueda.leap.dto.CreateClientSubscriptionRequestDto;
import com.neueda.leap.service.ClientSubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients/{clientId}/subscriptions")
public class ClientSubscriptionController {
    private final ClientSubscriptionService clientSubscriptionService;

    public ClientSubscriptionController(ClientSubscriptionService clientSubscriptionService) {
        this.clientSubscriptionService = clientSubscriptionService;
    }

    @GetMapping
    public ClientSubscriptionListResponseDto listClientSubscriptions(@PathVariable Integer clientId) {
        return ClientSubscriptionListResponseDto.fromEntities(
                clientSubscriptionService.listClientSubscriptions(clientId)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientSubscriptionResponseDto createClientSubscription(
            @PathVariable Integer clientId,
            @RequestBody CreateClientSubscriptionRequestDto request
    ) {
        ClientSubscription subscription = clientSubscriptionService.createClientSubscription(clientId, request);
        return ClientSubscriptionResponseDto.fromEntity(subscription);
    }
}
