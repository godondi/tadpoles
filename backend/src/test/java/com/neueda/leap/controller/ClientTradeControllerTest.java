package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static com.neueda.leap.support.TestSecurityUtils.jwtWithSubjectAndRoles;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.ClientTradeService;
import com.neueda.leap.service.OrderFillService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(ClientTradeController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class ClientTradeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientTradeService clientTradeService;
    @MockBean
    private OrderFillService orderFillService;
    @MockBean
    private AppUserService appUserService;

    @Test
    void listTradesReturnsJsonResponse() throws Exception {
        when(clientTradeService.listClientTrades(7)).thenReturn(List.of(buildTrade()));
        when(appUserService.getUserByUsername("client01")).thenReturn(buildCurrentUser(7));

        mockMvc.perform(
                        get("/api/clients/7/trades")
                                .with(jwtWithSubjectAndRoles("client01", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trades[0].tradeId").value(21))
                .andExpect(jsonPath("$.trades[0].status").value("APPROVED"));
    }

    @Test
    void createTradeRouteIsRemoved() throws Exception {
        mockMvc.perform(
                        post("/api/clients/7/trades")
                                .with(jwtWithRoles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "instrumentId": 11,
                                          "submittedByUserId": 14,
                                          "tradeType": "BUY",
                                          "quantity": 2.5,
                                          "price": 110.25,
                                          "tradeDate": "2026-09-24",
                                          "reason": "Add position"
                                        }
                                        """))
                .andExpect(status().isMethodNotAllowed());
        org.mockito.Mockito.verifyNoInteractions(clientTradeService);
    }

    @Test
    void getTradeReturnsJsonResponse() throws Exception {
        when(clientTradeService.getTrade(7, 21)).thenReturn(buildTrade());

        mockMvc.perform(get("/api/clients/7/trades/21").with(jwtWithRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeId").value(21))
                .andExpect(jsonPath("$.instrumentId").value(11));
    }

    @Test
    void updateTradeRouteIsRemoved() throws Exception {
        mockMvc.perform(
                        patch("/api/clients/7/trades/21")
                                .with(jwtWithRoles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "status": "EXECUTED"
                                        }
                                        """))
                .andExpect(status().isMethodNotAllowed());
        org.mockito.Mockito.verifyNoInteractions(clientTradeService);
    }

    @Test
    void fillTradeRouteIsRemoved() throws Exception {
        mockMvc.perform(
                        post("/api/clients/7/trades/21/fill")
                                .with(jwtWithRoles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "approvedByUserId": 1,
                                          "price": 110.25,
                                          "executedAt": "2026-09-24T11:00:00",
                                          "reason": "Executed on market"
                                        }
                                        """))
                .andExpect(status().isNotFound());
        org.mockito.Mockito.verifyNoInteractions(orderFillService, clientTradeService);
    }

    @Test
    void getTradeReturnsForbiddenWhenClientRequestsAnotherClientsTrade() throws Exception {
        when(appUserService.getUserByUsername("client01")).thenReturn(buildCurrentUser(7));

        mockMvc.perform(
                        get("/api/clients/8/trades/21")
                                .with(jwtWithSubjectAndRoles("client01", "CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.message").value("Clients may only access their own records."));
    }

    private ClientTrade buildTrade() {
        ClientTrade trade = new ClientTrade();
        trade.setTradeId(21);
        trade.setClientId(7);
        trade.setInstrumentId(11);
        trade.setSubmittedByUserId(14);
        trade.setApprovedByUserId(1);
        trade.setTradeType("BUY");
        trade.setQuantity(new BigDecimal("2.500000"));
        trade.setPrice(new BigDecimal("110.25"));
        trade.setTradeDate(LocalDate.of(2026, 9, 24));
        trade.setStatus("APPROVED");
        trade.setExecutedAt(LocalDateTime.of(2026, 9, 24, 10, 15));
        trade.setReason("Add position");
        return trade;
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
