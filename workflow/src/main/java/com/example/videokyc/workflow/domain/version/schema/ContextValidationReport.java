package com.example.videokyc.workflow.domain.version.schema;

import java.util.List;

/**
 * Diagnostic report of context validation against a {@link com.example.videokyc.workflow.domain.version.ContextSchema}.
 */
public record ContextValidationReport(
        boolean valid,
        List<ContextValidationError> errors
) {
    public static ContextValidationReport success() {
        return new ContextValidationReport(true, List.of());
    }

    public static ContextValidationReport failure(List<ContextValidationError> errors) {
        return new ContextValidationReport(false, errors != null ? List.copyOf(errors) : List.of());
    }
}
