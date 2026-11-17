package com.example.videokyc.workflow.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for identifying the direct cause of a state change or message.
 */
public record CausationId(UUID value) {
    public CausationId {
        Objects.requireNonNull(value, "CausationId value must not be null");
    }

    public static CausationId generate() {
        return new CausationId(UUID.randomUUID());
    }

    public static CausationId fromString(String id) {
        return new CausationId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
