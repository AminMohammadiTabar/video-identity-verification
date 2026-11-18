package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.publication.PublicationJob;

import java.util.Optional;

/**
 * Inbound port for query operations on {@link PublicationJob} aggregates.
 */
public interface GetPublicationJobUseCase {

    Optional<PublicationJob> getById(Long jobId);

    Optional<PublicationJob> getByVersionId(Long versionId);
}
