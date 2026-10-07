package com.neueda.leap.controller;

import com.neueda.leap.dto.FauxnanceDailyCandleResponseDto;
import com.neueda.leap.service.FauxnanceDailyCandleService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/candles", "/api/candles"})
public class FauxnanceDailyCandleController {
    private final FauxnanceDailyCandleService fauxnanceDailyCandleService;

    public FauxnanceDailyCandleController(FauxnanceDailyCandleService fauxnanceDailyCandleService) {
        this.fauxnanceDailyCandleService = fauxnanceDailyCandleService;
    }

    @GetMapping("/{symbol}")
    @PreAuthorize("isAuthenticated()")
    public FauxnanceDailyCandleResponseDto getCandles(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String interval
    ) {
        return FauxnanceDailyCandleResponseDto.fromEntity(
                fauxnanceDailyCandleService.getCandles(symbol, from, to, interval)
        );
    }
}

