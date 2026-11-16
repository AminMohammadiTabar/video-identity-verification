package com.example.videokyc.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(schema = "auth", name = "email_outbox")
public class EmailOutboxEntity {
    @Id
    private UUID id;

    @Column(name = "verification_request_id", nullable = false, updatable = false)
    private UUID verificationRequestId;

    @Column(name = "subject_user_id", nullable = false, updatable = false)
    private UUID subjectUserId;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, updatable = false, length = 40)
    private EmailNotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Version
    @Column(nullable = false)
    private Long version;

    protected EmailOutboxEntity() { }

    public EmailOutboxEntity(UUID id, UUID verificationRequestId, UUID subjectUserId,
                             String recipientEmail, EmailNotificationType notificationType, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.verificationRequestId = Objects.requireNonNull(verificationRequestId);
        this.subjectUserId = Objects.requireNonNull(subjectUserId);
        if (recipientEmail == null || recipientEmail.isBlank() || recipientEmail.length() > 320)
            throw new IllegalArgumentException("A valid notification recipient is required");
        this.recipientEmail = recipientEmail;
        this.notificationType = Objects.requireNonNull(notificationType);
        this.status = EmailOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = Objects.requireNonNull(now);
        this.createdAt = now;
    }

    public void claim(Instant leaseUntil) {
        if (status != EmailOutboxStatus.PENDING && status != EmailOutboxStatus.PROCESSING)
            throw new IllegalStateException("Outbox item is not claimable");
        status = EmailOutboxStatus.PROCESSING;
        attemptCount++;
        lockedUntil = Objects.requireNonNull(leaseUntil);
    }

    public void retry(Instant nextAttempt, String errorCode, int maxAttempts) {
        if (status != EmailOutboxStatus.PROCESSING) throw new IllegalStateException("Outbox item is not processing");
        status = attemptCount >= maxAttempts ? EmailOutboxStatus.DEAD : EmailOutboxStatus.PENDING;
        nextAttemptAt = Objects.requireNonNull(nextAttempt);
        lockedUntil = null;
        lastErrorCode = errorCode;
        if (status == EmailOutboxStatus.DEAD) recipientEmail = null;
    }

    public void sent(Instant now) {
        if (status != EmailOutboxStatus.PROCESSING) throw new IllegalStateException("Outbox item is not processing");
        status = EmailOutboxStatus.SENT;
        sentAt = Objects.requireNonNull(now);
        lockedUntil = null;
        lastErrorCode = null;
        recipientEmail = null;
    }

    public UUID getId() { return id; }
    public UUID getVerificationRequestId() { return verificationRequestId; }
    public UUID getSubjectUserId() { return subjectUserId; }
    public String getRecipientEmail() { return recipientEmail; }
    public EmailNotificationType getNotificationType() { return notificationType; }
    public EmailOutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastErrorCode() { return lastErrorCode; }
}
