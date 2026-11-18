package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;

/**
 * Inbound port for creating a new {@link WorkflowDefinition}.
 */
public interface CreateWorkflowDefinitionUseCase {

    WorkflowDefinition createDefinition(CreateDefinitionCommand command);

    record CreateDefinitionCommand(
            String workflowKey,
            String name,
            String description,
            String createdBy
    ) {
        public CreateDefinitionCommand {
            if (workflowKey == null || workflowKey.isBlank()) {
                throw new IllegalArgumentException("workflowKey must not be blank");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (createdBy == null || createdBy.isBlank()) {
                throw new IllegalArgumentException("createdBy must not be blank");
            }
        }
    }
}
