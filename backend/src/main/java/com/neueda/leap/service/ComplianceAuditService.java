package com.neueda.leap.service;

import com.neueda.leap.mapper.ComplianceAccessMapper;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Persists denied attempts independently so a failed audit never becomes a successful read. */
@Service
public class ComplianceAuditService {
    private final ComplianceAccessMapper db;

    public ComplianceAuditService(ComplianceAccessMapper db) {
        this.db = db;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(Map<String, Object> event) {
        try {
            db.audit(event);
        } catch (DataAccessException failure) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Required access audit is unavailable");
        }
    }
}
