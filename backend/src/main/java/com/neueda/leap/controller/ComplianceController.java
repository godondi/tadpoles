package com.neueda.leap.controller;

import com.neueda.leap.domain.AuditLog;
import com.neueda.leap.domain.ClientTrade;
import com.neueda.leap.dto.ClientTradeListResponseDto;
import com.neueda.leap.dto.ComplianceEvidenceResponseDto;
import com.neueda.leap.service.AuditLogService;
import com.neueda.leap.service.ClientTradeService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {
    private final ClientTradeService clientTradeService;
    private final AuditLogService auditLogService;

    public ComplianceController(ClientTradeService clientTradeService, AuditLogService auditLogService) {
        this.clientTradeService = clientTradeService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/investigations")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ClientTradeListResponseDto listInvestigations() {
        return ClientTradeListResponseDto.fromEntities(clientTradeService.listAllTrades());
    }

    @GetMapping("/orders/{orderId}/evidence")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public ComplianceEvidenceResponseDto getOrderEvidence(@PathVariable Integer orderId) {
        ClientTrade trade = clientTradeService.getTradeById(orderId);
        List<AuditLog> tradeAuditLogs = auditLogService.listAuditLogs().stream()
                .filter((log) -> log.getEntityName() != null && log.getEntityName().toLowerCase().contains("trade"))
                .filter((log) -> log.getEntityId() != null && log.getEntityId().equals(String.valueOf(orderId)))
                .toList();
        return ComplianceEvidenceResponseDto.fromEntities(trade, tradeAuditLogs);
    }
}
