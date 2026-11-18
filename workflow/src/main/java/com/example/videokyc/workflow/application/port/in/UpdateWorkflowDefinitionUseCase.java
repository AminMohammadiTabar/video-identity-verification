package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;

/**
 * Inbound port for updating descriptive metadata of a {@link WorkflowDefinition}.
 */
public interface UpdateWorkflowDefinitionUseCase {

    WorkflowDefinition updateMetadata(UpdateDefinitionCommand command);

    record UpdateDefinitionCommand(
            Long id,
            String name,
            String description,
            String updatedBy
    ) {
        public UpdateDefinitionCommand {
            if (id == null) {
                throw new IllegalArgumentException("id must not be null");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (updatedBy == null || updatedBy.isBlank()) {
                throw new IllegalArgumentException("updatedBy must not be blank");
            }
        }
    }
}
