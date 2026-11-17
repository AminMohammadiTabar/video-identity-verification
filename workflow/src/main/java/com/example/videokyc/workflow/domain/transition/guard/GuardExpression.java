package com.example.videokyc.workflow.domain.transition.guard;

import java.util.Objects;

/**
 * Value object representing an immutable Guard rule attached to a Workflow Transition.
 */
public record GuardExpression(String rawJson, GuardNode rootNode, int guardSchemaVersion) {

    public GuardExpression {
        Objects.requireNonNull(rawJson, "rawJson must not be null");
        Objects.requireNonNull(rootNode, "rootNode must not be null");
        if (guardSchemaVersion < 1) {
            throw new IllegalArgumentException("guardSchemaVersion must be >= 1");
        }
    }

    public static GuardExpression of(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        GuardNode node = GuardParser.parse(json);
        return new GuardExpression(json.trim(), node, 1);
    }
}
