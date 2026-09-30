package com.neueda.leap.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.domain.ModelPortfolioHolding;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.ModelPortfolioHoldingService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ModelPortfolioHoldingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ModelPortfolioHoldingControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelPortfolioHoldingService modelPortfolioHoldingService;

    @Test
    void listModelPortfolioHoldingsReturnsJsonResponse() throws Exception {
        when(modelPortfolioHoldingService.listModelPortfolioHoldings(5)).thenReturn(List.of(buildHolding()));

        mockMvc.perform(get("/api/model-portfolios/5/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[0].modelPortfolioId").value(5))
                .andExpect(jsonPath("$.holdings[0].targetWeightPct").value(60.0));
    }

    @Test
    void createModelPortfolioHoldingReturnsCreatedResponse() throws Exception {
        when(modelPortfolioHoldingService.createModelPortfolioHolding(eq(5), any())).thenReturn(buildHolding());

        mockMvc.perform(post("/api/model-portfolios/5/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrumentId": 11,
                                  "targetWeightPct": 60.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instrumentId").value(11))
                .andExpect(jsonPath("$.targetWeightPct").value(60.0));
    }

    @Test
    void getModelPortfolioHoldingReturnsJsonResponse() throws Exception {
        when(modelPortfolioHoldingService.getModelPortfolioHolding(5, 11)).thenReturn(buildHolding());

        mockMvc.perform(get("/api/model-portfolios/5/holdings/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelPortfolioId").value(5))
                .andExpect(jsonPath("$.instrumentId").value(11));
    }

    @Test
    void updateModelPortfolioHoldingReturnsJsonResponse() throws Exception {
        ModelPortfolioHolding updated = buildHolding();
        updated.setTargetWeightPct(new BigDecimal("55.50"));
        when(modelPortfolioHoldingService.updateModelPortfolioHolding(eq(5), eq(11), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/model-portfolios/5/holdings/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetWeightPct": 55.5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetWeightPct").value(55.5));
    }

    @Test
    void listModelPortfolioHoldingsReturnsBadRequestForInvalidPortfolioId() throws Exception {
        when(modelPortfolioHoldingService.listModelPortfolioHoldings(0))
                .thenThrow(new IllegalArgumentException("Model portfolio id must be a positive integer."));

        mockMvc.perform(get("/api/model-portfolios/0/holdings"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Model portfolio id must be a positive integer."));
    }

    private ModelPortfolioHolding buildHolding() {
        ModelPortfolioHolding holding = new ModelPortfolioHolding();
        holding.setModelPortfolioId(5);
        holding.setInstrumentId(11);
        holding.setTargetWeightPct(new BigDecimal("60.00"));
        return holding;
    }
}

