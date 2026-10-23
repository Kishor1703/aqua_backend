package com.aquaalert.exception;

import java.time.Instant;
import java.util.List;

/**
 * Single, consistent error envelope returned by every endpoint on failure.
 * Frontend can rely on this shape instead of parsing framework-specific
 * exception messages.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }
}
