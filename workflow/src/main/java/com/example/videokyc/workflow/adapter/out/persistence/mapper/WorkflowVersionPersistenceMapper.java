package com.example.videokyc.workflow.adapter.out.persistence.mapper;

import com.example.videokyc.workflow.adapter.out.persistence.entity.WorkflowStateEntity;
import com.example.videokyc.workflow.adapter.out.persistence.entity.WorkflowTransitionEntity;
import com.example.videokyc.workflow.adapter.out.persistence.entity.WorkflowVersionEntity;
import com.example.videokyc.workflow.domain.state.StateKey;
import com.example.videokyc.workflow.domain.state.StateType;
import com.example.videokyc.workflow.domain.state.WorkflowState;
import com.example.videokyc.workflow.domain.transition.TransitionKey;
import com.example.videokyc.workflow.domain.transition.WorkflowTransition;
import com.example.videokyc.workflow.domain.transition.guard.GuardExpression;
import com.example.videokyc.workflow.domain.version.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Mapper between {@link WorkflowVersion} aggregate root (including its states and transitions)
 * and its relational persistence entities.
 */
@Component
public class WorkflowVersionPersistenceMapper {

    public WorkflowVersion toDomain(WorkflowVersionEntity versionEntity,
                                    List<WorkflowStateEntity> stateEntities,
                                    List<WorkflowTransitionEntity> transitionEntities) {
        if (versionEntity == null) {
            return null;
        }

        // 1. Map states
        Map<Long, StateKey> stateIdToKeyMap = new HashMap<>();
        List<WorkflowState> domainStates = new ArrayList<>();
        if (stateEntities != null) {
            for (WorkflowStateEntity s : stateEntities) {
                StateKey stateKey = StateKey.of(s.getStateKey());
                stateIdToKeyMap.put(s.getId(), stateKey);
                WorkflowState state = new WorkflowState(
                        s.getId(),
                        s.getVersionId(),
                        stateKey,
                        s.getName(),
                        s.getDescription(),
                        StateType.valueOf(s.getStateType()),
                        s.getDisplayOrder(),
                        s.getUiMetadataJson()
                );
                domainStates.add(state);
            }
        }

        // 2. Map transitions
        List<WorkflowTransition> domainTransitions = new ArrayList<>();
        if (transitionEntities != null) {
            for (WorkflowTransitionEntity t : transitionEntities) {
                StateKey fromKey = stateIdToKeyMap.get(t.getFromStateId());
                StateKey toKey = stateIdToKeyMap.get(t.getToStateId());

                GuardExpression guard = t.getGuardExpressionJson() != null
                        ? GuardExpression.of(t.getGuardExpressionJson())
                        : null;

                WorkflowTransition transition = new WorkflowTransition(
                        t.getId(),
                        t.getVersionId(),
                        TransitionKey.of(t.getTransitionKey()),
                        t.getName(),
                        t.getDescription(),
                        t.getFromStateId(),
                        t.getToStateId(),
                        fromKey,
                        toKey,
                        guard,
                        t.getGuardSchemaVersion(),
                        t.getDisplayOrder()
                );
                domainTransitions.add(transition);
            }
        }

        // 3. Reconstitute version aggregate
        return WorkflowVersion.reconstitute(
                versionEntity.getId(),
                versionEntity.getDefinitionId(),
                VersionNumber.of(versionEntity.getVersionNumber()),
                WorkflowVersionStatus.valueOf(versionEntity.getStatus()),
                ContextSchema.of(versionEntity.getContextSchemaJson()),
                versionEntity.getSnapshotJson(),
                versionEntity.getChecksum() != null ? SnapshotChecksum.of(versionEntity.getChecksum()) : null,
                versionEntity.getSnapshotSchemaVersion(),
                versionEntity.getCreatedAt(),
                versionEntity.getCreatedBy(),
                versionEntity.getPublishedAt(),
                versionEntity.getRowVersion(),
                domainStates,
                domainTransitions
        );
    }

    public WorkflowVersionEntity toVersionEntity(WorkflowVersion domain) {
        if (domain == null) {
            return null;
        }
        WorkflowVersionEntity entity = new WorkflowVersionEntity(
                domain.getDefinitionId(),
                domain.getVersionNumber().value(),
                domain.getStatus().name(),
                domain.getContextSchema().schemaJson(),
                domain.getSnapshotJson(),
                domain.getChecksum() != null ? domain.getChecksum().value() : null,
                (short) domain.getSnapshotSchemaVersion(),
                domain.getCreatedAt(),
                domain.getCreatedBy(),
                domain.getPublishedAt()
        );
        entity.setId(domain.getId());
        entity.setRowVersion(domain.getRowVersion());
        return entity;
    }

    public WorkflowStateEntity toStateEntity(WorkflowState state, Long versionId) {
        if (state == null) {
            return null;
        }
        WorkflowStateEntity entity = new WorkflowStateEntity(
                versionId,
                state.getStateKey().value(),
                state.getName(),
                state.getDescription(),
                state.getStateType().name(),
                state.getDisplayOrder(),
                state.getUiMetadata()
        );
        entity.setId(state.getId());
        return entity;
    }

    public WorkflowTransitionEntity toTransitionEntity(WorkflowTransition transition,
                                                       Long versionId,
                                                       Long fromStateId,
                                                       Long toStateId) {
        if (transition == null) {
            return null;
        }
        String guardJson = transition.getGuardExpression() != null
                ? transition.getGuardExpression().rawJson()
                : null;

        WorkflowTransitionEntity entity = new WorkflowTransitionEntity(
                versionId,
                transition.getTransitionKey().value(),
                transition.getName(),
                transition.getDescription(),
                fromStateId,
                toStateId,
                guardJson,
                (short) transition.getGuardSchemaVersion(),
                transition.getDisplayOrder()
        );
        entity.setId(transition.getId());
        return entity;
    }
}
