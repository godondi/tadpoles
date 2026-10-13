package com.neueda.leap.exception;

public class AuditLogNotFoundException extends RuntimeException {
    public AuditLogNotFoundException(Integer id) {
        super("Audit log with id " + id + " was not found.");
    }
}
