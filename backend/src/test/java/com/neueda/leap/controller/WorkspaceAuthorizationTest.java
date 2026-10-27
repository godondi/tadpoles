package com.neueda.leap.controller;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static com.neueda.leap.support.TestSecurityUtils.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest({ClientController.class, ClientHoldingController.class, ClientTradeController.class, InstrumentController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class WorkspaceAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockBean AppUserService users;
    @MockBean ClientService clients;
    @MockBean ClientHoldingService holdings;
    @MockBean ClientTradeService trades;
    @MockBean OrderFillService fills;
    @MockBean InstrumentService instruments;

    @ParameterizedTest
    @ValueSource(strings = {"TRADING_OPS", "COMPLIANCE", "ANALYST", "FINANCE", "OPERATIONS", "GUEST"})
    void staffCannotReadClientRecordsViaLegacyPaths(String role) throws Exception {
        for (String path : new String[]{"/api/clients/7", "/api/clients/7/holdings", "/api/clients/7/trades"}) {
            mvc.perform(get(path).with(jwtWithRoles(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/clients/7/balance").with(jwtWithRoles(role)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(clients, holdings, trades);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "TRADING_OPS", "COMPLIANCE", "ANALYST", "FINANCE", "OPERATIONS"})
    void workspaceRolesCannotMutateFinancialRecords(String role) throws Exception {
        mvc.perform(post("/api/clients").with(jwtWithRoles(role)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        for (String path : new String[]{"/api/clients/7/holdings", "/api/clients/7/trades", "/api/instruments"}) {
            mvc.perform(post(path).with(jwtWithRoles(role)).contentType("application/json").content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }
        mvc.perform(post("/api/clients/7/trades/21/fill").with(jwtWithRoles(role)).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/clients/7").with(jwtWithRoles(role)).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        for (String path : new String[]{"/api/clients/7/holdings/1", "/api/clients/7/trades/21", "/api/instruments/1"}) {
            mvc.perform(patch(path).with(jwtWithRoles(role)).contentType("application/json").content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }
        verifyNoInteractions(clients, holdings, trades, fills, instruments);
    }

    @Test void mixedRoleClientStillHasOwnershipBoundaryAndOwnReadsWork() throws Exception {
        AppUser user = new AppUser(); user.setClientId(7);
        when(users.getUserByUsername("mixed")).thenReturn(user);
        when(trades.listClientTrades(7)).thenReturn(java.util.List.of());
        mvc.perform(get("/api/clients/7/trades").with(jwtWithSubjectAndRoles("mixed", "CLIENT", "FINANCE")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/clients/8/trades").with(jwtWithSubjectAndRoles("mixed", "CLIENT", "FINANCE")))
                .andExpect(status().isForbidden());
        verify(trades, never()).listClientTrades(8);
    }
}
