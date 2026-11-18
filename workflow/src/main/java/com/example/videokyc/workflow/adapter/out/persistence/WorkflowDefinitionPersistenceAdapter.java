package com.example.videokyc.workflow.adapter.out.persistence;

import com.example.videokyc.workflow.adapter.out.persistence.entity.*;
import com.example.videokyc.workflow.adapter.out.persistence.repository.*;

import com.example.videokyc.workflow.adapter.out.persistence.mapper.WorkflowDefinitionPersistenceMapper;
import com.example.videokyc.workflow.application.port.out.WorkflowDefinitionPort;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter implementing {@link WorkflowDefinitionPort} using Spring Data JPA.
 */
@Component
public class WorkflowDefinitionPersistenceAdapter implements WorkflowDefinitionPort {

    private final WorkflowDefinitionRepository repository;
    private final WorkflowDefinitionPersistenceMapper mapper;

    public WorkflowDefinitionPersistenceAdapter(WorkflowDefinitionRepository repository,
                                                WorkflowDefinitionPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public WorkflowDefinition save(WorkflowDefinition definition) {
        WorkflowDefinitionEntity entity = mapper.toEntity(definition);
        WorkflowDefinitionEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<WorkflowDefinition> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<WorkflowDefinition> findByWorkflowKey(WorkflowKey workflowKey) {
        return repository.findByWorkflowKey(workflowKey.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByWorkflowKey(WorkflowKey workflowKey) {
        return repository.existsByWorkflowKey(workflowKey.value());
    }

    @Override
    public List<WorkflowDefinition> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }
}
