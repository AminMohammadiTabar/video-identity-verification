package com.example.videokyc.workflow.domain.transition.guard;

import java.util.List;

/**
 * Diagnostic result of evaluating a {@link GuardExpression} against a runtime business context.
 */
public record GuardEvaluationResult(
        boolean matched,
        String reason,
        GuardEvaluationError error,
        List<String> evaluatedPaths
) {
    public static GuardEvaluationResult match(List<String> evaluatedPaths) {
        return new GuardEvaluationResult(
                true,
                "All guard conditions met",
                GuardEvaluationError.NONE,
                evaluatedPaths != null ? List.copyOf(evaluatedPaths) : List.of()
        );
    }

    public static GuardEvaluationResult noMatch(String reason, List<String> evaluatedPaths) {
        return new GuardEvaluationResult(
                false,
                reason != null ? reason : "Guard condition not met",
                GuardEvaluationError.NONE,
                evaluatedPaths != null ? List.copyOf(evaluatedPaths) : List.of()
        );
    }

    public static GuardEvaluationResult error(GuardEvaluationError error, String message, List<String> evaluatedPaths) {
        return new GuardEvaluationResult(
                false,
                message,
                error != null ? error : GuardEvaluationError.EVALUATION_ERROR,
                evaluatedPaths != null ? List.copyOf(evaluatedPaths) : List.of()
        );
    }

    public boolean hasError() {
        return error != null && error != GuardEvaluationError.NONE;
    }
}
