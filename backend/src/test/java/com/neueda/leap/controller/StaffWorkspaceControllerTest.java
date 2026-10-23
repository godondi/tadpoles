package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.dto.FinanceSummaryResponseDto;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.FinanceSummaryService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StaffWorkspaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class StaffWorkspaceControllerTest {
    @Autowired MockMvc mvc;
    @MockBean FinanceSummaryService financeSummary;

    @org.junit.jupiter.api.Test
    void financeSummaryReturnsScopedAggregateContractForAnInclusiveUtcRange() throws Exception {
        LocalDate from = LocalDate.parse("2026-10-01");
        LocalDate to = LocalDate.parse("2026-10-22");
        when(financeSummary.summary(from, to))
            .thenReturn(
                new FinanceSummaryResponseDto(
                    "finance-summary-v1",
                    "CURRENT",
                    "USD",
                    new FinanceSummaryResponseDto.Period(from, to, "UTC"),
                    Instant.parse("2026-10-22T18:00:00Z"),
                    Instant.parse("2026-10-22T18:01:00Z"),
                    "finance-summary-v1",
                    "primary-postgresql-ledger",
                    List.of("Internal records only"),
                    new FinanceSummaryResponseDto.Totals(
                        "100.00", "25.00", "51.00", "0.00", 1)));
        mvc.perform(
                        get("/api/finance/summary")
                                .param("from", "2026-10-01")
                                .param("to", "2026-10-22")
                                .with(jwtWithRoles("FINANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("finance-summary-v1"))
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.period.from").value("2026-10-01"))
                .andExpect(jsonPath("$.period.to").value("2026-10-22"))
                .andExpect(jsonPath("$.period.timeZone").value("UTC"))
                .andExpect(jsonPath("$.dataAsOf").isString())
                .andExpect(jsonPath("$.generatedAt").isString())
                .andExpect(jsonPath("$.definitionVersion").value("finance-summary-v1"))
                .andExpect(jsonPath("$.source").value("primary-postgresql-ledger"))
                .andExpect(jsonPath("$.totals.settledDeposits").isString())
                .andExpect(jsonPath("$.totals.settledWithdrawals").isString())
                .andExpect(jsonPath("$.totals.grossBuyConsideration").isString())
                .andExpect(jsonPath("$.totals.grossSellConsideration").isString())
                .andExpect(jsonPath("$.totals.bookedFillCount").isNumber())
                .andExpect(jsonPath("$.totals.fees").doesNotExist());
        verify(financeSummary).summary(from, to);
    }

    @org.junit.jupiter.api.Test
    void financeSummaryRequiresBothUtcDateParameters() throws Exception {
        mvc.perform(
                        get("/api/finance/summary")
                                .param("from", "2026-10-01")
                                .with(jwtWithRoles("FINANCE")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(financeSummary);
    }

    @org.junit.jupiter.api.Test
    void financeSummaryRejectsMalformedIsoDateBeforeReading() throws Exception {
        mvc.perform(
                        get("/api/finance/summary")
                                .param("from", "not-a-date")
                                .param("to", "2026-10-22")
                                .with(jwtWithRoles("FINANCE")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(financeSummary);
    }

    @org.junit.jupiter.api.Test
    void financeSummaryDeniesUnapprovedRoleCombinationsBeforeReading() throws Exception {
        for (String role :
                new String[] {"CLIENT", "ANALYST", "ADVISOR", "COMPLIANCE", "TRADING_OPS", "ADMIN"}) {
            mvc.perform(
                            get("/api/finance/summary")
                                    .param("from", "2026-10-01")
                                    .param("to", "2026-10-22")
                                    .with(jwtWithRoles(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(
                        get("/api/finance/summary")
                                .param("from", "2026-10-01")
                                .param("to", "2026-10-22")
                                .with(jwtWithRoles("FINANCE", "CLIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/finance/summary")
                                .param("from", "2026-10-01")
                                .param("to", "2026-10-22"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(financeSummary);
    }

    @ParameterizedTest
    @CsvSource({
        "COMPLIANCE,/api/compliance/investigations",
        "COMPLIANCE,/api/compliance/orders/7/evidence",
        "COMPLIANCE,/api/compliance/evidence",
        "ANALYST,/api/analytics/summary",
        "ANALYST,/api/analytics/trends",
        "ANALYST,/api/analytics/instruments"
    })
    void authorizedRoleSeesUnavailableInsteadOfInventedData(String role,String path) throws Exception {
        mvc.perform(get(path).with(jwtWithRoles(role))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").isString());
        mvc.perform(get(path).with(jwtWithRoles("CLIENT"))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(jwtWithRoles("OPERATIONS"))).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
