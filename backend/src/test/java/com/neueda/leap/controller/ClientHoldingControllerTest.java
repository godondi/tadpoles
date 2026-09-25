package com.neueda.leap.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.domain.ClientHolding;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.ClientHoldingService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClientHoldingController.class)
@Import(GlobalExceptionHandler.class)
class ClientHoldingControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientHoldingService clientHoldingService;

    @Test
    void listHoldingsReturnsJsonResponse() throws Exception {
        when(clientHoldingService.listClientHoldings(7)).thenReturn(List.of(buildHolding()));

        mockMvc.perform(get("/api/clients/7/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[0].holdingId").value(13))
                .andExpect(jsonPath("$.holdings[0].quantity").value(9.5));
    }

    @Test
    void createHoldingReturnsCreatedResponse() throws Exception {
        when(clientHoldingService.createHolding(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.any()))
                .thenReturn(buildHolding());

        mockMvc.perform(post("/api/clients/7/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrumentId": 11,
                                  "quantity": 9.5,
                                  "asOfDate": "2026-09-24"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdingId").value(13))
                .andExpect(jsonPath("$.instrumentId").value(11));
    }

    @Test
    void getHoldingReturnsJsonResponse() throws Exception {
        when(clientHoldingService.getHolding(7, 13)).thenReturn(buildHolding());

        mockMvc.perform(get("/api/clients/7/holdings/13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdingId").value(13))
                .andExpect(jsonPath("$.clientId").value(7));
    }

    @Test
    void updateHoldingReturnsJsonResponse() throws Exception {
        ClientHolding holding = buildHolding();
        holding.setQuantity(new BigDecimal("12.000000"));
        when(clientHoldingService.updateHolding(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.eq(13), org.mockito.ArgumentMatchers.any()))
                .thenReturn(holding);

        mockMvc.perform(patch("/api/clients/7/holdings/13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 12.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(12.0));
    }

    @Test
    void listHoldingsReturnsBadRequestForInvalidClientId() throws Exception {
        when(clientHoldingService.listClientHoldings(0))
                .thenThrow(new IllegalArgumentException("Client id must be a positive integer."));

        mockMvc.perform(get("/api/clients/0/holdings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Client id must be a positive integer."));
    }

    private ClientHolding buildHolding() {
        ClientHolding holding = new ClientHolding();
        holding.setHoldingId(13);
        holding.setClientId(7);
        holding.setInstrumentId(11);
        holding.setQuantity(new BigDecimal("9.500000"));
        holding.setAsOfDate(LocalDate.of(2026, 9, 24));
        return holding;
    }
}

