package com.neueda.leap.controller;

import com.neueda.leap.dto.AuditLogListResponseDto;
import com.neueda.leap.service.AuditLogService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    public AuditLogListResponseDto listAuditLogs(@AuthenticationPrincipal Jwt jwt) {
        SecurityRoleSupport.requireAnyRole(jwt, "ADMIN", "AUDITOR");
        return AuditLogListResponseDto.fromEntities(auditLogService.listAuditLogs());
    }
}
