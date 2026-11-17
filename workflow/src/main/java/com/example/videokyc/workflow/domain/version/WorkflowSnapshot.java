package com.example.videokyc.workflow.domain.version;

import com.example.videokyc.workflow.domain.shared.CanonicalJsonSerializer;
import com.example.videokyc.workflow.domain.shared.DomainException;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, consumer-facing snapshot of a published workflow version.
 * <p>
 * Does not contain internal database primary keys or JPA details.
 */
public record WorkflowSnapshot(
        int snapshotSchemaVersion,
        String workflowKey,
        int versionNumber,
        String contextSchema,
        List<StateSnapshot> states,
        List<TransitionSnapshot> transitions
) {
    public WorkflowSnapshot {
        if (snapshotSchemaVersion < 1) {
            throw new DomainException("INVALID_SNAPSHOT_SCHEMA_VERSION", "snapshotSchemaVersion must be >= 1");
        }
        Objects.requireNonNull(workflowKey, "workflowKey must not be null");
        if (versionNumber < 1) {
            throw new DomainException("INVALID_SNAPSHOT_VERSION_NUMBER", "versionNumber must be >= 1");
        }
        Objects.requireNonNull(contextSchema, "contextSchema must not be null");
        states = states != null ? List.copyOf(states) : List.of();
        transitions = transitions != null ? List.copyOf(transitions) : List.of();
    }

    public String toCanonicalJson() {
        return CanonicalJsonSerializer.toCanonicalJson(this);
    }

    public SnapshotChecksum computeChecksum() {
        String canonical = toCanonicalJson();
        return SnapshotChecksum.of(CanonicalJsonSerializer.computeSha256Checksum(canonical));
    }

    public record StateSnapshot(
            String stateKey,
            String name,
            String description,
            String stateType,
            int displayOrder,
            String uiMetadata
    ) {}

    public record TransitionSnapshot(
            String transitionKey,
            String name,
            String description,
            String fromStateKey,
            String toStateKey,
            String guardExpression,
            int guardSchemaVersion,
            int displayOrder
    ) {}
}
