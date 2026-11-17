package com.example.videokyc.workflow.domain.transition.guard;

/**
 * Boolean logical aggregation operators supported by the Guard DSL.
 */
public enum LogicalOperator {
    /**
     * All child expressions must evaluate to true. Children list must not be empty.
     */
    ALL,

    /**
     * At least one child expression must evaluate to true. Children list must not be empty.
     */
    ANY,

    /**
     * Logical negation. Must contain exactly one child expression.
     */
    NOT
}
