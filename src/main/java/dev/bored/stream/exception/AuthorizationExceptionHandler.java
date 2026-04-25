package dev.bored.stream.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Maps {@link AuthorizationDeniedException} (thrown by {@code @PreAuthorize}
 * when an authenticated principal fails the authorization check) to a
 * proper HTTP 403 instead of letting common-lib's catch-all handler turn
 * it into a 500.
 *
 * <p>Ordered with {@link Ordered#HIGHEST_PRECEDENCE} so this handler
 * runs before {@code dev.bored.common.exception.CommonExceptionHandler},
 * which lives in a different module and treats the exception as
 * "unexpected".</p>
 *
 * @author Bored Software Developer
 * @since 2026-04-25
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthorizationExceptionHandler {

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", 403,
                "error", "Forbidden",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
