package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;

/**
 * Inbound port for managing administrative lifecycle states (ACTIVE, DISABLED, ARCHIVED)
 * of a {@link WorkflowDefinition}.
 */
public interface ChangeWorkflowStatusUseCase {

    WorkflowDefinition enable(Long id, String updatedBy);

    WorkflowDefinition disable(Long id, String updatedBy);

    WorkflowDefinition archive(Long id, String updatedBy);
}
