package com.example.videokyc.auth.service;

import com.example.videokyc.auth.domain.EmailOutboxEntity;
import com.example.videokyc.auth.domain.EmailNotificationType;
import com.example.videokyc.auth.domain.EmailOutboxStatus;
import com.example.videokyc.auth.observability.audit.AuditEventCommand;
import com.example.videokyc.auth.observability.audit.AuditEventRecorder;
import com.example.videokyc.auth.repository.EmailOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Manages the transactional email outbox for asynchronous notification delivery.
 * <p>
 * Ensures reliable message dispatch without external brokers (RabbitMQ/Kafka):
 * <ul>
 *   <li>Notification records are enqueued inside the same database transaction as the business event.</li>
 *   <li>Workers claim batches using time-bounded leases to prevent double delivery.</li>
 *   <li>Retries are scheduled with exponential backoff up to a configurable maximum attempt threshold.</li>
 * </ul>
 */
@Service
public class EmailOutboxService {
    private final EmailOutboxRepository repository;
    private final AuditEventRecorder audit;
    private final int maxAttempts;

    public EmailOutboxService(EmailOutboxRepository repository, AuditEventRecorder audit,
                              @Value("${app.notifications.email.max-attempts:8}") int maxAttempts) {
        this.repository = repository;
        this.audit = audit;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** Called inside the verification decision transaction. */
    @Transactional
    public UUID enqueue(UUID verificationRequestId, UUID subjectUserId,
                        String recipientEmail, EmailNotificationType type) {
        var item = repository.saveAndFlush(new EmailOutboxEntity(UUID.randomUUID(), verificationRequestId,
                subjectUserId, recipientEmail, type, Instant.now()));
        audit.record(AuditEventCommand.of("auth.notification.email.queued", "SYSTEM", "auth",
                "USER", subjectUserId.toString(), "VERIFICATION_ATTEMPT", verificationRequestId.toString(),
                "SUCCESS", null, null, "EMAIL_QUEUED").withDetails(java.util.Map.of(
                        "notificationType", type.name(), "outboxId", item.getId().toString())));
        return item.getId();
    }

    /** Claim work in a short transaction; the SMTP call is made after this transaction commits. */
    @Transactional
    public List<UUID> claimDueBatch() {
        Instant now = Instant.now();
        var expired = repository.findTop10ByStatusAndLockedUntilLessThanEqualOrderByCreatedAtAsc(
                EmailOutboxStatus.PROCESSING, now);
        for (var item : expired) {
            item.retry(now, "PROCESSING_LEASE_EXPIRED", maxAttempts);
            audit.record(AuditEventCommand.of("auth.notification.email.lease_expired", "SYSTEM", "auth",
                    "USER", item.getSubjectUserId().toString(), "VERIFICATION_ATTEMPT", item.getVerificationRequestId().toString(),
                    "FAILURE", "PROCESSING_LEASE_EXPIRED", "PROCESSING", item.getStatus().name())
                    .withDetails(java.util.Map.of("notificationType", item.getNotificationType().name(),
                            "outboxId", item.getId().toString(), "attemptCount", item.getAttemptCount())));
        }
        var due = repository.findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                EmailOutboxStatus.PENDING, now);
        for (var item : due) item.claim(now.plus(Duration.ofMinutes(5)));
        return due.stream().map(EmailOutboxEntity::getId).toList();
    }

    @Transactional(readOnly = true)
    public EmailMessage message(UUID id) {
        var item = repository.findById(id).orElseThrow();
        if (item.getStatus() != EmailOutboxStatus.PROCESSING)
            throw new IllegalStateException("Email outbox item is not claimed");
        return new EmailMessage(item.getId(), item.getVerificationRequestId(),
                item.getRecipientEmail(), item.getNotificationType(), item.getAttemptCount());
    }

    @Transactional
    public void markSent(UUID id) {
        var item = repository.findById(id).orElseThrow();
        item.sent(Instant.now());
        audit.record(AuditEventCommand.of("auth.notification.email.sent", "SYSTEM", "auth",
                "USER", item.getSubjectUserId().toString(), "VERIFICATION_ATTEMPT", item.getVerificationRequestId().toString(),
                "SUCCESS", null, "PROCESSING", "SENT").withDetails(java.util.Map.of(
                        "notificationType", item.getNotificationType().name(), "outboxId", id.toString())));
    }

    @Transactional
    public void markFailed(UUID id, String safeErrorCode) {
        var item = repository.findById(id).orElseThrow();
        long delayMinutes = Math.min(360L, 1L << Math.min(item.getAttemptCount(), 8));
        Instant next = Instant.now().plus(Duration.ofMinutes(delayMinutes));
        item.retry(next, safeErrorCode, maxAttempts);
        audit.record(AuditEventCommand.of("auth.notification.email.failed", "SYSTEM", "auth",
                "USER", item.getSubjectUserId().toString(), "VERIFICATION_ATTEMPT", item.getVerificationRequestId().toString(),
                "FAILURE", safeErrorCode, "PROCESSING", item.getStatus().name())
                .withDetails(java.util.Map.of("notificationType", item.getNotificationType().name(),
                        "outboxId", id.toString(), "attemptCount", item.getAttemptCount())));
    }

    public record EmailMessage(UUID id, UUID verificationRequestId, String recipient,
                               EmailNotificationType type, int attemptCount) { }
}
