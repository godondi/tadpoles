package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.Instrument;
import com.neueda.leap.dto.CreateInstrumentRequestDto;
import com.neueda.leap.dto.UpdateInstrumentRequestDto;
import com.neueda.leap.exception.InstrumentNotFoundException;
import com.neueda.leap.mapper.InstrumentMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceImplTest {
    @Mock
    private InstrumentMapper instrumentMapper;

    private InstrumentServiceImpl instrumentService;

    @BeforeEach
    void setUp() {
        instrumentService = new InstrumentServiceImpl(instrumentMapper);
    }

    @Test
    void getInstrumentReturnsInstrumentWhenFound() {
        Instrument instrument = buildInstrument();
        when(instrumentMapper.getInstrument(11)).thenReturn(instrument);

        Instrument result = instrumentService.getInstrument(11);

        assertEquals(11, result.getInstrumentId());
        assertEquals("AAPL", result.getTicker());
    }

    @Test
    void listInstrumentsReturnsInstruments() {
        when(instrumentMapper.listInstruments()).thenReturn(List.of(buildInstrument()));

        List<Instrument> results = instrumentService.listInstruments();

        assertEquals(1, results.size());
        assertEquals(11, results.get(0).getInstrumentId());
    }

    @Test
    void createInstrumentReturnsInsertedInstrument() {
        CreateInstrumentRequestDto request = new CreateInstrumentRequestDto(
                "Apple Inc",
                "AAPL",
                "USD",
                "Equity",
                "Stock",
                null
        );

        when(instrumentMapper.insertInstrument(any())).thenAnswer(invocation -> {
            Instrument toInsert = invocation.getArgument(0, Instrument.class);
            toInsert.setInstrumentId(11);
            return 1;
        });
        when(instrumentMapper.getInstrument(11)).thenReturn(buildInstrument());

        Instrument result = instrumentService.createInstrument(request);

        assertEquals(11, result.getInstrumentId());
    }

    @Test
    void updateInstrumentReturnsUpdatedInstrument() {
        UpdateInstrumentRequestDto request = new UpdateInstrumentRequestDto(
                null,
                null,
                "EUR",
                null,
                null,
                null
        );
        Instrument updated = buildInstrument();
        updated.setCurrency("EUR");

        when(instrumentMapper.updateInstrument(any())).thenReturn(1);
        when(instrumentMapper.getInstrument(11)).thenReturn(updated);

        Instrument result = instrumentService.updateInstrument(11, request);

        assertEquals("EUR", result.getCurrency());
    }

    @Test
    void getInstrumentThrowsWhenMissing() {
        when(instrumentMapper.getInstrument(99)).thenReturn(null);
        assertThrows(InstrumentNotFoundException.class, () -> instrumentService.getInstrument(99));
    }

    @Test
    void updateInstrumentThrowsWhenMissing() {
        when(instrumentMapper.updateInstrument(any())).thenReturn(0);
        assertThrows(InstrumentNotFoundException.class, () -> instrumentService.updateInstrument(
                99, new UpdateInstrumentRequestDto("A", null, null, null, null, null)));
    }

    @Test
    void createInstrumentThrowsOnBlankTicker() {
        CreateInstrumentRequestDto request = new CreateInstrumentRequestDto(
                "Apple Inc",
                " ",
                "USD",
                null,
                null,
                null
        );
        assertThrows(IllegalArgumentException.class, () -> instrumentService.createInstrument(request));
    }

    @Test
    void updateInstrumentThrowsWhenNoFieldsProvided() {
        UpdateInstrumentRequestDto request = new UpdateInstrumentRequestDto(null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> instrumentService.updateInstrument(11, request));
    }

    private Instrument buildInstrument() {
        Instrument instrument = new Instrument();
        instrument.setInstrumentId(11);
        instrument.setInstrumentName("Apple Inc");
        instrument.setTicker("AAPL");
        instrument.setCurrency("USD");
        instrument.setAssetClass("Equity");
        instrument.setSecurityType("Stock");
        instrument.setIsActive(true);
        return instrument;
    }
}

