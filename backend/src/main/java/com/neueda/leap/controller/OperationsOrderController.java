package com.neueda.leap.controller;

import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.dto.ClientTradeListResponseDto;
import com.neueda.leap.dto.ClientTradeResponseDto;
import com.neueda.leap.service.ClientTradeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations/orders")
public class OperationsOrderController {
    private final ClientTradeService clientTradeService;

    public OperationsOrderController(ClientTradeService clientTradeService) {
        this.clientTradeService = clientTradeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TRADING_OPS', 'OPERATIONS')")
    public ClientTradeListResponseDto listOrders() {
        return ClientTradeListResponseDto.fromEntities(clientTradeService.listAllTrades());
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('TRADING_OPS', 'OPERATIONS')")
    public ClientTradeResponseDto getOrder(@PathVariable Integer orderId) {
        ClientTrade trade = clientTradeService.getTradeById(orderId);
        return ClientTradeResponseDto.fromEntity(trade);
    }
}
