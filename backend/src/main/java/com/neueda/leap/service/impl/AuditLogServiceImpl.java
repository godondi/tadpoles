package com.neueda.leap.service.impl;

import com.neueda.leap.domain.AuditLog;
import com.neueda.leap.exception.AuditLogNotFoundException;
import com.neueda.leap.mapper.AuditLogMapper;
import com.neueda.leap.service.AuditLogService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl implements AuditLogService {
    private final AuditLogMapper auditLogMapper;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public List<AuditLog> listAuditLogs() {
        return auditLogMapper.listAuditLogs();
    }

    @Override
    public AuditLog getAuditLog(Integer id) {
        validateId(id);

        AuditLog auditLog = auditLogMapper.getAuditLog(id);
        if (auditLog == null) {
            throw new AuditLogNotFoundException(id);
        }

        return auditLog;
    }

    private void validateId(Integer id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("Audit log id must be a positive integer.");
        }
    }
}
