package com.example.videokyc.workflow.adapter.out.persistence;

import com.example.videokyc.workflow.adapter.out.persistence.mapper.PublicationJobPersistenceMapper;
import com.example.videokyc.workflow.application.port.out.PublicationJobPort;
import com.example.videokyc.workflow.domain.publication.PublicationJob;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Persistence adapter implementing {@link PublicationJobPort} using Spring Data JPA.
 */
@Component
public class PublicationJobPersistenceAdapter implements PublicationJobPort {

    private final PublicationJobRepository repository;
    private final PublicationJobPersistenceMapper mapper;

    public PublicationJobPersistenceAdapter(PublicationJobRepository repository,
                                            PublicationJobPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public PublicationJob save(PublicationJob job) {
        PublicationJobEntity entity = mapper.toEntity(job);
        PublicationJobEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PublicationJob> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PublicationJob> findByVersionId(Long versionId) {
        return repository.findByVersionId(versionId).map(mapper::toDomain);
    }
}
