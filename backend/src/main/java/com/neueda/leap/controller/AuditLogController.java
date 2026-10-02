package com.neueda.leap.controller;

import com.neueda.leap.dto.AuditLogListResponseDto;
import com.neueda.leap.service.AuditLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {
    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public AuditLogListResponseDto listAuditLogs() {
        return AuditLogListResponseDto.fromEntities(auditLogService.listAuditLogs());
    }
}
