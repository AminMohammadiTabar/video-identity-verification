package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for query operations on {@link WorkflowDefinition}.
 */
public interface GetWorkflowDefinitionUseCase {

    Optional<WorkflowDefinition> getById(Long id);

    Optional<WorkflowDefinition> getByKey(WorkflowKey workflowKey);

    List<WorkflowDefinition> getAll();
}
