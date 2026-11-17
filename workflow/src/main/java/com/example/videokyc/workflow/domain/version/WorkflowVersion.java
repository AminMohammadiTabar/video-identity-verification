package com.example.videokyc.workflow.domain.version;

import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.state.StateKey;
import com.example.videokyc.workflow.domain.state.StateType;
import com.example.videokyc.workflow.domain.state.WorkflowState;
import com.example.videokyc.workflow.domain.transition.TransitionKey;
import com.example.videokyc.workflow.domain.transition.WorkflowTransition;

import java.time.Instant;
import java.util.*;

/**
 * Aggregate Root representing an immutable or draft version graph of a Workflow Definition.
 * <p>
 * Controls states, transitions, context schema, and publication snapshots for one specific version number.
 */
public class WorkflowVersion {
    private Long id;
    private final Long definitionId;
    private final VersionNumber versionNumber;
    private WorkflowVersionStatus status;
    private ContextSchema contextSchema;
    private String snapshotJson;
    private SnapshotChecksum checksum;
    private int snapshotSchemaVersion;
    private final Instant createdAt;
    private final String createdBy;
    private Instant publishedAt;
    private long rowVersion;

    private final Map<StateKey, WorkflowState> states = new LinkedHashMap<>();
    private final Map<TransitionKey, WorkflowTransition> transitions = new LinkedHashMap<>();

