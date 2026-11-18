package com.example.videokyc.workflow.domain.version.schema;

/**
 * Value object representing a single schema or context validation error.
 */
public record ContextValidationError(
        String path,
        String errorCode,
        String message
) {
    public ContextValidationError {
        if (path == null) {
            path = "";
        }
        if (errorCode == null || errorCode.isBlank()) {
            errorCode = "VALIDATION_ERROR";
        }
        if (message == null || message.isBlank()) {
            message = "Validation failed at path: " + path;
        }
    }
}
