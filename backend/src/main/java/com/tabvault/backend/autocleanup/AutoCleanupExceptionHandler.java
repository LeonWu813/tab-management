package com.tabvault.backend.autocleanup;

import com.tabvault.backend.shared.error.ApiErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception handler for auto-cleanup domain exceptions.
 *
 * {@code @Order(Ordered.HIGHEST_PRECEDENCE)} ensures these specific handlers
 * are evaluated before the global {@code @ExceptionHandler(Exception.class)} in
 * {@link com.tabvault.backend.shared.error.GlobalExceptionHandler}, which would
 * otherwise intercept all exceptions and return HTTP 500.
 *
 * AC-038: invalid staleness threshold returns HTTP 400 with INVALID_STALENESS_THRESHOLD.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AutoCleanupExceptionHandler {

    /**
     * Handles {@link InvalidStalenessThresholdException} — returns HTTP 400.
     *
     * AC-038: staleness threshold must be 14, 30, 60, or 90. Any other value
     * causes this exception which is returned as a structured error.
     *
     * @param exception the exception with the invalid threshold value
     * @return HTTP 400 with {@code INVALID_STALENESS_THRESHOLD} code
     */
    @ExceptionHandler(InvalidStalenessThresholdException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidStalenessThreshold(
            InvalidStalenessThresholdException exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(
                        "INVALID_STALENESS_THRESHOLD",
                        exception.getMessage(),
                        "stalenessThresholdDays"));
    }
}
