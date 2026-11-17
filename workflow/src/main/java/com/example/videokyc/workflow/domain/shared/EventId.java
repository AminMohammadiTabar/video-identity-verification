package com.example.videokyc.workflow.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for distributed domain events.
 */
public record EventId(UUID value) {
    public EventId {
        Objects.requireNonNull(value, "EventId value must not be null");
    }

    public static EventId generate() {
        return new EventId(UUID.randomUUID());
    }

    public static EventId fromString(String id) {
        return new EventId(UUID.fromString(id));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
