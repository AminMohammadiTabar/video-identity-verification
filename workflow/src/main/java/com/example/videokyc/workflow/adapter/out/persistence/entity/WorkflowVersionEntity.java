package com.example.videokyc.workflow.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA entity mapping for {@code workflow.workflow_versions}.
 */
@Entity
@Table(schema = "workflow", name = "workflow_versions")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class WorkflowVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "definition_id", nullable = false)
    private Long definitionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "context_schema_json", nullable = false, columnDefinition = "TEXT")
    private String contextSchemaJson;

    @Column(name = "snapshot_json", columnDefinition = "TEXT")
    private String snapshotJson;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "snapshot_schema_version", nullable = false)
    private short snapshotSchemaVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 128)
    private String createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public WorkflowVersionEntity(Long definitionId,
                                int versionNumber,
                                String status,
                                String contextSchemaJson,
                                String snapshotJson,
                                String checksum,
                                short snapshotSchemaVersion,
                                Instant createdAt,
                                String createdBy,
                                Instant publishedAt) {
        this.definitionId = definitionId;
        this.versionNumber = versionNumber;
        this.status = status;
        this.contextSchemaJson = contextSchemaJson;
        this.snapshotJson = snapshotJson;
        this.checksum = checksum;
        this.snapshotSchemaVersion = snapshotSchemaVersion;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.publishedAt = publishedAt;
        this.rowVersion = 0L;
    }
}
