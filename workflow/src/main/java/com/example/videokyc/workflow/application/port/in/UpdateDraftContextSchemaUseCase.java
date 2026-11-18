package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.version.WorkflowVersion;

/**
 * Inbound port for updating context schema in a draft {@link WorkflowVersion}.
 */
public interface UpdateDraftContextSchemaUseCase {

    WorkflowVersion updateContextSchema(UpdateContextSchemaCommand command);

    record UpdateContextSchemaCommand(
            Long versionId,
            String contextSchemaJson
    ) {
        public UpdateContextSchemaCommand {
            if (versionId == null) {
                throw new IllegalArgumentException("versionId must not be null");
            }
            if (contextSchemaJson == null || contextSchemaJson.isBlank()) {
                throw new IllegalArgumentException("contextSchemaJson must not be blank");
            }
        }
    }
}
