package com.example.videokyc.workflow.domain;

import com.example.videokyc.workflow.domain.publication.PublicationJob;
import com.example.videokyc.workflow.domain.publication.PublicationJobStatus;
import com.example.videokyc.workflow.domain.shared.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicationJobTest {

    private final Instant now = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    @DisplayName("Should successfully track publication job lifecycle")
    void shouldTrackJobLifecycle() {
        PublicationJob job = PublicationJob.create(1L, 3, now);

        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.PENDING);
        assertThat(job.getAttemptCount()).isZero();
        assertThat(job.getMaxAttempts()).isEqualTo(3);

        // Worker claims job
        job.claim("worker-1", Duration.ofMinutes(5), now);
        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.PROCESSING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getLockedBy()).isEqualTo("worker-1");
        assertThat(job.getLockedUntil()).isEqualTo(now.plus(Duration.ofMinutes(5)));

        // Job completes successfully
        job.markSucceeded(now.plusSeconds(30));
        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.SUCCEEDED);
        assertThat(job.getLockedBy()).isNull();
        assertThat(job.getLockedUntil()).isNull();
        assertThat(job.getCompletedAt()).isEqualTo(now.plusSeconds(30));
    }

    @Test
    @DisplayName("Should transition to RETRY_WAIT and exhaust attempts to FAILED")
    void shouldHandleRetriesAndFailures() {
        PublicationJob job = PublicationJob.create(1L, 2, now);

        // Attempt 1 fails transiently
        job.claim("worker-1", Duration.ofMinutes(5), now);
        job.markRetryWait(Duration.ofSeconds(15), "REDIS_TIMEOUT", "Connection timed out", now.plusSeconds(10));
        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.RETRY_WAIT);
        assertThat(job.getNextAttemptAt()).isEqualTo(now.plusSeconds(25));
        assertThat(job.getLockedBy()).isNull();

        // Attempt 2 fails and exhausts max attempts (2)
        Instant attempt2Time = now.plusSeconds(25);
        job.claim("worker-2", Duration.ofMinutes(5), attempt2Time);
        job.markRetryWait(Duration.ofSeconds(45), "REDIS_TIMEOUT", "Connection timed out again", attempt2Time.plusSeconds(5));

        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.FAILED);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getLastErrorCode()).isEqualTo("REDIS_TIMEOUT");

        // Administrative manual retry resets to PENDING
        job.manualRetry(attempt2Time.plusSeconds(100));
        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.PENDING);
        assertThat(job.getLastErrorCode()).isNull();
    }

    @Test
    @DisplayName("Should recover expired lease safely")
    void shouldRecoverExpiredLease() {
        PublicationJob job = PublicationJob.create(1L, 5, now);
        job.claim("worker-1", Duration.ofMinutes(2), now);

        // Before lease expiration: no recovery
        job.recoverExpiredLease(now.plus(Duration.ofMinutes(1)));
        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.PROCESSING);
        assertThat(job.getLockedBy()).isEqualTo("worker-1");

        // After lease expiration: recovered to RETRY_WAIT
        Instant pastLease = now.plus(Duration.ofMinutes(3));
        job.recoverExpiredLease(pastLease);
        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.RETRY_WAIT);
        assertThat(job.getLockedBy()).isNull();
        assertThat(job.getLockedUntil()).isNull();
    }
}
