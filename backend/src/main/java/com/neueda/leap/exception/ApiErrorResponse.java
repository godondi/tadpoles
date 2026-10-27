package com.neueda.leap.exception;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        String code,
        String correlationId) {
    public ApiErrorResponse(
            OffsetDateTime timestamp, int status, String error, String message, String path) {
        this(
                timestamp,
                status,
                error,
                message,
                path,
                "HTTP_" + status,
                java.util.UUID.randomUUID().toString());
    }
}
