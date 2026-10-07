package com.neueda.leap.controller;

import static org.mockito.Mockito.when;
import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static com.neueda.leap.support.TestSecurityUtils.jwtWithSubjectAndRoles;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.domain.Client;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.ClientService;
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

@WebMvcTest(ClientController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class ClientControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;
    @MockBean
    private AppUserService appUserService;

    @Test
    void listClientsReturnsJsonResponse() throws Exception {
        Client client = buildClient();
        when(clientService.listClients()).thenReturn(List.of(client));

        mockMvc.perform(get("/api/clients")
                        .with(jwtWithRoles("ADMIN")))
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
                        .with(jwtWithRoles("ADMIN"))
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

        mockMvc.perform(get("/api/clients/7")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(7))
                .andExpect(jsonPath("$.clientName").value("Alice Investor"))
                .andExpect(jsonPath("$.advisorId").value(3))
                .andExpect(jsonPath("$.modelPortfolioId").value(5))
                .andExpect(jsonPath("$.cashBalance").value(1200.5));
    }

    @Test
    void getClientReturnsForbiddenWhenClientRequestsAnotherClient() throws Exception {
        when(appUserService.getUserByUsername("client01")).thenReturn(buildCurrentUser(7));

        mockMvc.perform(get("/api/clients/8")
                        .with(jwtWithSubjectAndRoles("client01", "CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Clients may only access their own records."));
    }

    @Test
    void updateClientReturnsJsonResponse() throws Exception {
        Client client = buildClient();
        client.setClientName("Alice Updated");
        when(clientService.updateClient(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.any()))
                .thenReturn(client);

        mockMvc.perform(patch("/api/clients/7")
                        .with(jwtWithRoles("ADMIN"))
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

        mockMvc.perform(get("/api/clients/7/balance")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(7))
                .andExpect(jsonPath("$.cashBalance").value(1200.5));
    }

    @Test
    void getClientReturnsBadRequestForInvalidId() throws Exception {
        when(clientService.getClient(0)).thenThrow(new IllegalArgumentException("Client id must be a positive integer."));

        mockMvc.perform(get("/api/clients/0")
                        .with(jwtWithRoles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Client id must be a positive integer."));
    }

    private Client buildClient() {
        Client client = new Client();
        client.setClientId(7);
        client.setUserId(14);
        client.setClientName("Alice Investor");
        client.setAdvisorId(3);
        client.setModelPortfolioId(5);
        client.setCashBalance(new BigDecimal("1200.50"));
        return client;
    }

    private AppUser buildCurrentUser(Integer clientId) {
        AppUser user = new AppUser();
        user.setUserId(14);
        user.setUsername("client01");
        user.setEmail("client01@tadpoles.dev");
        user.setDisplayName("Client User");
        user.setEnabled(true);
        user.setClientId(clientId);
        user.setCreatedAt(LocalDateTime.of(2026, 9, 24, 8, 20));
        user.setUpdatedAt(LocalDateTime.of(2026, 9, 24, 8, 20));
        return user;
    }
}