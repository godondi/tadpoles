package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithSubjectAndRoles;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.AppUserService;
import com.neueda.leap.service.impl.PortfolioValuationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PortfolioValuationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class PortfolioValuationControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private PortfolioValuationService valuations;

    @MockBean
    private AppUserService users;

    @Test
    void valuesOnlyThePersistedClientsAccount() throws Exception {
        var user = new AppUser();
        user.setClientId(7);
        when(users.getUserByUsername("client")).thenReturn(user);
        mvc.perform(
                        get("/api/workspace/client/portfolio/valuation?clientId=99")
                                .with(jwtWithSubjectAndRoles("client", "CLIENT")))
                .andExpect(status().isOk());
        verify(valuations).value(7);
        verifyNoMoreInteractions(valuations);
    }

    @Test
    void rejectsStaffAndUnauthenticatedAccess() throws Exception {
        mvc.perform(get("/api/workspace/client/portfolio/valuation"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/workspace/client/portfolio/valuation")
                                .with(jwtWithSubjectAndRoles("staff", "TRADING_OPS")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(valuations);
    }

    @Test
    void rejectsAnIdentityWithoutAClientAccount() throws Exception {
        when(users.getUserByUsername("client")).thenReturn(new AppUser());
        mvc.perform(
                        get("/api/workspace/client/portfolio/valuation")
                                .with(jwtWithSubjectAndRoles("client", "CLIENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(valuations);
    }
}