    public WorkflowVersion(Long id,
                           Long definitionId,
                           VersionNumber versionNumber,
                           WorkflowVersionStatus status,
                           ContextSchema contextSchema,
                           String snapshotJson,
                           SnapshotChecksum checksum,
                           int snapshotSchemaVersion,
                           Instant createdAt,
                           String createdBy,
                           Instant publishedAt,
                           long rowVersion) {
        this.id = id;
        this.definitionId = definitionId;
        this.versionNumber = Objects.requireNonNull(versionNumber, "versionNumber must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.contextSchema = Objects.requireNonNull(contextSchema, "contextSchema must not be null");
        this.snapshotJson = snapshotJson;
        this.checksum = checksum;
        this.snapshotSchemaVersion = snapshotSchemaVersion > 0 ? snapshotSchemaVersion : 1;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdBy = validateUser(createdBy, "createdBy");
        this.publishedAt = publishedAt;
        this.rowVersion = rowVersion;
    }

    public static WorkflowVersion createDraft(Long definitionId,
                                              VersionNumber versionNumber,
                                              ContextSchema contextSchema,
                                              String createdBy,
                                              Instant now) {
        return new WorkflowVersion(
                null,
                definitionId,
                versionNumber,
                WorkflowVersionStatus.DRAFT,
                contextSchema,
                null,
                null,
                1,
                now,
                createdBy,
                null,
                0L
        );
    }

    public void updateContextSchema(ContextSchema schema) {
        ensureDraft("update context schema");
        this.contextSchema = Objects.requireNonNull(schema, "contextSchema must not be null");
    }

    public void addState(WorkflowState state) {
        ensureDraft("add state");
        Objects.requireNonNull(state, "state must not be null");
        if (states.containsKey(state.getStateKey())) {
            throw new DomainException("DUPLICATE_STATE_KEY",
                    "State key already exists in this version: " + state.getStateKey());
        }
        state.setVersionId(this.id);
        states.put(state.getStateKey(), state);
    }

    public void updateState(StateKey key, String name, String description, StateType stateType,
                            int displayOrder, String uiMetadata) {
        ensureDraft("update state");
        WorkflowState state = getStateOrThrow(key);
        state.update(name, description, stateType, displayOrder, uiMetadata);
    }

    public void removeState(StateKey key) {
        ensureDraft("remove state");
        getStateOrThrow(key);
        for (WorkflowTransition transition : transitions.values()) {
            if (Objects.equals(transition.getFromStateKey(), key) || Objects.equals(transition.getToStateKey(), key)) {
                throw new DomainException("CANNOT_REMOVE_STATE_REFERENCED_BY_TRANSITION",
                        "Cannot remove state " + key + " because transition " + transition.getTransitionKey() + " references it");
            }
        }
        states.remove(key);
    }

    public void addTransition(WorkflowTransition transition) {
        ensureDraft("add transition");
        Objects.requireNonNull(transition, "transition must not be null");
        if (transitions.containsKey(transition.getTransitionKey())) {
            throw new DomainException("DUPLICATE_TRANSITION_KEY",
                    "Transition key already exists in this version: " + transition.getTransitionKey());
        }

        WorkflowState fromState = getStateOrThrow(transition.getFromStateKey());
        getStateOrThrow(transition.getToStateKey());

        if (fromState.getStateType() == StateType.FINAL) {
            throw new DomainException("TRANSITION_FROM_FINAL_STATE",
                    "Cannot create transition originating from FINAL state: " + fromState.getStateKey());
        }

        transition.setVersionId(this.id);
        transitions.put(transition.getTransitionKey(), transition);
    }

    public void updateTransition(TransitionKey key, String name, String description,
                                 StateKey fromStateKey, StateKey toStateKey,
                                 com.example.videokyc.workflow.domain.transition.guard.GuardExpression guard,
                                 int displayOrder) {
        ensureDraft("update transition");
        WorkflowTransition transition = getTransitionOrThrow(key);
        WorkflowState fromState = getStateOrThrow(fromStateKey);
        getStateOrThrow(toStateKey);

        if (fromState.getStateType() == StateType.FINAL) {
            throw new DomainException("TRANSITION_FROM_FINAL_STATE",
                    "Cannot update transition to originate from FINAL state: " + fromState.getStateKey());
        }

        transition.update(name, description, fromStateKey, toStateKey, guard, displayOrder);
    }

    public void removeTransition(TransitionKey key) {
        ensureDraft("remove transition");
        getTransitionOrThrow(key);
        transitions.remove(key);
    }

    public void validateForPublication() {
        long initialCount = states.values().stream()
                .filter(s -> s.getStateType() == StateType.INITIAL)
                .count();
        if (initialCount != 1) {
            throw new DomainException("INVALID_INITIAL_STATE_COUNT",
                    "A published workflow version must have exactly one INITIAL state, but found: " + initialCount);
        }

        long finalCount = states.values().stream()
                .filter(s -> s.getStateType() == StateType.FINAL)
                .count();
        if (finalCount < 1) {
            throw new DomainException("MISSING_FINAL_STATE",
                    "A published workflow version must have at least one FINAL state");
        }

        for (WorkflowTransition transition : transitions.values()) {
            WorkflowState fromState = states.get(transition.getFromStateKey());
            WorkflowState toState = states.get(transition.getToStateKey());
            if (fromState == null || toState == null) {
                throw new DomainException("ORPHANED_TRANSITION_ENDPOINT",
                        "Transition " + transition.getTransitionKey() + " connects to an undefined state in this version");
            }
            if (fromState.getStateType() == StateType.FINAL) {
                throw new DomainException("TRANSITION_FROM_FINAL_STATE",
                        "Transition " + transition.getTransitionKey() + " originates from a FINAL state: " + fromState.getStateKey());
            }
        }
    }

    public WorkflowSnapshot buildSnapshot(WorkflowKey workflowKey) {
        List<WorkflowSnapshot.StateSnapshot> stateSnapshots = states.values().stream()
                .sorted(Comparator.comparingInt(WorkflowState::getDisplayOrder))
                .map(s -> new WorkflowSnapshot.StateSnapshot(
                        s.getStateKey().value(),
                        s.getName(),
                        s.getDescription(),
                        s.getStateType().name(),
                        s.getDisplayOrder(),
                        s.getUiMetadata()
                ))
                .toList();

        List<WorkflowSnapshot.TransitionSnapshot> transitionSnapshots = transitions.values().stream()
                .sorted(Comparator.comparingInt(WorkflowTransition::getDisplayOrder))
                .map(t -> new WorkflowSnapshot.TransitionSnapshot(
                        t.getTransitionKey().value(),
                        t.getName(),
                        t.getDescription(),
                        t.getFromStateKey().value(),
                        t.getToStateKey().value(),
                        t.getGuardExpression() != null ? t.getGuardExpression().rawJson() : null,
                        t.getGuardSchemaVersion(),
                        t.getDisplayOrder()
                ))
                .toList();

        return new WorkflowSnapshot(
                this.snapshotSchemaVersion,
                workflowKey.value(),
                this.versionNumber.value(),
                this.contextSchema.schemaJson(),
                stateSnapshots,
                transitionSnapshots
        );
    }

    public void requestPublication(WorkflowKey workflowKey) {
        ensureDraft("request publication");
        validateForPublication();
        WorkflowSnapshot snapshot = buildSnapshot(workflowKey);
        this.snapshotJson = snapshot.toCanonicalJson();
        this.checksum = snapshot.computeChecksum();
        this.status = WorkflowVersionStatus.PUBLISH_PENDING;
    }

    public void markPublished(Instant publishedAt) {
        if (this.status != WorkflowVersionStatus.PUBLISH_PENDING) {
            throw new DomainException("INVALID_VERSION_STATUS",
                    "Cannot mark published when status is " + this.status);
        }
        if (this.snapshotJson == null || this.checksum == null) {
            throw new DomainException("MISSING_SNAPSHOT_DATA",
                    "Snapshot JSON and checksum must exist before publishing");
        }
        this.status = WorkflowVersionStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }

    public void markPublicationFailed() {
        if (this.status != WorkflowVersionStatus.PUBLISH_PENDING) {
            throw new DomainException("INVALID_VERSION_STATUS",
                    "Cannot mark publication failed when status is " + this.status);
        }
        this.status = WorkflowVersionStatus.PUBLICATION_FAILED;
    }

    public void retryPublication() {
        if (this.status != WorkflowVersionStatus.PUBLICATION_FAILED) {
            throw new DomainException("INVALID_VERSION_STATUS",
                    "Can only retry publication on a PUBLICATION_FAILED version, current: " + this.status);
        }
        this.status = WorkflowVersionStatus.PUBLISH_PENDING;
    }

    private void ensureDraft(String action) {
        if (this.status != WorkflowVersionStatus.DRAFT) {
            throw new DomainException("VERSION_NOT_EDITABLE",
                    "Cannot " + action + " on version in status " + this.status + "; only DRAFT versions are mutable");
        }
    }

    public WorkflowState getStateOrThrow(StateKey key) {
        WorkflowState state = states.get(key);
        if (state == null) {
            throw new DomainException("STATE_NOT_FOUND", "State not found: " + key);
        }
        return state;
    }

    public WorkflowTransition getTransitionOrThrow(TransitionKey key) {
        WorkflowTransition transition = transitions.get(key);
        if (transition == null) {
            throw new DomainException("TRANSITION_NOT_FOUND", "Transition not found: " + key);
        }
        return transition;
    }

    private static String validateUser(String user, String field) {
        if (user == null || user.isBlank()) {
            throw new DomainException("INVALID_USER", field + " must not be blank");
        }
        String trimmed = user.trim();
        if (trimmed.length() > 128) {
            throw new DomainException("USER_TOO_LONG", field + " must not exceed 128 characters");
        }
        return trimmed;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDefinitionId() { return definitionId; }
    public VersionNumber getVersionNumber() { return versionNumber; }
    public WorkflowVersionStatus getStatus() { return status; }
    public ContextSchema getContextSchema() { return contextSchema; }
    public String getSnapshotJson() { return snapshotJson; }
    public SnapshotChecksum getChecksum() { return checksum; }
    public int getSnapshotSchemaVersion() { return snapshotSchemaVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public long getRowVersion() { return rowVersion; }
    public Map<StateKey, WorkflowState> getStates() { return Collections.unmodifiableMap(states); }
    public Map<TransitionKey, WorkflowTransition> getTransitions() { return Collections.unmodifiableMap(transitions); }
}
