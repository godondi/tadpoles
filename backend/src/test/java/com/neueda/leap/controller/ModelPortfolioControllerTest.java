package com.neueda.leap.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.ModelPortfolio;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.ModelPortfolioService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ModelPortfolioController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class ModelPortfolioControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ModelPortfolioService modelPortfolioService;

    @Test
    void listModelPortfoliosReturnsJsonResponse() throws Exception {
        when(modelPortfolioService.listModelPortfolios()).thenReturn(List.of(buildPortfolio()));

        mockMvc.perform(get("/api/model-portfolios")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelPortfolios[0].modelPortfolioId").value(5))
                .andExpect(jsonPath("$.modelPortfolios[0].modelName").value("Growth"));
    }

    @Test
    void createModelPortfolioReturnsCreatedResponse() throws Exception {
        when(modelPortfolioService.createModelPortfolio(any())).thenReturn(buildPortfolio());

        mockMvc.perform(post("/api/model-portfolios")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelName": "Growth",
                                  "description": "Growth portfolio",
                                  "createdByUserId": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.modelPortfolioId").value(5))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void getModelPortfolioReturnsJsonResponse() throws Exception {
        when(modelPortfolioService.getModelPortfolio(5)).thenReturn(buildPortfolio());

        mockMvc.perform(get("/api/model-portfolios/5")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelPortfolioId").value(5))
                .andExpect(jsonPath("$.description").value("Growth portfolio"));
    }

    @Test
    void updateModelPortfolioReturnsJsonResponse() throws Exception {
        ModelPortfolio updated = buildPortfolio();
        updated.setDescription("Updated growth portfolio");
        when(modelPortfolioService.updateModelPortfolio(eq(5), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/model-portfolios/5")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Updated growth portfolio",
                                  "isActive": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated growth portfolio"));
    }

    @Test
    void getModelPortfolioReturnsBadRequestForInvalidId() throws Exception {
        when(modelPortfolioService.getModelPortfolio(0))
                .thenThrow(new IllegalArgumentException("Model portfolio id must be a positive integer."));

        mockMvc.perform(get("/api/model-portfolios/0")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("ADMIN")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Model portfolio id must be a positive integer."));
    }

    private ModelPortfolio buildPortfolio() {
        ModelPortfolio portfolio = new ModelPortfolio();
        portfolio.setModelPortfolioId(5);
        portfolio.setModelName("Growth");
        portfolio.setDescription("Growth portfolio");
        portfolio.setIsActive(true);
        portfolio.setCreatedByUserId(1);
        portfolio.setCreatedAt(LocalDateTime.of(2026, 9, 24, 10, 15));
        return portfolio;
    }
}
