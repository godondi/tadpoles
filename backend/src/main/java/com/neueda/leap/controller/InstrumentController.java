package com.neueda.leap.controller;

import com.neueda.leap.domain.Instrument;
import com.neueda.leap.dto.CreateInstrumentRequestDto;
import com.neueda.leap.dto.InstrumentListResponseDto;
import com.neueda.leap.dto.InstrumentResponseDto;
import com.neueda.leap.dto.UpdateInstrumentRequestDto;
import com.neueda.leap.service.InstrumentService;
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
@RequestMapping("/api/instruments")
public class InstrumentController {
    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    public InstrumentListResponseDto listInstruments() {
        return InstrumentListResponseDto.fromEntities(instrumentService.listInstruments());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentResponseDto createInstrument(@RequestBody CreateInstrumentRequestDto request) {
        Instrument instrument = instrumentService.createInstrument(request);
        return InstrumentResponseDto.fromEntity(instrument);
    }

    @GetMapping("/{instrumentId}")
    public InstrumentResponseDto getInstrument(@PathVariable Integer instrumentId) {
        Instrument instrument = instrumentService.getInstrument(instrumentId);
        return InstrumentResponseDto.fromEntity(instrument);
    }

    @PatchMapping("/{instrumentId}")
    public InstrumentResponseDto updateInstrument(
            @PathVariable Integer instrumentId,
            @RequestBody UpdateInstrumentRequestDto request
    ) {
        Instrument instrument = instrumentService.updateInstrument(instrumentId, request);
        return InstrumentResponseDto.fromEntity(instrument);
    }
}
