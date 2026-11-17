package com.example.videokyc.workflow.domain.publication;

import com.example.videokyc.workflow.domain.shared.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate Root tracking the asynchronous publication attempt lifecycle of a single WorkflowVersion.
 */
public class PublicationJob {
    private Long id;
    private final Long versionId;
    private PublicationJobStatus status;
    private int attemptCount;
    private int maxAttempts;
    private Instant nextAttemptAt;
    private String lockedBy;
    private Instant lockedUntil;
    private String lastErrorCode;
    private String lastErrorMessage;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;
    private long rowVersion;

    public PublicationJob(Long id,
                          Long versionId,
                          PublicationJobStatus status,
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
                          Instant updatedAt,
                          long rowVersion) {
        this.id = id;
        this.versionId = Objects.requireNonNull(versionId, "versionId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.attemptCount = validateNonNegative(attemptCount, "attemptCount");
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
        this.nextAttemptAt = nextAttemptAt;
        this.lockedBy = lockedBy;
        this.lockedUntil = lockedUntil;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.rowVersion = rowVersion;
    }

    public static PublicationJob create(Long versionId, int maxAttempts, Instant now) {
        return new PublicationJob(
                null,
                versionId,
                PublicationJobStatus.PENDING,
                0,
                maxAttempts > 0 ? maxAttempts : 5,
                now,
                null,
                null,
                null,
                null,
                now,
                null,
                null,
                now,
                0L
        );
    }

    public void claim(String workerId, Duration leaseDuration, Instant now) {
        if (this.status != PublicationJobStatus.PENDING && this.status != PublicationJobStatus.RETRY_WAIT) {
            throw new DomainException("INVALID_JOB_STATUS_FOR_CLAIM",
                    "Cannot claim job in status " + this.status + "; only PENDING or RETRY_WAIT jobs are claimable");
        }
        if (this.nextAttemptAt != null && now.isBefore(this.nextAttemptAt)) {
            throw new DomainException("JOB_NOT_DUE", "Job is not yet due for execution until " + this.nextAttemptAt);
        }

        this.status = PublicationJobStatus.PROCESSING;
        this.attemptCount++;
        this.lockedBy = Objects.requireNonNull(workerId, "workerId must not be null");
        this.lockedUntil = now.plus(Objects.requireNonNull(leaseDuration, "leaseDuration must not be null"));
        if (this.startedAt == null) {
            this.startedAt = now;
        }
        this.updatedAt = now;
    }

    public void markSucceeded(Instant now) {
        if (this.status != PublicationJobStatus.PROCESSING) {
            throw new DomainException("INVALID_JOB_STATUS",
                    "Cannot complete job in status " + this.status + "; expected PROCESSING");
        }
        this.status = PublicationJobStatus.SUCCEEDED;
        this.lockedBy = null;
        this.lockedUntil = null;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void markRetryWait(Duration backoff, String errorCode, String errorMessage, Instant now) {
        if (this.status != PublicationJobStatus.PROCESSING) {
            throw new DomainException("INVALID_JOB_STATUS",
                    "Cannot set retry wait in status " + this.status + "; expected PROCESSING");
        }
        if (this.attemptCount >= this.maxAttempts) {
            markFailed(errorCode, errorMessage, now);
            return;
        }

        this.status = PublicationJobStatus.RETRY_WAIT;
        this.nextAttemptAt = now.plus(Objects.requireNonNull(backoff, "backoff must not be null"));
        this.lockedBy = null;
        this.lockedUntil = null;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = sanitizeMessage(errorMessage);
        this.updatedAt = now;
    }

    public void markFailed(String errorCode, String errorMessage, Instant now) {
        this.status = PublicationJobStatus.FAILED;
        this.lockedBy = null;
        this.lockedUntil = null;
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = sanitizeMessage(errorMessage);
        this.updatedAt = now;
    }

    public void recoverExpiredLease(Instant now) {
        if (this.status == PublicationJobStatus.PROCESSING && this.lockedUntil != null && now.isAfter(this.lockedUntil)) {
            this.status = PublicationJobStatus.RETRY_WAIT;
            this.lockedBy = null;
            this.lockedUntil = null;
            this.nextAttemptAt = now;
            this.updatedAt = now;
        }
    }

    public void manualRetry(Instant now) {
        if (this.status != PublicationJobStatus.FAILED) {
            throw new DomainException("INVALID_JOB_STATUS_FOR_RETRY",
                    "Can only manually retry a FAILED publication job, current status: " + this.status);
        }
        this.status = PublicationJobStatus.PENDING;
        this.nextAttemptAt = now;
        this.lockedBy = null;
        this.lockedUntil = null;
        this.lastErrorCode = null;
        this.lastErrorMessage = null;
        this.updatedAt = now;
    }

    private static int validateNonNegative(int value, String field) {
        if (value < 0) {
            throw new DomainException("INVALID_VALUE", field + " must be non-negative");
        }
        return value;
    }

    private static String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersionId() { return versionId; }
    public PublicationJobStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLockedBy() { return lockedBy; }
    public Instant getLockedUntil() { return lockedUntil; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getRowVersion() { return rowVersion; }
}
