package com.example.videokyc.workflow.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for tracing request correlation across boundaries.
 */
public record CorrelationId(UUID value) {
    public CorrelationId {
        Objects.requireNonNull(value, "CorrelationId value must not be null");
    }

    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID());
    }

    public static CorrelationId fromString(String id) {
        return new CorrelationId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
