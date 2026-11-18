package com.example.videokyc.workflow.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity mapping for {@code workflow.workflow_definitions}.
 */
@Entity
@Table(schema = "workflow", name = "workflow_definitions")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class WorkflowDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_key", nullable = false, unique = true, length = 100)
    private String workflowKey;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "active_version_id")
    private Long activeVersionId;

    @Column(name = "pending_version_id")
    private Long pendingVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public WorkflowDefinitionEntity(String workflowKey,
                                  String name,
                                  String description,
                                  String status,
                                  Long activeVersionId,
                                  Long pendingVersionId,
                                  Instant createdAt,
                                  String createdBy,
                                  Instant updatedAt,
                                  String updatedBy) {
        this.workflowKey = workflowKey;
        this.name = name;
        this.description = description;
        this.status = status;
        this.activeVersionId = activeVersionId;
        this.pendingVersionId = pendingVersionId;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.rowVersion = 0L;
    }
}
