package com.tabvault.backend.shared.error;

/**
 * Standard API error response envelope.
 * All error responses are wrapped in this structure:
 * { "error": { "code": "ERROR_CODE", "message": "human-readable message", "field": "optional" } }
 */
public record ApiErrorResponse(ApiError error) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ApiError(code, message));
    }

    public static ApiErrorResponse of(String code, String message, String field) {
        return new ApiErrorResponse(new ApiError(code, message, field));
    }
}
