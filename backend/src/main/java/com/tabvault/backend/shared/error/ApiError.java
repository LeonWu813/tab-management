package com.tabvault.backend.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Inner "error" object in the standard API error envelope.
 * Serialized as: { "error": { "code": "...", "message": "...", "field": "..." } }
 * The field property is omitted when null (validation errors only).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String field) {

    public ApiError(String code, String message) {
        this(code, message, null);
    }
}
