package com.example.videokyc.workflow.domain.definition;

import com.example.videokyc.workflow.domain.shared.DomainException;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate Root representing the identity, administrative lifecycle, and active version
 * pointers of a Workflow Definition.
 * <p>
 * Does not own version graphs or transition rules; those belong to {@code WorkflowVersion}.
 */
public class WorkflowDefinition {
    private Long id;
    private final WorkflowKey workflowKey;
    private String name;
    private String description;
    private WorkflowDefinitionStatus status;
    private Long activeVersionId;
    private Long pendingVersionId;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    private long rowVersion;

    public WorkflowDefinition(Long id,
                              WorkflowKey workflowKey,
                              String name,
                              String description,
                              WorkflowDefinitionStatus status,
                              Long activeVersionId,
                              Long pendingVersionId,
                              Instant createdAt,
                              String createdBy,
                              Instant updatedAt,
                              String updatedBy,
                              long rowVersion) {
        this.id = id;
        this.workflowKey = Objects.requireNonNull(workflowKey, "workflowKey must not be null");
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.activeVersionId = activeVersionId;
        this.pendingVersionId = pendingVersionId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.createdBy = validateUser(createdBy, "createdBy");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.updatedBy = validateUser(updatedBy, "updatedBy");
        this.rowVersion = rowVersion;

        validateVersionInvariants();
    }

    public static WorkflowDefinition create(WorkflowKey workflowKey,
                                           String name,
                                           String description,
                                           String createdBy,
                                           Instant now) {
        return new WorkflowDefinition(
                null,
                workflowKey,
                name,
                description,
                WorkflowDefinitionStatus.ACTIVE,
                null,
                null,
                now,
                createdBy,
                now,
                createdBy,
                0L
        );
    }

    public void updateMetadata(String name, String description, String updatedBy, Instant now) {
        ensureNotArchived("update metadata");
        this.name = validateName(name);
        this.description = validateDescription(description);
        touch(updatedBy, now);
    }

    public void enable(String updatedBy, Instant now) {
        ensureNotArchived("enable");
        if (this.status != WorkflowDefinitionStatus.ACTIVE) {
            this.status = WorkflowDefinitionStatus.ACTIVE;
            touch(updatedBy, now);
        }
    }

    public void disable(String updatedBy, Instant now) {
        ensureNotArchived("disable");
        if (this.status != WorkflowDefinitionStatus.DISABLED) {
            this.status = WorkflowDefinitionStatus.DISABLED;
            touch(updatedBy, now);
        }
    }

    public void archive(String updatedBy, Instant now) {
        ensureNotArchived("archive");
        if (this.pendingVersionId != null) {
            throw new DomainException("CANNOT_ARCHIVE_WITH_PENDING_PUBLICATION",
                    "Cannot archive definition while a version publication is pending");
        }
        this.status = WorkflowDefinitionStatus.ARCHIVED;
        touch(updatedBy, now);
    }

    public void setPendingVersion(Long versionId, String updatedBy, Instant now) {
        ensureNotArchived("set pending version");
        if (this.status != WorkflowDefinitionStatus.ACTIVE) {
            throw new DomainException("DEFINITION_NOT_ACTIVE", "Cannot publish versions for an inactive definition");
        }
        if (this.pendingVersionId != null) {
            throw new DomainException("PENDING_PUBLICATION_EXISTS",
                    "A publication job is already pending for version ID: " + this.pendingVersionId);
        }
        Objects.requireNonNull(versionId, "versionId must not be null");
        if (Objects.equals(this.activeVersionId, versionId)) {
            throw new DomainException("VERSION_ALREADY_ACTIVE", "Version ID " + versionId + " is already active");
        }
        this.pendingVersionId = versionId;
        touch(updatedBy, now);
    }

    public void completePublication(Long versionId, String updatedBy, Instant now) {
        if (!Objects.equals(this.pendingVersionId, versionId)) {
            throw new DomainException("INVALID_PENDING_VERSION",
                    "Version ID " + versionId + " does not match pending version ID: " + this.pendingVersionId);
        }
        this.activeVersionId = versionId;
        this.pendingVersionId = null;
        touch(updatedBy, now);
    }

    public void clearPendingVersion(Long versionId, String updatedBy, Instant now) {
        if (Objects.equals(this.pendingVersionId, versionId)) {
            this.pendingVersionId = null;
            touch(updatedBy, now);
        }
    }

    public boolean isExecutable() {
        return this.status == WorkflowDefinitionStatus.ACTIVE && this.activeVersionId != null;
    }

    private void ensureNotArchived(String action) {
        if (this.status == WorkflowDefinitionStatus.ARCHIVED) {
            throw new DomainException("DEFINITION_ARCHIVED", "Cannot " + action + " on an archived definition");
        }
    }

    private void validateVersionInvariants() {
        if (activeVersionId != null && pendingVersionId != null && activeVersionId.equals(pendingVersionId)) {
            throw new DomainException("ACTIVE_AND_PENDING_EQUAL",
                    "Active version ID and pending version ID cannot be the same: " + activeVersionId);
        }
    }

    private void touch(String user, Instant now) {
        this.updatedBy = validateUser(user, "updatedBy");
        this.updatedAt = Objects.requireNonNull(now, "updatedAt must not be null");
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("INVALID_NAME", "Workflow definition name must not be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 200) {
            throw new DomainException("NAME_TOO_LONG", "Workflow definition name must not exceed 200 characters");
        }
        return trimmed;
    }

    private static String validateDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > 1000) {
            throw new DomainException("DESCRIPTION_TOO_LONG", "Description must not exceed 1000 characters");
        }
        return trimmed.isEmpty() ? null : trimmed;
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

    // Getters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public WorkflowKey getWorkflowKey() { return workflowKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public WorkflowDefinitionStatus getStatus() { return status; }
    public Long getActiveVersionId() { return activeVersionId; }
    public Long getPendingVersionId() { return pendingVersionId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public long getRowVersion() { return rowVersion; }
}
