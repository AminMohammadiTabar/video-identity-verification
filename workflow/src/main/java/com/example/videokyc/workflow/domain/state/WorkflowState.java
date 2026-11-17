package com.example.videokyc.workflow.domain.state;

import com.example.videokyc.workflow.domain.shared.DomainException;

import java.util.Objects;

/**
 * Domain entity representing an individual state node in a {@code WorkflowVersion} graph.
 */
public class WorkflowState {
    private Long id;
    private Long versionId;
    private final StateKey stateKey;
    private String name;
    private String description;
    private StateType stateType;
    private int displayOrder;
    private String uiMetadata;

    public WorkflowState(Long id,
                         Long versionId,
                         StateKey stateKey,
                         String name,
                         String description,
                         StateType stateType,
                         int displayOrder,
                         String uiMetadata) {
        this.id = id;
        this.versionId = versionId;
        this.stateKey = Objects.requireNonNull(stateKey, "stateKey must not be null");
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.stateType = Objects.requireNonNull(stateType, "stateType must not be null");
        this.displayOrder = validateDisplayOrder(displayOrder);
        this.uiMetadata = uiMetadata;
    }

    public static WorkflowState create(StateKey stateKey,
                                       String name,
                                       String description,
                                       StateType stateType,
                                       int displayOrder,
                                       String uiMetadata) {
        return new WorkflowState(null, null, stateKey, name, description, stateType, displayOrder, uiMetadata);
    }

    public void update(String name, String description, StateType stateType, int displayOrder, String uiMetadata) {
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.stateType = Objects.requireNonNull(stateType, "stateType must not be null");
        this.displayOrder = validateDisplayOrder(displayOrder);
        this.uiMetadata = uiMetadata;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("INVALID_STATE_NAME", "State name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 200) {
            throw new DomainException("STATE_NAME_TOO_LONG", "State name must not exceed 200 characters");
        }
        return trimmed;
    }

    private static String validateDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > 1000) {
            throw new DomainException("STATE_DESCRIPTION_TOO_LONG", "Description must not exceed 1000 characters");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int validateDisplayOrder(int order) {
        if (order < 0) {
            throw new DomainException("INVALID_DISPLAY_ORDER", "Display order must be non-negative");
        }
        return order;
    }

    // Getters and internal setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public StateKey getStateKey() { return stateKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public StateType getStateType() { return stateType; }
    public int getDisplayOrder() { return displayOrder; }
    public String getUiMetadata() { return uiMetadata; }
}
