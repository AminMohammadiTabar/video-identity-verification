package com.example.videokyc.workflow.application.port.out;

import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and retrieving {@link WorkflowDefinition} aggregate roots.
 */
public interface WorkflowDefinitionPort {

    /**
     * Saves or updates a workflow definition aggregate.
     */
    WorkflowDefinition save(WorkflowDefinition definition);

    /**
     * Finds a workflow definition by its internal ID.
     */
    Optional<WorkflowDefinition> findById(Long id);

    /**
     * Finds a workflow definition by its business key.
     */
    Optional<WorkflowDefinition> findByWorkflowKey(WorkflowKey workflowKey);

    /**
     * Checks if a workflow definition exists for the given business key.
     */
    boolean existsByWorkflowKey(WorkflowKey workflowKey);

    /**
     * Returns all workflow definitions.
     */
    List<WorkflowDefinition> findAll();
}
