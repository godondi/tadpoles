package com.neueda.leap.controller;

import com.neueda.leap.domain.Instrument;
import com.neueda.leap.dto.InstrumentListResponseDto;
import com.neueda.leap.dto.InstrumentResponseDto;
import com.neueda.leap.service.InstrumentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {
    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT', 'TRADING_OPS')")
    public InstrumentListResponseDto listInstruments() {
        return InstrumentListResponseDto.fromEntities(instrumentService.listInstruments());
    }

    @GetMapping("/{instrumentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENT', 'TRADING_OPS')")
    public InstrumentResponseDto getInstrument(@PathVariable Integer instrumentId) {
        Instrument instrument = instrumentService.getInstrument(instrumentId);
        return InstrumentResponseDto.fromEntity(instrument);
    }
}
