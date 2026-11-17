package com.example.videokyc.auth.service;

import com.example.videokyc.auth.domain.VerificationRequestEntity;
import com.example.videokyc.auth.domain.VerificationStatus;
import com.example.videokyc.auth.observability.audit.AuditEventCommand;
import com.example.videokyc.auth.observability.audit.AuditEventRecorder;
import com.example.videokyc.auth.repository.VerificationRequestRepository;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Application service orchestrating end-to-end video verification workflows.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Coordinates verification reservations with {@link VerificationStateService}.</li>
 *   <li>Uploads video payloads securely to DMS via {@link DmsGateway} outside database transactions.</li>
 *   <li>Handles compensation and logs orphaned files for operator reconciliation upon communication failure.</li>
 *   <li>Provides views for user attempt history and administrative review queues.</li>
 *   <li>Tracks operations with Micrometer {@link Observation} and W3C distributed tracing context.</li>
 * </ul>
 */
@Service
public class VerificationService {
    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private final VerificationStateService states;
    private final VerificationRequestRepository requests;
    private final DmsGateway dms;
    private final ObservationRegistry observations;
    private final AuditEventRecorder audit;

    public VerificationService(VerificationStateService states, VerificationRequestRepository requests,
                               DmsGateway dms, ObservationRegistry observations, AuditEventRecorder audit) {
        this.states = states;
        this.requests = requests;
        this.dms = dms;
        this.observations = observations;
        this.audit = audit;
    }

    public VerificationView mine(UUID userId) {
        return requests.findTopByUser_IdOrderByAttemptNumberDesc(userId).map(VerificationView::from)
                .orElse(new VerificationView(null, 0, VerificationStatus.NOT_SUBMITTED, null, null, null, null));
    }

    public List<VerificationView> history(UUID userId) {
        return requests.findByUser_IdOrderByAttemptNumberDesc(userId).stream()
                .map(VerificationView::from).toList();
    }

    /** Reserve in a short transaction; never hold a database lock during the remote upload. */
    public VerificationView submit(UUID userId, MultipartFile video) {
        if (video == null || video.isEmpty()) {
            audit.recordIndependent(AuditEventCommand.of("auth.verification.upload.denied", "USER",
                    userId.toString(), "USER", userId.toString(), "VERIFICATION", null,
                    "DENIED", "EMPTY_VIDEO", null, null));
            throw new VerificationException(HttpStatus.BAD_REQUEST, "Video is required");
        }
        UUID requestId = states.reserve(userId);
        UUID fileId;
        try {
            fileId = Observation.createNotStarted("auth.verification.dms-upload", observations)
                    .lowCardinalityKeyValue("service", "dms")
                    .observe(() -> dms.uploadUserVideo(userId, video));
        } catch (RuntimeException error) {
            markFailedSafely(requestId);
            if (error instanceof VerificationException known) throw known;
            throw new VerificationException(HttpStatus.BAD_GATEWAY, "Video storage failed");
        }

        try {
            states.complete(requestId, fileId);
        } catch (RuntimeException error) {
            // Commit outcome can be uncertain. Never delete a DMS file that might already be linked.
            try {
                var current = requests.findById(requestId).orElseThrow();
                if (current.getStatus() == VerificationStatus.PENDING_REVIEW && fileId.equals(current.getDmsFileId())) {
                    return VerificationView.from(current);
                }
                if (current.getStatus() == VerificationStatus.UPLOADING) {
                    states.fail(requestId);
                    try { dms.cleanup(fileId); }
                    catch (RuntimeException compensationError) { logOrphan(fileId, requestId); }
                } else {
                    logOrphan(fileId, requestId);
                }
            } catch (RuntimeException recoveryError) {
                logOrphan(fileId, requestId);
            }
            throw new VerificationException(HttpStatus.BAD_GATEWAY,
                    "Unable to finalize verification; support may need to reconcile the upload");
        }
        return requests.findById(requestId).map(VerificationView::from).orElseThrow();
    }

    private void markFailedSafely(UUID requestId) {
        try { states.fail(requestId); }
        catch (RuntimeException e) {
            log.atError().addKeyValue("event", "verification.recovery")
                    .addKeyValue("requestId", requestId.toString())
                    .log("Verification upload failure could not be persisted");
        }
    }

    private void logOrphan(UUID fileId, UUID requestId) {
        log.atError().addKeyValue("event", "verification.orphan")
                .addKeyValue("fileId", fileId.toString())
                .addKeyValue("requestId", requestId.toString())
                .log("DMS upload needs operator reconciliation");
    }

    public List<AdminVerificationView> pending() {
        return requests.findByStatusOrderBySubmittedAtAsc(VerificationStatus.PENDING_REVIEW)
                .stream().map(AdminVerificationView::from).toList();
    }

    public VerificationView decide(UUID requestId, UUID reviewerId, boolean approved, String reason) {
        return Observation.createNotStarted("auth.verification.review", observations)
                .lowCardinalityKeyValue("decision", approved ? "approved" : "rejected")
                .observe(() -> {
                    states.review(requestId, reviewerId, approved, reason);
                    return requests.findById(requestId).map(VerificationView::from).orElseThrow();
                });
    }

    public DmsGateway.VideoContent adminVideo(UUID requestId) {
        var request = requests.findById(requestId).orElseThrow(() ->
                new VerificationException(HttpStatus.NOT_FOUND, "Verification request not found"));
        if (request.getDmsFileId() == null) throw new VerificationException(HttpStatus.NOT_FOUND, "No video is available");
        return Observation.createNotStarted("auth.verification.admin-video", observations)
                .lowCardinalityKeyValue("service", "dms")
                .observe(() -> dms.downloadVideo(request.getDmsFileId()));
    }

    public record VerificationView(UUID id, int attemptNumber, VerificationStatus status, UUID dmsFileId,
                                   java.time.Instant submittedAt, java.time.Instant reviewedAt, String reviewReason) {
        static VerificationView from(VerificationRequestEntity e) {
            return new VerificationView(e.getId(), e.getAttemptNumber(), e.getStatus(), e.getDmsFileId(),
                    e.getSubmittedAt(), e.getReviewedAt(), e.getReviewReason());
        }
    }
    public record AdminVerificationView(UUID id, int attemptNumber, UUID userId, String userEmail,
                                         java.time.Instant submittedAt) {
        static AdminVerificationView from(VerificationRequestEntity e) {
            return new AdminVerificationView(e.getId(), e.getAttemptNumber(), e.getUser().getId(),
                    e.getUser().getEmail(), e.getSubmittedAt());
        }
    }
}
