package com.example.videokyc.workflow.domain.state;

/**
 * Structural role of a state node within a workflow version graph.
 */
public enum StateType {
    /**
     * Initial entry point of the workflow graph. A published version must have exactly one INITIAL state.
     */
    INITIAL,

    /**
     * Standard intermediary processing step.
     */
    INTERMEDIATE,

    /**
     * Terminal state of the workflow graph. A published version must have at least one FINAL state.
     * Transitions originating from a FINAL state are prohibited.
     */
    FINAL
}
