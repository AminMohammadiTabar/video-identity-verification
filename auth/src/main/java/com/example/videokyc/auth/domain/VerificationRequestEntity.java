package com.example.videokyc.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One immutable review attempt. A rejected attempt is retained when a new attempt is created. */
@Entity
@Table(schema = "auth", name = "verification_requests",
        uniqueConstraints = @UniqueConstraint(name = "uq_verification_user_attempt", columnNames = {"user_id", "attempt_number"}))
public class VerificationRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "dms_file_id")
    private UUID dmsFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private VerificationStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected VerificationRequestEntity() { }

    public VerificationRequestEntity(UserEntity user, int attemptNumber) {
        this.user = Objects.requireNonNull(user);
        if (attemptNumber < 1) throw new IllegalArgumentException("attemptNumber must be positive");
        this.attemptNumber = attemptNumber;
        this.status = VerificationStatus.UPLOADING;
    }

    /** Retry the storage operation within the same attempt after a transient DMS failure. */
    public void retryUpload() {
        if (status != VerificationStatus.UPLOAD_FAILED) throw new IllegalStateException("Cannot retry this request");
        status = VerificationStatus.UPLOADING;
    }

    public void uploaded(UUID fileId) {
        if (status != VerificationStatus.UPLOADING) throw new IllegalStateException("Upload not reserved");
        dmsFileId = Objects.requireNonNull(fileId);
        submittedAt = Instant.now();
        status = VerificationStatus.PENDING_REVIEW;
    }

    public void uploadFailed() {
        if (status != VerificationStatus.UPLOADING) throw new IllegalStateException("Not uploading");
        status = VerificationStatus.UPLOAD_FAILED;
    }

    public void decide(UUID reviewerId, boolean approved, String reason) {
        if (status != VerificationStatus.PENDING_REVIEW) throw new IllegalStateException("Request is not awaiting review");
        if (!approved && (reason == null || reason.isBlank())) throw new IllegalArgumentException("Rejection reason is required");
        if (reason != null && reason.length() > 500) throw new IllegalArgumentException("Reason exceeds 500 characters");
        status = approved ? VerificationStatus.APPROVED : VerificationStatus.REJECTED;
        reviewedBy = Objects.requireNonNull(reviewerId);
        reviewedAt = Instant.now();
        reviewReason = approved ? null : reason.strip();
    }

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public int getAttemptNumber() { return attemptNumber; }
    public UUID getDmsFileId() { return dmsFileId; }
    public VerificationStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public String getReviewReason() { return reviewReason; }
    public long getVersion() { return version; }
}
