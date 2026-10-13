package com.neueda.leap.dto;

import com.neueda.leap.domain.AuditLog;
import com.neueda.leap.domain.ClientTrade;
import java.util.List;

public record ComplianceEvidenceResponseDto(
        ClientTradeResponseDto trade,
        List<AuditLogResponseDto> auditLogs
) {
    public static ComplianceEvidenceResponseDto fromEntities(ClientTrade trade, List<AuditLog> auditLogs) {
        return new ComplianceEvidenceResponseDto(
                ClientTradeResponseDto.fromEntity(trade),
                auditLogs.stream().map(AuditLogResponseDto::fromEntity).toList()
        );
    }
}
