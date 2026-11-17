package com.example.videokyc.workflow.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * JPA entity mapping for {@code workflow.workflow_publication_jobs}.
 */
@Entity
@Table(schema = "workflow", name = "workflow_publication_jobs")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class PublicationJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false, unique = true)
    private Long versionId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public PublicationJobEntity(Long versionId,
                               String status,
                               int attemptCount,
                               int maxAttempts,
                               Instant nextAttemptAt,
                               String lockedBy,
                               Instant lockedUntil,
                               String lastErrorCode,
                               String lastErrorMessage,
                               Instant createdAt,
                               Instant startedAt,
                               Instant completedAt,
                               Instant updatedAt) {
        this.versionId = versionId;
        this.status = status;
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = nextAttemptAt;
        this.lockedBy = lockedBy;
        this.lockedUntil = lockedUntil;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.updatedAt = updatedAt;
        this.rowVersion = 0L;
    }
}
