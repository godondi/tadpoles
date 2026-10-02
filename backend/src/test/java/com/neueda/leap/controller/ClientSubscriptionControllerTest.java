package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.ClientSubscription;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.ClientSubscriptionService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClientSubscriptionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class ClientSubscriptionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientSubscriptionService clientSubscriptionService;

    @Test
    void listClientSubscriptionsReturnsJsonResponse() throws Exception {
        when(clientSubscriptionService.listClientSubscriptions(7)).thenReturn(List.of(buildSubscription()));

        mockMvc.perform(get("/api/clients/7/subscriptions")
                        .with(jwtWithRoles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions[0].subscriptionId").value(9))
                .andExpect(jsonPath("$.subscriptions[0].modelPortfolioId").value(5))
                .andExpect(jsonPath("$.subscriptions[0].status").value("ACTIVE"));
    }

    @Test
    void createClientSubscriptionReturnsCreatedResponse() throws Exception {
        when(clientSubscriptionService.createClientSubscription(org.mockito.ArgumentMatchers.eq(7), org.mockito.ArgumentMatchers.any()))
                .thenReturn(buildSubscription());

        mockMvc.perform(post("/api/clients/7/subscriptions")
                        .with(jwtWithRoles("ADVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelPortfolioId": 5,
                                  "subscribedDate": "2026-09-24",
                                  "approvedByUserId": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriptionId").value(9))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void listClientSubscriptionsReturnsBadRequestForInvalidClientId() throws Exception {
        when(clientSubscriptionService.listClientSubscriptions(0))
                .thenThrow(new IllegalArgumentException("Client id must be a positive integer."));

        mockMvc.perform(get("/api/clients/0/subscriptions")
                        .with(jwtWithRoles("CLIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Client id must be a positive integer."));
    }

    private ClientSubscription buildSubscription() {
        ClientSubscription subscription = new ClientSubscription();
        subscription.setSubscriptionId(9);
        subscription.setClientId(7);
        subscription.setModelPortfolioId(5);
        subscription.setSubscribedDate(LocalDate.of(2026, 9, 24));
        subscription.setEndedDate(null);
        subscription.setStatus("ACTIVE");
        subscription.setApprovedByUserId(1);
        return subscription;
    }
}
