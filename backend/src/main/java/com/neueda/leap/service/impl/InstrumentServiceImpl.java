package com.neueda.leap.service.impl;

import com.neueda.leap.domain.Instrument;
import com.neueda.leap.dto.CreateInstrumentRequestDto;
import com.neueda.leap.dto.UpdateInstrumentRequestDto;
import com.neueda.leap.exception.InstrumentNotFoundException;
import com.neueda.leap.mapper.InstrumentMapper;
import com.neueda.leap.service.InstrumentService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InstrumentServiceImpl implements InstrumentService {
    private final InstrumentMapper instrumentMapper;

    public InstrumentServiceImpl(InstrumentMapper instrumentMapper) {
        this.instrumentMapper = instrumentMapper;
    }

    @Override
    public Instrument getInstrument(Integer id) {
        validateId(id);
        Instrument instrument = instrumentMapper.getInstrument(id);
        if (instrument == null) {
            throw new InstrumentNotFoundException(id);
        }
        return instrument;
    }

    @Override
    public List<Instrument> listInstruments() {
        return instrumentMapper.listInstruments();
    }

    @Override
    public Instrument createInstrument(CreateInstrumentRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Instrument request is required.");
        }
        if (isBlank(request.instrumentName())) {
            throw new IllegalArgumentException("Instrument name is required.");
        }
        if (isBlank(request.ticker())) {
            throw new IllegalArgumentException("Ticker is required.");
        }
        if (isBlank(request.currency())) {
            throw new IllegalArgumentException("Currency is required.");
        }

        Instrument instrument = new Instrument();
        instrument.setInstrumentName(request.instrumentName().trim());
        instrument.setTicker(request.ticker().trim());
        instrument.setCurrency(request.currency().trim());
        instrument.setAssetClass(trimToNull(request.assetClass()));
        instrument.setSecurityType(trimToNull(request.securityType()));
        instrument.setIsActive(request.isActive() == null ? Boolean.TRUE : request.isActive());

        instrumentMapper.insertInstrument(instrument);
        return getInstrument(instrument.getInstrumentId());
    }

    @Override
    public Instrument updateInstrument(Integer id, UpdateInstrumentRequestDto request) {
        validateId(id);
        if (request == null) {
            throw new IllegalArgumentException("Instrument update request is required.");
        }
        if (request.instrumentName() != null && request.instrumentName().isBlank()) {
            throw new IllegalArgumentException("Instrument name cannot be blank.");
        }
        if (request.ticker() != null && request.ticker().isBlank()) {
            throw new IllegalArgumentException("Ticker cannot be blank.");
        }
        if (request.currency() != null && request.currency().isBlank()) {
            throw new IllegalArgumentException("Currency cannot be blank.");
        }
        if (request.instrumentName() == null
                && request.ticker() == null
                && request.currency() == null
                && request.assetClass() == null
                && request.securityType() == null
                && request.isActive() == null) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }

        Instrument update = new Instrument();
        update.setInstrumentId(id);
        update.setInstrumentName(trimToNull(request.instrumentName()));
        update.setTicker(trimToNull(request.ticker()));
        update.setCurrency(trimToNull(request.currency()));
        update.setAssetClass(trimToNull(request.assetClass()));
        update.setSecurityType(trimToNull(request.securityType()));
        update.setIsActive(request.isActive());

        int rows = instrumentMapper.updateInstrument(update);
        if (rows == 0) {
            throw new InstrumentNotFoundException(id);
        }

        return getInstrument(id);
    }

    private void validateId(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Instrument id must be a positive integer.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

