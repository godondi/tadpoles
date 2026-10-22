package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.exception.GlobalExceptionHandler;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffWorkspaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class StaffWorkspaceControllerTest {
    @Autowired MockMvc mvc;

    @org.junit.jupiter.api.Test
    void financeSummaryDoesNotMisrepresentAvailableReconciliation() throws Exception {
        mvc.perform(get("/api/finance/summary").with(jwtWithRoles("FINANCE")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        org.hamcrest.Matchers.containsString(
                                                "Operational reconciliation is available")));
    }

    @ParameterizedTest
    @CsvSource({
        "ANALYST,/api/analytics/summary",
        "ANALYST,/api/analytics/trends",
        "ANALYST,/api/analytics/instruments",
        "FINANCE,/api/finance/summary"
    })
    void authorizedRoleSeesUnavailableInsteadOfInventedData(String role,String path) throws Exception {
        mvc.perform(get(path).with(jwtWithRoles(role))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").isString());
        mvc.perform(get(path).with(jwtWithRoles("CLIENT"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwtWithRoles("OPERATIONS"))).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
