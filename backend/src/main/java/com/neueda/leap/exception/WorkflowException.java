package com.neueda.leap.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class WorkflowException extends ResponseStatusException {
    private final String code;

    public WorkflowException(HttpStatus status, String code, String message) {
        super(status, message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
