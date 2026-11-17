package com.example.videokyc.workflow.domain.transition.guard;

/**
 * Leaf comparison operators supported by the Guard DSL.
 */
public enum ComparisonOperator {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    IN,
    NOT_IN,
    EXISTS,
    NOT_EXISTS;

    public boolean isUnary() {
        return this == EXISTS || this == NOT_EXISTS;
    }
}
