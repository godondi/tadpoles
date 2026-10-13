package com.neueda.leap.dto;

import com.neueda.leap.domain.AuditLog;
import java.util.List;

public record AuditLogListResponseDto(
        List<AuditLogResponseDto> auditLogs
) {
    public static AuditLogListResponseDto fromEntities(List<AuditLog> auditLogs) {
        return new AuditLogListResponseDto(
                auditLogs.stream().map(AuditLogResponseDto::fromEntity).toList()
        );
    }
}
