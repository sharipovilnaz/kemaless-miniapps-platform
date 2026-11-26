package com.kemaless.core.auth.controller.handler;

import com.kemaless.core.auth.dto.api.ErrorDto;
import com.kemaless.core.auth.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorDto.FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> ErrorDto.FieldErrorDto.builder()
                        .field(err.getField())
                        .message(err.getDefaultMessage())
                        .build())
                .toList();
        return buildValidationResponse("Validation data error", request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        List<ErrorDto.FieldErrorDto> errors = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return ErrorDto.FieldErrorDto.builder()
                            .field(field)
                            .message(violation.getMessage())
                            .build();
                })
                .toList();
        return buildValidationResponse("Validation parameter error", request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorDto> handleMethodValidationException(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        log.warn("Handler method validation failed: {}", ex.getMessage());
        List<ErrorDto.FieldErrorDto> errors = new ArrayList<>();
        ex.getValueResults().forEach(result -> {
            String field = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err ->
                    errors.add(ErrorDto.FieldErrorDto.builder()
                            .field(field)
                            .message(err.getDefaultMessage())
                            .build())
            );
        });
        return buildValidationResponse("Handler method validation failed", request, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDto> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch for parameter {}: {}", ex.getName(), ex.getMessage());
        String message = String.format("Invalid data format. Expected type: %s",
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        List<ErrorDto.FieldErrorDto> errors = List.of(ErrorDto.FieldErrorDto.builder()
                .field(ex.getName())
                .message(message)
                .build());
        return buildValidationResponse("Type mismatch error", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleMalformedRequest(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request payload: {}", ex.getMessage());
        return buildValidationResponse("Malformed JSON request or invalid parameters format",
                request, Collections.emptyList());
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorDto> handleServiceException(ServiceException e, HttpServletRequest request) {
        log.warn("Business logic exception: {} (Status: {})", e.getMessage(), e.getStatus());
        return buildResponse(e.getErrorCode(), e.getMessage(), HttpStatus.valueOf(e.getStatus()), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        log.warn("Access denied for {}: {}", request.getRequestURI(), e.getMessage());
        return buildResponse("AccessDenied", "Access denied", HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("Bad request: {}", e.getMessage());
        return buildResponse("BadRequest", e.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDto> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("Method not supported: {} {}", request.getMethod(), request.getRequestURI());
        return buildResponse(
                "MethodNotAllowed",
                "HTTP method is not supported for this endpoint",
                HttpStatus.METHOD_NOT_ALLOWED,
                request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorDto> handleDataAccessException(DataAccessException e, HttpServletRequest request) {
        log.error("Database error (DataAccessException): ", e);
        return buildResponse("DatabaseError",
                "A data integrity error occurred. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDto> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException e, HttpServletRequest request) {
        log.error("Optimistic locking failure: ", e);
        return buildResponse("OptimisticLockingFailure",
                "The entity has been modified by another user. Please refresh the page and try again.",
                HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleException(Exception e, HttpServletRequest request) {
        String incidentId = UUID.randomUUID().toString();
        log.error("Unexpected server error. IncidentID: {}", incidentId, e);
        return buildResponse(
                "InternalServerError",
                "An unexpected error occurred. Please contact support and mention Incident ID: %s"
                        .formatted(incidentId),
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ErrorDto> buildResponse(
            String errorName, String message, HttpStatus status, HttpServletRequest request) {
        ErrorDto response = ErrorDto.builder()
                .status(status.value())
                .error(errorName)
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<ErrorDto> buildValidationResponse(
            String message, HttpServletRequest request, List<ErrorDto.FieldErrorDto> errors) {
        ErrorDto response = ErrorDto.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .validationErrors(errors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
