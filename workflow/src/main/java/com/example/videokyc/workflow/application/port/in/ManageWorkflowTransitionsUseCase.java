package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.version.WorkflowVersion;

/**
 * Inbound port for managing transitions in a draft {@link WorkflowVersion}.
 */
public interface ManageWorkflowTransitionsUseCase {

    WorkflowVersion addTransition(AddTransitionCommand command);

    WorkflowVersion updateTransition(UpdateTransitionCommand command);

    WorkflowVersion removeTransition(RemoveTransitionCommand command);

    record AddTransitionCommand(
            Long versionId,
            String transitionKey,
            String name,
            String description,
            String fromStateKey,
            String toStateKey,
            String guardExpressionJson,
            int displayOrder
    ) {
        public AddTransitionCommand {
            if (versionId == null) throw new IllegalArgumentException("versionId must not be null");
            if (transitionKey == null || transitionKey.isBlank()) throw new IllegalArgumentException("transitionKey must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (fromStateKey == null || fromStateKey.isBlank()) throw new IllegalArgumentException("fromStateKey must not be blank");
            if (toStateKey == null || toStateKey.isBlank()) throw new IllegalArgumentException("toStateKey must not be blank");
        }
    }

    record UpdateTransitionCommand(
            Long versionId,
            String transitionKey,
            String name,
            String description,
            String fromStateKey,
            String toStateKey,
            String guardExpressionJson,
            int displayOrder
    ) {
        public UpdateTransitionCommand {
            if (versionId == null) throw new IllegalArgumentException("versionId must not be null");
            if (transitionKey == null || transitionKey.isBlank()) throw new IllegalArgumentException("transitionKey must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (fromStateKey == null || fromStateKey.isBlank()) throw new IllegalArgumentException("fromStateKey must not be blank");
            if (toStateKey == null || toStateKey.isBlank()) throw new IllegalArgumentException("toStateKey must not be blank");
        }
    }

    record RemoveTransitionCommand(
            Long versionId,
            String transitionKey
    ) {
        public RemoveTransitionCommand {
            if (versionId == null) throw new IllegalArgumentException("versionId must not be null");
            if (transitionKey == null || transitionKey.isBlank()) throw new IllegalArgumentException("transitionKey must not be blank");
        }
    }
}
