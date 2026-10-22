package com.neueda.leap.service;

import com.neueda.leap.mapper.OperationsAccessMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.Map;

/** Separate proxy ensures a denial is retained even when the surrounding read rolls back. */
@Service
public class OperationsAuditService {
    private final OperationsAccessMapper db;

    public OperationsAuditService(OperationsAccessMapper db) {
        this.db = db;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(Map<String, Object> event) {
        try {
            db.audit(event);
        } catch (org.springframework.dao.DataAccessException failure) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Required access audit is unavailable");
        }
    }
}
