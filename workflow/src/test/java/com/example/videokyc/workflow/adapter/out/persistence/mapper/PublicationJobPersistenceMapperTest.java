package com.example.videokyc.workflow.adapter.out.persistence.mapper;

import com.example.videokyc.workflow.adapter.out.persistence.PublicationJobEntity;
import com.example.videokyc.workflow.domain.publication.PublicationJob;
import com.example.videokyc.workflow.domain.publication.PublicationJobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PublicationJobPersistenceMapperTest {

    private final PublicationJobPersistenceMapper mapper = new PublicationJobPersistenceMapper();

    @Test
    @DisplayName("Should map PublicationJob domain aggregate to entity and back without data loss")
    void shouldMapPublicationJobToEntityAndBack() {
        Instant now = Instant.parse("2026-11-18T10:00:00Z");
        PublicationJob domain = new PublicationJob(
                55L,
                2001L,
                PublicationJobStatus.PROCESSING,
                2,
                5,
                now.plusSeconds(30),
                "worker-42",
                now.plusSeconds(120),
                "ERR_TIMEOUT",
                "Connection timed out",
                now,
                now.plusSeconds(5),
                null,
                now.plusSeconds(10),
                1L
        );

        PublicationJobEntity entity = mapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(55L);
        assertThat(entity.getVersionId()).isEqualTo(2001L);
        assertThat(entity.getStatus()).isEqualTo("PROCESSING");
        assertThat(entity.getAttemptCount()).isEqualTo(2);
        assertThat(entity.getMaxAttempts()).isEqualTo(5);
        assertThat(entity.getNextAttemptAt()).isEqualTo(now.plusSeconds(30));
        assertThat(entity.getLockedBy()).isEqualTo("worker-42");
        assertThat(entity.getLockedUntil()).isEqualTo(now.plusSeconds(120));
        assertThat(entity.getLastErrorCode()).isEqualTo("ERR_TIMEOUT");
        assertThat(entity.getLastErrorMessage()).isEqualTo("Connection timed out");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getStartedAt()).isEqualTo(now.plusSeconds(5));
        assertThat(entity.getCompletedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isEqualTo(now.plusSeconds(10));
        assertThat(entity.getRowVersion()).isEqualTo(1L);

        PublicationJob roundtripped = mapper.toDomain(entity);

        assertThat(roundtripped).isNotNull();
        assertThat(roundtripped.getId()).isEqualTo(domain.getId());
        assertThat(roundtripped.getVersionId()).isEqualTo(domain.getVersionId());
        assertThat(roundtripped.getStatus()).isEqualTo(domain.getStatus());
        assertThat(roundtripped.getAttemptCount()).isEqualTo(domain.getAttemptCount());
        assertThat(roundtripped.getMaxAttempts()).isEqualTo(domain.getMaxAttempts());
        assertThat(roundtripped.getNextAttemptAt()).isEqualTo(domain.getNextAttemptAt());
        assertThat(roundtripped.getLockedBy()).isEqualTo(domain.getLockedBy());
        assertThat(roundtripped.getLockedUntil()).isEqualTo(domain.getLockedUntil());
        assertThat(roundtripped.getLastErrorCode()).isEqualTo(domain.getLastErrorCode());
        assertThat(roundtripped.getLastErrorMessage()).isEqualTo(domain.getLastErrorMessage());
        assertThat(roundtripped.getCreatedAt()).isEqualTo(domain.getCreatedAt());
        assertThat(roundtripped.getStartedAt()).isEqualTo(domain.getStartedAt());
        assertThat(roundtripped.getCompletedAt()).isEqualTo(domain.getCompletedAt());
        assertThat(roundtripped.getUpdatedAt()).isEqualTo(domain.getUpdatedAt());
        assertThat(roundtripped.getRowVersion()).isEqualTo(domain.getRowVersion());
    }

    @Test
    @DisplayName("Should handle null entity and domain gracefully")
    void shouldHandleNull() {
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toEntity(null)).isNull();
    }
}
