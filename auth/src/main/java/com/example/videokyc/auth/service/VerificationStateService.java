package com.example.videokyc.auth.service;

import com.example.videokyc.auth.domain.EmailNotificationType;
import com.example.videokyc.auth.domain.VerificationRequestEntity;
import com.example.videokyc.auth.domain.VerificationStatus;
import com.example.videokyc.auth.observability.audit.AuditEventCommand;
import com.example.videokyc.auth.observability.audit.AuditEventRecorder;
import com.example.videokyc.auth.repository.UserRepository;
import com.example.videokyc.auth.repository.VerificationRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Short transactions only. Never hold a DB lock across a remote call to DMS. */
@Service
public class VerificationStateService {
    private final UserRepository users;
    private final VerificationRequestRepository requests;
    private final AuditEventRecorder audit;
    private final EmailOutboxService emailOutbox;

    public VerificationStateService(UserRepository users, VerificationRequestRepository requests,
                                    AuditEventRecorder audit, EmailOutboxService emailOutbox) {
        this.users = users;
        this.requests = requests;
        this.audit = audit;
        this.emailOutbox = emailOutbox;
    }

    @Transactional
    public UUID reserve(UUID userId) {
        var user = users.lockById(userId).orElseThrow(() ->
                new VerificationException(HttpStatus.UNAUTHORIZED, "Account is not available"));
        var latest = requests.findTopByUser_IdOrderByAttemptNumberDesc(userId);
        if (latest.isPresent()) {
            var previous = latest.get();
            if (previous.getStatus() == VerificationStatus.UPLOAD_FAILED) {
                String old = previous.getStatus().name();
                previous.retryUpload();
                audit.record(AuditEventCommand.of("auth.verification.upload.retry_started", "USER",
                        userId.toString(), "USER", userId.toString(), "VERIFICATION_ATTEMPT",
                        previous.getId().toString(), "SUCCESS", null, old, previous.getStatus().name()));
                return previous.getId();
            }
            if (previous.getStatus() != VerificationStatus.REJECTED) {
                audit.recordIndependent(AuditEventCommand.of("auth.verification.attempt.denied", "USER",
                        userId.toString(), "USER", userId.toString(), "VERIFICATION_ATTEMPT",
                        previous.getId().toString(), "DENIED",
                        previous.getStatus() == VerificationStatus.APPROVED ? "ALREADY_APPROVED" : "ACTIVE_ATTEMPT_EXISTS",
                        previous.getStatus().name(), previous.getStatus().name()));
                throw new VerificationException(HttpStatus.CONFLICT,
                        "An active or approved verification attempt already exists");
            }
            int nextAttempt = previous.getAttemptNumber() + 1;
            var next = requests.saveAndFlush(new VerificationRequestEntity(user, nextAttempt));
            audit.record(AuditEventCommand.of("auth.verification.attempt.created", "USER",
                    userId.toString(), "USER", userId.toString(), "VERIFICATION_ATTEMPT",
                    next.getId().toString(), "SUCCESS", null, previous.getStatus().name(),
                    VerificationStatus.UPLOADING.name()).withDetails(java.util.Map.of(
                            "attemptNumber", nextAttempt, "previousAttemptNumber", previous.getAttemptNumber())));
            return next.getId();
        }
        var first = requests.saveAndFlush(new VerificationRequestEntity(user, 1));
        audit.record(AuditEventCommand.of("auth.verification.attempt.created", "USER",
                userId.toString(), "USER", userId.toString(), "VERIFICATION_ATTEMPT",
                first.getId().toString(), "SUCCESS", null, null, VerificationStatus.UPLOADING.name())
                .withDetails(java.util.Map.of("attemptNumber", 1)));
        return first.getId();
    }

    @Transactional
    public void complete(UUID requestId, UUID fileId) {
        var request = requests.lockById(requestId).orElseThrow();
        String previous = request.getStatus().name();
        request.uploaded(fileId);
        audit.record(AuditEventCommand.of("auth.verification.submitted", "USER",
                request.getUser().getId().toString(), "USER", request.getUser().getId().toString(),
                "VERIFICATION_ATTEMPT", requestId.toString(), "SUCCESS", null, previous,
                request.getStatus().name()).withDetails(java.util.Map.of("dmsFileId", fileId.toString(),
                "attemptNumber", request.getAttemptNumber())));
    }

    @Transactional
    public void fail(UUID requestId) {
        var request = requests.lockById(requestId).orElseThrow();
        if (request.getStatus() == VerificationStatus.UPLOADING) {
            String previous = request.getStatus().name();
            request.uploadFailed();
            audit.record(AuditEventCommand.of("auth.verification.upload.failed", "USER",
                    request.getUser().getId().toString(), "USER", request.getUser().getId().toString(),
                    "VERIFICATION_ATTEMPT", requestId.toString(), "FAILURE", "DMS_UPLOAD_FAILED",
                    previous, request.getStatus().name()).withDetails(java.util.Map.of(
                            "attemptNumber", request.getAttemptNumber())));
        }
    }

    @Transactional
    public void review(UUID requestId, UUID reviewerId, boolean approved, String reason) {
        var request = requests.lockById(requestId).orElseThrow(() ->
                new VerificationException(HttpStatus.NOT_FOUND, "Verification request not found"));
        if (request.getStatus() != VerificationStatus.PENDING_REVIEW) {
            audit.recordIndependent(AuditEventCommand.of("auth.verification.review.denied", "ADMIN",
                    reviewerId.toString(), "USER", request.getUser().getId().toString(),
                    "VERIFICATION_ATTEMPT", requestId.toString(), "DENIED", "REQUEST_NOT_PENDING",
                    request.getStatus().name(), request.getStatus().name()));
            throw new VerificationException(HttpStatus.CONFLICT, "Request has already been reviewed");
        }
        String previous = request.getStatus().name();
        try {
            request.decide(reviewerId, approved, reason);
        } catch (IllegalArgumentException e) {
            audit.recordIndependent(AuditEventCommand.of("auth.verification.review.denied", "ADMIN",
                    reviewerId.toString(), "USER", request.getUser().getId().toString(),
                    "VERIFICATION_ATTEMPT", requestId.toString(), "DENIED", "INVALID_REJECTION_REASON",
                    previous, previous));
            throw new VerificationException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        String event = approved ? "auth.verification.approved" : "auth.verification.rejected";
        audit.record(AuditEventCommand.of(event, "ADMIN", reviewerId.toString(), "USER",
                request.getUser().getId().toString(), "VERIFICATION_ATTEMPT", requestId.toString(),
                "SUCCESS", approved ? null : "ADMIN_REJECTED", previous, request.getStatus().name())
                .withDetails(java.util.Map.of("attemptNumber", request.getAttemptNumber())));
        emailOutbox.enqueue(request.getId(), request.getUser().getId(), request.getUser().getEmail(), approved
                ? EmailNotificationType.VERIFICATION_APPROVED : EmailNotificationType.VERIFICATION_REJECTED);
    }
}
