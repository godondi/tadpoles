package com.neueda.leap.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.domain.Client;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.ClientService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClientController.class)
@Import(GlobalExceptionHandler.class)
class ClientControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    @Test
    void listClientsReturnsJsonResponse() throws Exception {
        Client client = buildClient();
        when(clientService.listClients()).thenReturn(List.of(client));

        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clients[0].clientId").value(7))
                .andExpect(jsonPath("$.clients[0].clientName").value("Alice Investor"))
                .andExpect(jsonPath("$.clients[0].cashBalance").value(1200.5));
    }

    @Test
    void createClientReturnsCreatedResponse() throws Exception {
        Client client = buildClient();
        when(clientService.createClient(org.mockito.ArgumentMatchers.any())).thenReturn(client);

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientName": "Alice Investor",
                                  "advisorId": 3,
                                  "modelPortfolioId": 5,
                                  "createdByUserId": 1,
                                  "cashBalance": 1200.50
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(7))
                .andExpect(jsonPath("$.clientName").value("Alice Investor"));
    }

    @Test
    void getClientReturnsJsonResponse() throws Exception {
        Client client = buildClient();
        when(clientService.getClient(7)).thenReturn(client);

        mockMvc.perform(get("/api/clients/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(7))
                .andExpect(jsonPath("$.clientName").value("Alice Investor"))
                .andExpect(jsonPath("$.advisorId").value(3))
                .andExpect(jsonPath("$.modelPortfolioId").value(5))
                .andExpect(jsonPath("$.cashBalance").value(1200.5));
    }

    @Test
    void updateClientReturnsJsonResponse() throws Exception {
        Client client = buildClient();
        client.setClientName("Alice Updated");
        when(clientService.updateClient(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.any()))
                .thenReturn(client);

        mockMvc.perform(patch("/api/clients/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientName": "Alice Updated",
                                  "cashBalance": 1400.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Alice Updated"));
    }

    @Test
    void getClientBalanceReturnsJsonResponse() throws Exception {
        when(clientService.getClientBalance(7)).thenReturn(new BigDecimal("1200.50"));

        mockMvc.perform(get("/api/clients/7/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(7))
                .andExpect(jsonPath("$.cashBalance").value(1200.5));
    }

    @Test
    void getClientReturnsBadRequestForInvalidId() throws Exception {
        when(clientService.getClient(0)).thenThrow(new IllegalArgumentException("Client id must be a positive integer."));

        mockMvc.perform(get("/api/clients/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Client id must be a positive integer."));
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
