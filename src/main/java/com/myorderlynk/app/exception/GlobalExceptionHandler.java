package com.myorderlynk.app.exception;

import com.myorderlynk.app.shipping.ShippingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        // Domain exceptions are expected control flow: log server-side faults loudly, client faults quietly.
        if (ex.getStatus().is5xxServerError()) {
            log.error("API error {}: {}", ex.getStatus().value(), ex.getMessage(), ex);
        } else {
            log.warn("API error {}: {}", ex.getStatus().value(), ex.getMessage());
        }
        return build(ex.getStatus(), ex.getMessage(), null);
    }

    /** A shipping-carrier call failed or the provider isn't configured — an upstream fault, not a 500 bug. */
    @ExceptionHandler(ShippingException.class)
    public ResponseEntity<Map<String, Object>> handleShipping(ShippingException ex) {
        log.warn("Shipping provider error: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "The shipping carrier could not complete this request", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "You do not have access to this resource", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, fe ->
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(), (a, b) -> a));
        log.debug("Validation failed: {}", fields);
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fields);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    /** Malformed/invalid request body (bad JSON, wrong types) — a client error, not a 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "Malformed or invalid request body", null);
    }

    /** Catch-all so unexpected failures are logged with a stack trace instead of surfacing untraced. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        // Spring MVC's own exceptions (malformed body → 400, wrong method → 405, unknown path → 404)
        // implement ErrorResponse and carry the correct status — preserve it instead of masking as 500.
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            log.warn("Request error {}: {}", status.value(), ex.getMessage());
            return build(status, status.getReasonPhrase(), null);
        }
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message, Object details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null) {
            body.put("details", details);
        }
        return ResponseEntity.status(status).body(body);
    }
}