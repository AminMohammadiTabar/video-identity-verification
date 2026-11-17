package com.example.videokyc.workflow.domain.transition;

import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.state.StateKey;
import com.example.videokyc.workflow.domain.transition.guard.GuardExpression;

import java.util.Objects;

/**
 * Domain entity representing an edge between two states in a {@code WorkflowVersion} graph.
 */
public class WorkflowTransition {
    private Long id;
    private Long versionId;
    private final TransitionKey transitionKey;
    private String name;
    private String description;
    private Long fromStateId;
    private Long toStateId;
    private StateKey fromStateKey;
    private StateKey toStateKey;
    private GuardExpression guardExpression;
    private int guardSchemaVersion;
    private int displayOrder;

    public WorkflowTransition(Long id,
                              Long versionId,
                              TransitionKey transitionKey,
                              String name,
                              String description,
                              Long fromStateId,
                              Long toStateId,
                              StateKey fromStateKey,
                              StateKey toStateKey,
                              GuardExpression guardExpression,
                              int guardSchemaVersion,
                              int displayOrder) {
        this.id = id;
        this.versionId = versionId;
        this.transitionKey = Objects.requireNonNull(transitionKey, "transitionKey must not be null");
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.fromStateId = fromStateId;
        this.toStateId = toStateId;
        this.fromStateKey = fromStateKey;
        this.toStateKey = toStateKey;
        this.guardExpression = guardExpression;
        this.guardSchemaVersion = guardSchemaVersion > 0 ? guardSchemaVersion : 1;
        this.displayOrder = validateDisplayOrder(displayOrder);
    }

    public static WorkflowTransition create(TransitionKey transitionKey,
                                            String name,
                                            String description,
                                            StateKey fromStateKey,
                                            StateKey toStateKey,
                                            GuardExpression guardExpression,
                                            int displayOrder) {
        return new WorkflowTransition(null, null, transitionKey, name, description, null, null,
                fromStateKey, toStateKey, guardExpression, 1, displayOrder);
    }

    public void update(String name,
                       String description,
                       StateKey fromStateKey,
                       StateKey toStateKey,
                       GuardExpression guardExpression,
                       int displayOrder) {
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.fromStateKey = fromStateKey;
        this.toStateKey = toStateKey;
        this.guardExpression = guardExpression;
        this.displayOrder = validateDisplayOrder(displayOrder);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("INVALID_TRANSITION_NAME", "Transition name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 200) {
            throw new DomainException("TRANSITION_NAME_TOO_LONG", "Transition name must not exceed 200 characters");
        }
        return trimmed;
    }

    private static String validateDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > 1000) {
            throw new DomainException("TRANSITION_DESCRIPTION_TOO_LONG", "Description must not exceed 1000 characters");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int validateDisplayOrder(int order) {
        if (order < 0) {
            throw new DomainException("INVALID_DISPLAY_ORDER", "Display order must be non-negative");
        }
        return order;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public TransitionKey getTransitionKey() { return transitionKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getFromStateId() { return fromStateId; }
    public void setFromStateId(Long fromStateId) { this.fromStateId = fromStateId; }
    public Long getToStateId() { return toStateId; }
    public void setToStateId(Long toStateId) { this.toStateId = toStateId; }
    public StateKey getFromStateKey() { return fromStateKey; }
    public void setFromStateKey(StateKey fromStateKey) { this.fromStateKey = fromStateKey; }
    public StateKey getToStateKey() { return toStateKey; }
    public void setToStateKey(StateKey toStateKey) { this.toStateKey = toStateKey; }
    public GuardExpression getGuardExpression() { return guardExpression; }
    public void setGuardExpression(GuardExpression guardExpression) { this.guardExpression = guardExpression; }
    public int getGuardSchemaVersion() { return guardSchemaVersion; }
    public int getDisplayOrder() { return displayOrder; }
}
