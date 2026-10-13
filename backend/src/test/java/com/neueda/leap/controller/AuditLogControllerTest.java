package com.neueda.leap.controller;

import static com.neueda.leap.support.TestSecurityUtils.jwtWithRoles;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.domain.AuditLog;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.service.AuditLogService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditLogController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-test-jwt-secret-123456")
class AuditLogControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    void listAuditLogsReturnsJsonResponseForAuditor() throws Exception {
        when(auditLogService.listAuditLogs()).thenReturn(List.of(buildAuditLog()));

        mockMvc.perform(get("/api/audit-logs")
                        .with(jwtWithRoles("AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs[0].auditLogId").value(31))
                .andExpect(jsonPath("$.auditLogs[0].entityName").value("client_subscriptions"))
                .andExpect(jsonPath("$.auditLogs[0].actionType").value("CREATE"));
    }

    @Test
    void listAuditLogsReturnsUnauthorizedWhenMissingToken() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Missing token"));
    }

    @Test
    void listAuditLogsReturnsUnauthorizedWhenRoleMissing() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .with(jwtWithRoles("CLIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access Denied"));
    }

    private AuditLog buildAuditLog() {
        AuditLog auditLog = new AuditLog();
        auditLog.setAuditLogId(31);
        auditLog.setUserId(1);
        auditLog.setEntityName("client_subscriptions");
        auditLog.setEntityId("9");
        auditLog.setActionType("CREATE");
        auditLog.setOldValues(null);
        auditLog.setNewValues("{\"status\":\"ACTIVE\"}");
        auditLog.setCreatedAt(LocalDateTime.of(2026, 9, 24, 12, 0));
        return auditLog;
    }
}
