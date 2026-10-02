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
import com.neueda.leap.domain.TradeSuggestion;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.TradeSuggestionService;
import java.math.BigDecimal;
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

@WebMvcTest(TradeSuggestionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class TradeSuggestionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradeSuggestionService tradeSuggestionService;

    @Test
    void createTradeSuggestionReturnsCreatedResponseForAdvisor() throws Exception {
        when(tradeSuggestionService.createTradeSuggestion(eq(3), eq(7), any())).thenReturn(buildSuggestion());

        mockMvc.perform(post("/api/advisors/3/clients/7/trade-suggestions")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("ADVISOR"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrumentId": 11,
                                  "tradeType": "BUY",
                                  "quantity": 5.5,
                                  "proposedPrice": 189.25,
                                  "notes": "Increase technology exposure"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestionId").value(15))
                .andExpect(jsonPath("$.status").value("SUGGESTED"));
    }

    @Test
    void listClientTradeSuggestionsReturnsJsonResponseForAuthorizedUser() throws Exception {
        when(tradeSuggestionService.listClientTradeSuggestions(7)).thenReturn(List.of(buildSuggestion()));

        mockMvc.perform(get("/api/clients/7/trade-suggestions")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("ANALYST")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].suggestionId").value(15))
                .andExpect(jsonPath("$.suggestions[0].tradeType").value("BUY"));
    }

    @Test
    void getTradeSuggestionReturnsJsonResponseForAuthorizedUser() throws Exception {
        when(tradeSuggestionService.getTradeSuggestion(15)).thenReturn(buildSuggestion());

        mockMvc.perform(get("/api/trade-suggestions/15")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestionId").value(15))
                .andExpect(jsonPath("$.clientId").value(7));
    }

    @Test
    void updateTradeSuggestionReturnsJsonResponseForClient() throws Exception {
        TradeSuggestion updated = buildSuggestion();
        updated.setStatus("ACCEPTED");
        updated.setNotes("Client approved");
        when(tradeSuggestionService.updateTradeSuggestion(eq(15), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/trade-suggestions/15")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("CLIENT"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACCEPTED",
                                  "notes": "Client approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.notes").value("Client approved"));
    }

    @Test
    void createTradeSuggestionReturnsUnauthorizedWhenAdvisorRoleMissing() throws Exception {
        mockMvc.perform(post("/api/advisors/3/clients/7/trade-suggestions")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("CLIENT"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instrumentId": 11,
                                  "tradeType": "BUY",
                                  "quantity": 5.5
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("ADVISOR role required"));
    }

    @Test
    void listClientTradeSuggestionsReturnsUnauthorizedWhenRoleMissing() throws Exception {
        mockMvc.perform(get("/api/clients/7/trade-suggestions")
                        .with(jwt().jwt(token -> token.claim("roles", List.of("GUEST")))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("ADMIN or AUDITOR or ANALYST or ADVISOR or CLIENT role required"));
    }

    private TradeSuggestion buildSuggestion() {
        TradeSuggestion suggestion = new TradeSuggestion();
        suggestion.setSuggestionId(15);
        suggestion.setAdvisorId(3);
        suggestion.setClientId(7);
        suggestion.setInstrumentId(11);
        suggestion.setTradeType("BUY");
        suggestion.setQuantity(new BigDecimal("5.500000"));
        suggestion.setProposedPrice(new BigDecimal("189.25"));
        suggestion.setSuggestedAt(LocalDateTime.of(2026, 9, 24, 11, 0));
        suggestion.setStatus("SUGGESTED");
        suggestion.setNotes("Increase technology exposure");
        return suggestion;
    }
}

