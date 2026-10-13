package com.neueda.leap.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.neueda.leap.domain.AuditLog;
import com.neueda.leap.exception.AuditLogNotFoundException;
import com.neueda.leap.mapper.AuditLogMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {
    @Mock
    private AuditLogMapper auditLogMapper;

    private AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogServiceImpl(auditLogMapper);
    }

    @Test
    void listAuditLogsReturnsAuditLogs() {
        when(auditLogMapper.listAuditLogs()).thenReturn(List.of(buildAuditLog()));

        List<AuditLog> results = auditLogService.listAuditLogs();

        assertEquals(1, results.size());
        assertEquals(31, results.get(0).getAuditLogId());
    }

    @Test
    void getAuditLogReturnsAuditLogWhenFound() {
        when(auditLogMapper.getAuditLog(31)).thenReturn(buildAuditLog());

        AuditLog result = auditLogService.getAuditLog(31);

        assertEquals(31, result.getAuditLogId());
        assertEquals("CREATE", result.getActionType());
    }

    @Test
    void getAuditLogThrowsWhenMissing() {
        when(auditLogMapper.getAuditLog(99)).thenReturn(null);

        assertThrows(AuditLogNotFoundException.class, () -> auditLogService.getAuditLog(99));
    }

    @Test
    void getAuditLogThrowsOnInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> auditLogService.getAuditLog(0));
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
