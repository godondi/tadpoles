package com.neueda.leap.dto;

import com.neueda.leap.domain.AuditLog;
import java.time.LocalDateTime;

public record AuditLogResponseDto(
        Integer auditLogId,
        Integer userId,
        String entityName,
        String entityId,
        String actionType,
        LocalDateTime createdAt
) {
    public static AuditLogResponseDto fromEntity(AuditLog auditLog) {
        return new AuditLogResponseDto(
                auditLog.getAuditLogId(),
                auditLog.getUserId(),
                auditLog.getEntityName(),
                auditLog.getEntityId(),
                auditLog.getActionType(),
                auditLog.getCreatedAt()
        );
    }
}
