package com.example.videokyc.workflow.application.service;

import com.example.videokyc.workflow.application.port.in.CreateDraftVersionUseCase;
import com.example.videokyc.workflow.application.port.in.GetWorkflowVersionUseCase;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowStatesUseCase;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowTransitionsUseCase;
import com.example.videokyc.workflow.application.port.in.UpdateDraftContextSchemaUseCase;
import com.example.videokyc.workflow.application.port.out.WorkflowDefinitionPort;
import com.example.videokyc.workflow.application.port.out.WorkflowVersionPort;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinitionStatus;
import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.state.StateKey;
import com.example.videokyc.workflow.domain.state.WorkflowState;
import com.example.videokyc.workflow.domain.transition.TransitionKey;
import com.example.videokyc.workflow.domain.transition.WorkflowTransition;
import com.example.videokyc.workflow.domain.transition.guard.GuardExpression;
import com.example.videokyc.workflow.domain.version.ContextSchema;
import com.example.videokyc.workflow.domain.version.VersionNumber;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service managing the creation, cloning, state graph, and transition graph
 * of draft {@link WorkflowVersion} aggregates.
 */
@Service
@Transactional
public class WorkflowDraftService implements
        CreateDraftVersionUseCase,
        UpdateDraftContextSchemaUseCase,
        ManageWorkflowStatesUseCase,
        ManageWorkflowTransitionsUseCase,
        GetWorkflowVersionUseCase {

    private final WorkflowDefinitionPort definitionPort;
    private final WorkflowVersionPort versionPort;
    private final Clock clock;

    public WorkflowDraftService(WorkflowDefinitionPort definitionPort, WorkflowVersionPort versionPort) {
        this(definitionPort, versionPort, Clock.systemUTC());
    }

    @Autowired
    public WorkflowDraftService(WorkflowDefinitionPort definitionPort,
                                WorkflowVersionPort versionPort,
                                @Autowired(required = false) Clock clock) {
        this.definitionPort = definitionPort;
        this.versionPort = versionPort;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public WorkflowVersion createDraft(CreateDraftVersionCommand command) {
        WorkflowDefinition definition = definitionPort.findById(command.definitionId())
                .orElseThrow(() -> new DomainException("DEFINITION_NOT_FOUND",
                        "Workflow definition not found with ID: " + command.definitionId()));

        if (definition.getStatus() == WorkflowDefinitionStatus.ARCHIVED) {
            throw new DomainException("DEFINITION_ARCHIVED",
                    "Cannot create a draft version for an archived workflow definition: " + command.definitionId());
        }

        int nextVersionNumber = versionPort.findMaxVersionNumberByDefinitionId(command.definitionId()).orElse(0) + 1;
        VersionNumber versionNumber = VersionNumber.of(nextVersionNumber);

        ContextSchema schema;
        WorkflowVersion sourceToClone = null;
        if (command.cloneFromVersionId() != null) {
            sourceToClone = versionPort.findById(command.cloneFromVersionId())
                    .orElseThrow(() -> new DomainException("SOURCE_VERSION_NOT_FOUND",
                            "Source version to clone from not found with ID: " + command.cloneFromVersionId()));

            if (!sourceToClone.getDefinitionId().equals(command.definitionId())) {
                throw new DomainException("INVALID_CLONE_SOURCE",
                        "Source version does not belong to workflow definition: " + command.definitionId());
            }

            if (command.contextSchemaJson() != null && !command.contextSchemaJson().isBlank()) {
                schema = ContextSchema.of(command.contextSchemaJson());
            } else {
                schema = sourceToClone.getContextSchema();
            }
        } else {
            if (command.contextSchemaJson() != null && !command.contextSchemaJson().isBlank()) {
                schema = ContextSchema.of(command.contextSchemaJson());
            } else {
                schema = ContextSchema.defaultSchema();
            }
        }

        Instant now = clock.instant();
        WorkflowVersion draft = WorkflowVersion.createDraft(
                command.definitionId(),
                versionNumber,
                schema,
                command.createdBy(),
                now
        );

        if (sourceToClone != null) {
            for (WorkflowState sourceState : sourceToClone.getStates().values()) {
                draft.addState(WorkflowState.create(
                        sourceState.getStateKey(),
                        sourceState.getName(),
                        sourceState.getDescription(),
                        sourceState.getStateType(),
                        sourceState.getDisplayOrder(),
                        sourceState.getUiMetadata()
                ));
            }

            for (WorkflowTransition sourceTransition : sourceToClone.getTransitions().values()) {
                draft.addTransition(WorkflowTransition.create(
                        sourceTransition.getTransitionKey(),
                        sourceTransition.getName(),
                        sourceTransition.getDescription(),
                        sourceTransition.getFromStateKey(),
                        sourceTransition.getToStateKey(),
                        sourceTransition.getGuardExpression(),
                        sourceTransition.getDisplayOrder()
                ));
            }
        }

        return versionPort.save(draft);
    }

    @Override
    public WorkflowVersion updateContextSchema(UpdateContextSchemaCommand command) {
        WorkflowVersion version = getVersionOrThrow(command.versionId());
        ContextSchema schema = ContextSchema.of(command.contextSchemaJson());
        version.updateContextSchema(schema);
        return versionPort.save(version);
    }

    @Override
    public WorkflowVersion addState(AddStateCommand command) {
        WorkflowVersion version = getVersionOrThrow(command.versionId());
        WorkflowState state = WorkflowState.create(
                StateKey.of(command.stateKey()),
                command.name(),
                command.description(),
                command.stateType(),
                command.displayOrder(),
                command.uiMetadata()
        );
        version.addState(state);
        return versionPort.save(version);
    }

    @Override
    public WorkflowVersion updateState(UpdateStateCommand command) {
        WorkflowVersion version = getVersionOrThrow(command.versionId());
        version.updateState(
                StateKey.of(command.stateKey()),
                command.name(),
                command.description(),
                command.stateType(),
                command.displayOrder(),
                command.uiMetadata()
        );
        return versionPort.save(version);
    }

    @Override
    public WorkflowVersion removeState(RemoveStateCommand command) {
        WorkflowVersion version = getVersionOrThrow(command.versionId());
        version.removeState(StateKey.of(command.stateKey()));
        return versionPort.save(version);
    }

    @Override
    public WorkflowVersion addTransition(AddTransitionCommand command) {
        WorkflowVersion version = getVersionOrThrow(command.versionId());
        GuardExpression guard = (command.guardExpressionJson() != null && !command.guardExpressionJson().isBlank())
                ? GuardExpression.of(command.guardExpressionJson())
                : null;

        WorkflowTransition transition = WorkflowTransition.create(
                TransitionKey.of(command.transitionKey()),
                command.name(),
                command.description(),
                StateKey.of(command.fromStateKey()),
                StateKey.of(command.toStateKey()),
                guard,
                command.displayOrder()
        );
        version.addTransition(transition);
        return versionPort.save(version);
    }

    @Override
    public WorkflowVersion updateTransition(UpdateTransitionCommand command) {
        WorkflowVersion version = getVersionOrThrow(command.versionId());
        GuardExpression guard = (command.guardExpressionJson() != null && !command.guardExpressionJson().isBlank())
                ? GuardExpression.of(command.guardExpressionJson())
                : null;

        version.updateTransition(
                TransitionKey.of(command.transitionKey()),
                command.name(),
                command.description(),
                StateKey.of(command.fromStateKey()),
                StateKey.of(command.toStateKey()),
                guard,
                command.displayOrder()
        );
        return versionPort.save(version);
    }

    @Override
    public WorkflowVersion removeTransition(RemoveTransitionCommand command) {
        WorkflowVersion version = getVersionOrThrow(command.versionId());
        version.removeTransition(TransitionKey.of(command.transitionKey()));
        return versionPort.save(version);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowVersion> getById(Long versionId) {
        return versionPort.findById(versionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowVersion> getByDefinitionIdAndVersionNumber(Long definitionId, VersionNumber versionNumber) {
        return versionPort.findByDefinitionIdAndVersionNumber(definitionId, versionNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowVersion> getAllByDefinitionId(Long definitionId) {
        return versionPort.findAllByDefinitionId(definitionId);
    }

    private WorkflowVersion getVersionOrThrow(Long id) {
        return versionPort.findById(id)
                .orElseThrow(() -> new DomainException("VERSION_NOT_FOUND",
                        "Workflow version not found with ID: " + id));
    }
}
