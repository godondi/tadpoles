package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.dto.ComplianceDtos.*;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.ComplianceAccessService;
import com.neueda.leap.service.ComplianceEvidenceService;
import com.neueda.leap.service.ComplianceExportService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ComplianceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class ComplianceControllerTest {
    @Autowired MockMvc mvc;

    @MockBean ComplianceAccessService access;
    @MockBean ComplianceEvidenceService evidence;
    @MockBean ComplianceExportService exports;

    @Test
    void policyAndContextUseComplianceBoundary() throws Exception {
        when(access.policy())
                .thenReturn(new AccessPolicy(true, List.of("ORDER_REVIEW"), "compliance-v1", "Assigned accounts only"));
        when(access.create(any()))
                .thenReturn(
                        new AccessContext(
                                UUID.randomUUID(),
                                "ORDER_REVIEW",
                                "CASE-12345678",
                                "Assigned accounts only",
                                List.of("EVIDENCE_READ"),
                                Instant.parse("2026-10-22T19:00:00Z"),
                                "compliance-v1"));

        mvc.perform(get("/api/compliance/access-policy").with(jwtWithRoles("COMPLIANCE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.scopeLabel").value("Assigned accounts only"));
        mvc.perform(
                        post("/api/compliance/access-contexts")
                                .with(jwtWithRoles("COMPLIANCE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"purposeCode":"ORDER_REVIEW","caseReference":"CASE-12345678"}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purposeCode").value("ORDER_REVIEW"))
                .andExpect(jsonPath("$.permissions[0]").value("EVIDENCE_READ"));
        verify(access).create(any());
    }

    @Test
    void protectedReadsCarryOpaqueContextAndReturnNoStore() throws Exception {
        UUID context = UUID.randomUUID();
        when(evidence.search(eq(context), anyMap()))
                .thenReturn(
                        new SearchPage(
                                List.of(),
                                0,
                                25,
                                0,
                                "Assigned accounts only",
                                Instant.parse("2026-10-22T18:00:00Z")));

        mvc.perform(
                        get("/api/compliance/investigations")
                                .with(jwtWithRoles("COMPLIANCE"))
                                .header("X-Compliance-Context", context)
                                .queryParam("page", "0")
                                .queryParam("pageSize", "25"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(0));
        verify(evidence).search(eq(context), anyMap());
    }

    @Test
    void nonComplianceRoleCannotReachComplianceServices() throws Exception {
        mvc.perform(get("/api/compliance/access-policy").with(jwtWithRoles("CLIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/compliance/investigations")
                                .with(jwtWithRoles("TRADING_OPS"))
                                .header("X-Compliance-Context", UUID.randomUUID())
                                .queryParam("page", "0")
                                .queryParam("pageSize", "25"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(access, evidence, exports);
    }
}
