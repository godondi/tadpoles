package com.neueda.leap.controller;

import com.neueda.leap.dto.FauxnanceQuoteResponseDto;
import com.neueda.leap.service.FauxnanceQuoteService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/quotes", "/api/quotes"})
public class FauxnanceQuoteController {
    private final FauxnanceQuoteService fauxnanceQuoteService;

    public FauxnanceQuoteController(FauxnanceQuoteService fauxnanceQuoteService) {
        this.fauxnanceQuoteService = fauxnanceQuoteService;
    }

    @GetMapping("/{symbol}")
    @PreAuthorize("isAuthenticated()")
    public FauxnanceQuoteResponseDto getQuote(@PathVariable String symbol) {
        return FauxnanceQuoteResponseDto.fromEntity(fauxnanceQuoteService.getQuote(symbol));
    }
}

