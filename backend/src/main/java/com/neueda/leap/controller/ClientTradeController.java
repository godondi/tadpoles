package com.neueda.leap.controller;

import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.dto.ClientTradeListResponseDto;
import com.neueda.leap.dto.ClientTradeResponseDto;
import com.neueda.leap.dto.CreateClientTradeRequestDto;
import com.neueda.leap.dto.FillOrderRequestDto;
import com.neueda.leap.dto.OrderFillResponseDto;
import com.neueda.leap.dto.UpdateClientTradeRequestDto;
import com.neueda.leap.service.ClientTradeService;
import com.neueda.leap.service.OrderFillService;
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
@RequestMapping("/api/clients/{clientId}/trades")
public class ClientTradeController {
    private final ClientTradeService clientTradeService;
    private final OrderFillService orderFillService;

    public ClientTradeController(ClientTradeService clientTradeService, OrderFillService orderFillService) {
        this.clientTradeService = clientTradeService;
        this.orderFillService = orderFillService;
    }

    @GetMapping
    public ClientTradeListResponseDto listTrades(@PathVariable Integer clientId) {
        return ClientTradeListResponseDto.fromEntities(clientTradeService.listClientTrades(clientId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientTradeResponseDto createTrade(
            @PathVariable Integer clientId,
            @RequestBody CreateClientTradeRequestDto request
    ) {
        ClientTrade trade = clientTradeService.createTrade(clientId, request);
        return ClientTradeResponseDto.fromEntity(trade);
    }

    @GetMapping("/{tradeId}")
    public ClientTradeResponseDto getTrade(
            @PathVariable Integer clientId,
            @PathVariable Integer tradeId
    ) {
        ClientTrade trade = clientTradeService.getTrade(clientId, tradeId);
        return ClientTradeResponseDto.fromEntity(trade);
    }

    @PatchMapping("/{tradeId}")
    public ClientTradeResponseDto updateTrade(
            @PathVariable Integer clientId,
            @PathVariable Integer tradeId,
            @RequestBody UpdateClientTradeRequestDto request
    ) {
        ClientTrade trade = clientTradeService.updateTrade(clientId, tradeId, request);
        return ClientTradeResponseDto.fromEntity(trade);
    }

    @PostMapping("/{tradeId}/fill")
    public OrderFillResponseDto fillTrade(
            @PathVariable Integer clientId,
            @PathVariable Integer tradeId,
            @RequestBody(required = false) FillOrderRequestDto request
    ) {
        return orderFillService.fillTrade(clientId, tradeId, request);
    }
}

