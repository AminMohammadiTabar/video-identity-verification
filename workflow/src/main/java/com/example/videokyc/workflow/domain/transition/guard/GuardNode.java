package com.example.videokyc.workflow.domain.transition.guard;

import java.util.List;

/**
 * Sealed interface for AST nodes in the restricted Guard JSON DSL.
 */
public sealed interface GuardNode permits GuardNode.Logical, GuardNode.Leaf {

    record Logical(LogicalOperator operator, List<GuardNode> children) implements GuardNode {
        public Logical {
            if (operator == null) {
                throw new IllegalArgumentException("Logical operator must not be null");
            }
            if (children == null || children.isEmpty()) {
                throw new IllegalArgumentException("Logical node children must not be null or empty");
            }
            if (operator == LogicalOperator.NOT && children.size() != 1) {
                throw new IllegalArgumentException("NOT operator must have exactly one child");
            }
            children = List.copyOf(children);
        }
    }

    record Leaf(String path, ComparisonOperator operator, Object value) implements GuardNode {
        public Leaf {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("Guard leaf path must not be null or blank");
            }
            if (operator == null) {
                throw new IllegalArgumentException("Guard leaf operator must not be null");
            }
            if (!operator.isUnary() && value == null) {
                throw new IllegalArgumentException("Comparison operator " + operator + " requires a non-null value");
            }
        }
    }
}
