package com.neueda.leap.controller;

import com.neueda.leap.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Workspace-local adaptation: never change shared error behavior for other roles. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AdvisorWorkspaceController.class, ClientAdvisoryController.class})
public class AdvisorWorkspaceExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> invalid(Exception error, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                "Invalid advisory request. IDs and versions must be JSON integers and reasoning must be valid.", request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiErrorResponse> unavailable(DataAccessException error, HttpServletRequest request) {
        return error instanceof DataIntegrityViolationException
                ? response(HttpStatus.CONFLICT, "The advisory record or eligibility changed. Refresh before retrying.", request)
                : response(HttpStatus.SERVICE_UNAVAILABLE, "Advisory information is temporarily unavailable. Try again.", request);
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}