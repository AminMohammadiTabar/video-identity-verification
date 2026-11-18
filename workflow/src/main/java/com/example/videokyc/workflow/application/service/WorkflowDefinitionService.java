package com.example.videokyc.workflow.application.service;

import com.example.videokyc.workflow.application.port.in.ChangeWorkflowStatusUseCase;
import com.example.videokyc.workflow.application.port.in.CreateWorkflowDefinitionUseCase;
import com.example.videokyc.workflow.application.port.in.GetWorkflowDefinitionUseCase;
import com.example.videokyc.workflow.application.port.in.UpdateWorkflowDefinitionUseCase;
import com.example.videokyc.workflow.application.port.out.WorkflowDefinitionPort;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import com.example.videokyc.workflow.domain.shared.DomainException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service managing the administrative lifecycle, creation, metadata,
 * and querying of {@link WorkflowDefinition} aggregate roots.
 */
@Service
@Transactional
public class WorkflowDefinitionService implements
        CreateWorkflowDefinitionUseCase,
        UpdateWorkflowDefinitionUseCase,
        ChangeWorkflowStatusUseCase,
        GetWorkflowDefinitionUseCase {

    private final WorkflowDefinitionPort definitionPort;
    private final Clock clock;

    public WorkflowDefinitionService(WorkflowDefinitionPort definitionPort) {
        this(definitionPort, Clock.systemUTC());
    }

    @Autowired
    public WorkflowDefinitionService(WorkflowDefinitionPort definitionPort,
                                     @Autowired(required = false) Clock clock) {
        this.definitionPort = definitionPort;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public WorkflowDefinition createDefinition(CreateDefinitionCommand command) {
        WorkflowKey key = WorkflowKey.of(command.workflowKey());
        if (definitionPort.existsByWorkflowKey(key)) {
            throw new DomainException("DUPLICATE_WORKFLOW_KEY",
                    "Workflow definition already exists with key: " + key.value());
        }

        Instant now = clock.instant();
        WorkflowDefinition definition = WorkflowDefinition.create(
                key,
                command.name(),
                command.description(),
                command.createdBy(),
                now
        );
        return definitionPort.save(definition);
    }

    @Override
    public WorkflowDefinition updateMetadata(UpdateDefinitionCommand command) {
        WorkflowDefinition definition = getDefinitionOrThrow(command.id());
        Instant now = clock.instant();
        definition.updateMetadata(command.name(), command.description(), command.updatedBy(), now);
        return definitionPort.save(definition);
    }

    @Override
    public WorkflowDefinition enable(Long id, String updatedBy) {
        WorkflowDefinition definition = getDefinitionOrThrow(id);
        Instant now = clock.instant();
        definition.enable(updatedBy, now);
        return definitionPort.save(definition);
    }

    @Override
    public WorkflowDefinition disable(Long id, String updatedBy) {
        WorkflowDefinition definition = getDefinitionOrThrow(id);
        Instant now = clock.instant();
        definition.disable(updatedBy, now);
        return definitionPort.save(definition);
    }

    @Override
    public WorkflowDefinition archive(Long id, String updatedBy) {
        WorkflowDefinition definition = getDefinitionOrThrow(id);
        Instant now = clock.instant();
        definition.archive(updatedBy, now);
        return definitionPort.save(definition);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> getById(Long id) {
        return definitionPort.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowDefinition> getByKey(WorkflowKey workflowKey) {
        return definitionPort.findByWorkflowKey(workflowKey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowDefinition> getAll() {
        return definitionPort.findAll();
    }

    private WorkflowDefinition getDefinitionOrThrow(Long id) {
        return definitionPort.findById(id)
                .orElseThrow(() -> new DomainException("DEFINITION_NOT_FOUND",
                        "Workflow definition not found with ID: " + id));
    }
}
