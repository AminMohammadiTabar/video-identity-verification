package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.version.WorkflowVersion;

/**
 * Inbound port for creating new {@link WorkflowVersion} drafts.
 */
public interface CreateDraftVersionUseCase {

    WorkflowVersion createDraft(CreateDraftVersionCommand command);

    record CreateDraftVersionCommand(
            Long definitionId,
            String contextSchemaJson,
            Long cloneFromVersionId,
            String createdBy
    ) {
        public CreateDraftVersionCommand {
            if (definitionId == null) {
                throw new IllegalArgumentException("definitionId must not be null");
            }
            if (createdBy == null || createdBy.isBlank()) {
                throw new IllegalArgumentException("createdBy must not be blank");
            }
        }
    }
}
