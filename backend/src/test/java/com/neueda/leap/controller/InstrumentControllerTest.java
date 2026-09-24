package com.neueda.leap.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.domain.Instrument;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.InstrumentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InstrumentController.class)
@Import(GlobalExceptionHandler.class)
class InstrumentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstrumentService instrumentService;

    @Test
    void listInstrumentsReturnsJsonResponse() throws Exception {
        Instrument instrument = buildInstrument();
        when(instrumentService.listInstruments()).thenReturn(List.of(instrument));

        mockMvc.perform(get("/api/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruments[0].instrumentId").value(11))
                .andExpect(jsonPath("$.instruments[0].ticker").value("AAPL"));
    }

    @Test
    void createInstrumentReturnsCreatedResponse() throws Exception {
        Instrument instrument = buildInstrument();
        when(instrumentService.createInstrument(org.mockito.ArgumentMatchers.any())).thenReturn(instrument);

        mockMvc.perform(post("/api/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrumentName": "Apple Inc",
                                  "ticker": "AAPL",
                                  "currency": "USD",
                                  "assetClass": "Equity",
                                  "securityType": "Stock"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentId").value(11))
                .andExpect(jsonPath("$.instrumentName").value("Apple Inc"));
    }

    @Test
    void getInstrumentReturnsJsonResponse() throws Exception {
        Instrument instrument = buildInstrument();
        when(instrumentService.getInstrument(11)).thenReturn(instrument);

        mockMvc.perform(get("/api/instruments/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrumentId").value(11))
                .andExpect(jsonPath("$.ticker").value("AAPL"));
    }

    @Test
    void updateInstrumentReturnsJsonResponse() throws Exception {
        Instrument instrument = buildInstrument();
        instrument.setCurrency("EUR");
        when(instrumentService.updateInstrument(org.mockito.ArgumentMatchers.eq(11), org.mockito.ArgumentMatchers.any()))
                .thenReturn(instrument);

        mockMvc.perform(patch("/api/instruments/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currency": "EUR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void getInstrumentReturnsBadRequestForInvalidId() throws Exception {
        when(instrumentService.getInstrument(0))
                .thenThrow(new IllegalArgumentException("Instrument id must be a positive integer."));

        mockMvc.perform(get("/api/instruments/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Instrument id must be a positive integer."));
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

