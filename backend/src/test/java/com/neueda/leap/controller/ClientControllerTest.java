package com.neueda.leap.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.domain.Client;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.ClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClientController.class)
@Import(GlobalExceptionHandler.class)
class ClientControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    @Test
    void getClientReturnsJsonResponse() throws Exception {
        Client client = new Client();
        client.setClientId(7);
        client.setClientName("Alice Investor");
        client.setAdvisorId(3);
        client.setModelPortfolioId(5);

        when(clientService.getClient(7)).thenReturn(client);

        mockMvc.perform(get("/api/client/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(7))
                .andExpect(jsonPath("$.clientName").value("Alice Investor"))
                .andExpect(jsonPath("$.advisorId").value(3))
                .andExpect(jsonPath("$.modelPortfolioId").value(5));
    }

    @Test
    void getClientReturnsBadRequestForInvalidId() throws Exception {
        when(clientService.getClient(0)).thenThrow(new IllegalArgumentException("Client id must be a positive integer."));

        mockMvc.perform(get("/api/client/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Client id must be a positive integer."));
    }
}



