package com.example.videokyc.workflow.domain.transition.guard;

/**
 * Diagnostic error classification for Guard and Context evaluation outcomes.
 */
public enum GuardEvaluationError {
    /**
     * No evaluation error occurred.
     */
    NONE,

    /**
     * A required path was not present in the runtime context.
     */
    PATH_NOT_FOUND,

    /**
     * The type of a field in the runtime context does not match the expected comparison type.
     */
    TYPE_MISMATCH,

    /**
     * The runtime context data failed validation against the version's ContextSchema.
     */
    SCHEMA_VIOLATION,

    /**
     * The runtime context payload was null or not a valid JSON object.
     */
    MALFORMED_CONTEXT,

    /**
     * General evaluation failure.
     */
    EVALUATION_ERROR
}
