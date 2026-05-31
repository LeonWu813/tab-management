package com.tabvault.backend.items;

import com.tabvault.backend.shared.error.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps item and category domain exceptions to the standard HTTP error envelope.
 *
 * Uses the same { "error": { "code": "...", "message": "..." } } envelope as
 * all other modules per the shared convention in production.md.
 */
@RestControllerAdvice
public class ItemExceptionHandler {

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleItemNotFound(ItemNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("ITEM_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCategoryNotFound(CategoryNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("CATEGORY_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(BatchRateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(BatchRateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiErrorResponse.of("BATCH_RATE_LIMIT_EXCEEDED", exception.getMessage()));
    }
}
