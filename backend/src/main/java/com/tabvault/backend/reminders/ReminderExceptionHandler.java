package com.tabvault.backend.reminders;

import com.tabvault.backend.shared.error.ApiErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps reminder domain exceptions to the standard HTTP error envelope.
 *
 * Uses the same { "error": { "code": "...", "message": "..." } } envelope defined
 * in production.md Shared Conventions.
 *
 * @Order(Ordered.HIGHEST_PRECEDENCE) ensures these specific handlers are evaluated
 * before GlobalExceptionHandler's broad Exception.class catch-all.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ReminderExceptionHandler {

    /**
     * Handles ReminderNotFoundException — returned when a reminder ID does not exist
     * or does not belong to the authenticated user (AC-023 ownership check).
     */
    @ExceptionHandler(ReminderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReminderNotFound(
            ReminderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("REMINDER_NOT_FOUND", exception.getMessage()));
    }

    /**
     * Handles ReminderItemNotFoundException — returned when trying to create a reminder
     * for an item that does not exist or is not owned by the authenticated user (AC-021).
     */
    @ExceptionHandler(ReminderItemNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleReminderItemNotFound(
            ReminderItemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("ITEM_NOT_FOUND", exception.getMessage()));
    }
}
