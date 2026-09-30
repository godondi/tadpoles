package com.neueda.leap.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.domain.Advisor;
import com.neueda.leap.domain.Client;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.AdvisorService;
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

@WebMvcTest(AdvisorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdvisorControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdvisorService advisorService;

    @Test
    void listAdvisorsReturnsJsonResponse() throws Exception {
        when(advisorService.listAdvisors()).thenReturn(List.of(buildAdvisor()));

        mockMvc.perform(get("/api/advisors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advisors[0].advisorId").value(3))
                .andExpect(jsonPath("$.advisors[0].advisorName").value("Advisor One"))
                .andExpect(jsonPath("$.advisors[0].userId").value(1));
    }

    @Test
    void createAdvisorReturnsCreatedResponse() throws Exception {
        when(advisorService.createAdvisor(org.mockito.ArgumentMatchers.any())).thenReturn(buildAdvisor());

        mockMvc.perform(post("/api/advisors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "advisorName": "Advisor One",
                                  "userId": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.advisorId").value(3))
                .andExpect(jsonPath("$.advisorName").value("Advisor One"));
    }

    @Test
    void getAdvisorReturnsJsonResponse() throws Exception {
        when(advisorService.getAdvisor(3)).thenReturn(buildAdvisor());

        mockMvc.perform(get("/api/advisors/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advisorId").value(3))
                .andExpect(jsonPath("$.advisorName").value("Advisor One"))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void updateAdvisorReturnsJsonResponse() throws Exception {
        Advisor advisor = buildAdvisor();
        advisor.setAdvisorName("Advisor Updated");
        when(advisorService.updateAdvisor(org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.any()))
                .thenReturn(advisor);

        mockMvc.perform(patch("/api/advisors/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "advisorName": "Advisor Updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advisorName").value("Advisor Updated"));
    }

    @Test
    void listAdvisorClientsReturnsJsonResponse() throws Exception {
        when(advisorService.listAdvisorClients(3)).thenReturn(List.of(buildClient()));

        mockMvc.perform(get("/api/advisors/3/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[0].clientId").value(7))
                .andExpect(jsonPath("$.clients[0].clientName").value("Alice Investor"))
                .andExpect(jsonPath("$.clients[0].advisorId").value(3));
    }

    @Test
    void getAdvisorReturnsBadRequestForInvalidId() throws Exception {
        when(advisorService.getAdvisor(0))
                .thenThrow(new IllegalArgumentException("Advisor id must be a positive integer."));

        mockMvc.perform(get("/api/advisors/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Advisor id must be a positive integer."));
    }

    private Advisor buildAdvisor() {
        Advisor advisor = new Advisor();
        advisor.setAdvisorId(3);
        advisor.setAdvisorName("Advisor One");
        advisor.setUserId(1);
        return advisor;
    }

    private Client buildClient() {
        Client client = new Client();
        client.setClientId(7);
        client.setClientName("Alice Investor");
        client.setAdvisorId(3);
        client.setModelPortfolioId(5);
        client.setCashBalance(new BigDecimal("1200.50"));
        return client;
    }
}
