package com.example.videokyc.workflow.application.port.out;

import com.example.videokyc.workflow.domain.publication.PublicationJob;

import java.util.Optional;

/**
 * Outbound port for persisting and retrieving {@link PublicationJob} aggregate roots.
 */
public interface PublicationJobPort {

    /**
     * Saves or updates a publication job aggregate.
     */
    PublicationJob save(PublicationJob job);

    /**
     * Finds a publication job by its internal ID.
     */
    Optional<PublicationJob> findById(Long id);

    /**
     * Finds a publication job by the target version ID.
     */
    Optional<PublicationJob> findByVersionId(Long versionId);
}
