package com.neueda.leap.service;

import com.neueda.leap.domain.AuditLog;
import java.util.List;

public interface AuditLogService {
    List<AuditLog> listAuditLogs();
    AuditLog getAuditLog(Integer id);
}
