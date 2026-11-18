package com.example.videokyc.workflow.adapter.out.persistence.mapper;

import com.example.videokyc.workflow.adapter.out.persistence.entity.PublicationJobEntity;
import com.example.videokyc.workflow.domain.publication.PublicationJob;
import com.example.videokyc.workflow.domain.publication.PublicationJobStatus;
import org.springframework.stereotype.Component;

/**
 * Mapper between {@link PublicationJob} domain aggregate and {@link PublicationJobEntity} JPA entity.
 */
@Component
public class PublicationJobPersistenceMapper {

    public PublicationJob toDomain(PublicationJobEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PublicationJob(
                entity.getId(),
                entity.getVersionId(),
                PublicationJobStatus.valueOf(entity.getStatus()),
                entity.getAttemptCount(),
                entity.getMaxAttempts(),
                entity.getNextAttemptAt(),
                entity.getLockedBy(),
                entity.getLockedUntil(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getUpdatedAt(),
                entity.getRowVersion()
        );
    }

    public PublicationJobEntity toEntity(PublicationJob domain) {
        if (domain == null) {
            return null;
        }
        PublicationJobEntity entity = new PublicationJobEntity(
                domain.getVersionId(),
                domain.getStatus().name(),
                domain.getAttemptCount(),
                domain.getMaxAttempts(),
                domain.getNextAttemptAt(),
                domain.getLockedBy(),
                domain.getLockedUntil(),
                domain.getLastErrorCode(),
                domain.getLastErrorMessage(),
                domain.getCreatedAt(),
                domain.getStartedAt(),
                domain.getCompletedAt(),
                domain.getUpdatedAt()
        );
        entity.setId(domain.getId());
        entity.setRowVersion(domain.getRowVersion());
        return entity;
    }
}
