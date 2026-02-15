package dev.bored.stream.exception;

import dev.bored.common.exception.ErrorResponse;
import dev.bored.common.exception.GenericException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Global exception handler that keeps error responses consistent.
 *
 * @author Bored Software Developer
 * @since 2026-02-15
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GenericException.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(GenericException ex) {
        log.warn("Error [{}]: {}", ex.getStatus().value(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.of(ex, null));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(500, "Internal Server Error", "An unexpected error occurred", null));
    }
}
