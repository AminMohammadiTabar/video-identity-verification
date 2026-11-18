package com.example.videokyc.workflow.domain.version.schema;

import java.util.List;

/**
 * Diagnostic report of static compatibility between a
 * {@link com.example.videokyc.workflow.domain.transition.guard.GuardExpression} and a
 * {@link com.example.videokyc.workflow.domain.version.ContextSchema}.
 */
public record GuardCompatibilityReport(
        boolean compatible,
        List<String> issues
) {
    public static GuardCompatibilityReport success() {
        return new GuardCompatibilityReport(true, List.of());
    }

    public static GuardCompatibilityReport failure(List<String> issues) {
        return new GuardCompatibilityReport(false, issues != null ? List.copyOf(issues) : List.of());
    }
}
