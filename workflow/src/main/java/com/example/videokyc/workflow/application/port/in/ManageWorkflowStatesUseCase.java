package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.state.StateType;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;

/**
 * Inbound port for managing state nodes in a draft {@link WorkflowVersion}.
 */
public interface ManageWorkflowStatesUseCase {

    WorkflowVersion addState(AddStateCommand command);

    WorkflowVersion updateState(UpdateStateCommand command);

    WorkflowVersion removeState(RemoveStateCommand command);

    record AddStateCommand(
            Long versionId,
            String stateKey,
            String name,
            String description,
            StateType stateType,
            int displayOrder,
            String uiMetadata
    ) {
        public AddStateCommand {
            if (versionId == null) throw new IllegalArgumentException("versionId must not be null");
            if (stateKey == null || stateKey.isBlank()) throw new IllegalArgumentException("stateKey must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (stateType == null) throw new IllegalArgumentException("stateType must not be null");
        }
    }

    record UpdateStateCommand(
            Long versionId,
            String stateKey,
            String name,
            String description,
            StateType stateType,
            int displayOrder,
            String uiMetadata
    ) {
        public UpdateStateCommand {
            if (versionId == null) throw new IllegalArgumentException("versionId must not be null");
            if (stateKey == null || stateKey.isBlank()) throw new IllegalArgumentException("stateKey must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (stateType == null) throw new IllegalArgumentException("stateType must not be null");
        }
    }

    record RemoveStateCommand(
            Long versionId,
            String stateKey
    ) {
        public RemoveStateCommand {
            if (versionId == null) throw new IllegalArgumentException("versionId must not be null");
            if (stateKey == null || stateKey.isBlank()) throw new IllegalArgumentException("stateKey must not be blank");
        }
    }
}
