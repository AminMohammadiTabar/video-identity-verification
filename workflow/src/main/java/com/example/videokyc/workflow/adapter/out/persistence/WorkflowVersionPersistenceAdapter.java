package com.example.videokyc.workflow.adapter.out.persistence;

import com.example.videokyc.workflow.adapter.out.persistence.entity.*;
import com.example.videokyc.workflow.adapter.out.persistence.repository.*;

import com.example.videokyc.workflow.adapter.out.persistence.mapper.WorkflowVersionPersistenceMapper;
import com.example.videokyc.workflow.application.port.out.WorkflowVersionPort;
import com.example.videokyc.workflow.domain.state.StateKey;
import com.example.videokyc.workflow.domain.state.WorkflowState;
import com.example.videokyc.workflow.domain.transition.TransitionKey;
import com.example.videokyc.workflow.domain.transition.WorkflowTransition;
import com.example.videokyc.workflow.domain.version.VersionNumber;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Persistence adapter implementing {@link WorkflowVersionPort} using Spring Data JPA.
 * Coordinates persistence of the version aggregate along with its states and transitions.
 */
@Component
public class WorkflowVersionPersistenceAdapter implements WorkflowVersionPort {

    private final WorkflowVersionRepository versionRepository;
    private final WorkflowStateRepository stateRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final WorkflowVersionPersistenceMapper mapper;

    public WorkflowVersionPersistenceAdapter(WorkflowVersionRepository versionRepository,
                                             WorkflowStateRepository stateRepository,
                                             WorkflowTransitionRepository transitionRepository,
                                             WorkflowVersionPersistenceMapper mapper) {
        this.versionRepository = versionRepository;
        this.stateRepository = stateRepository;
        this.transitionRepository = transitionRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public WorkflowVersion save(WorkflowVersion version) {
        // 1. Save version entity
        WorkflowVersionEntity versionEntity = mapper.toVersionEntity(version);
        WorkflowVersionEntity savedVersion = versionRepository.save(versionEntity);
        Long versionId = savedVersion.getId();
        version.setId(versionId);

        // 2. Synchronize states
        // 2. Delete removed transitions FIRST to prevent Foreign Key violations against states
        List<WorkflowTransitionEntity> existingTransitions = transitionRepository.findByVersionIdOrderByDisplayOrderAsc(versionId);
        Set<String> activeTransitionKeys = new HashSet<>();
        for (WorkflowTransition transition : version.getTransitions().values()) {
            activeTransitionKeys.add(transition.getTransitionKey().value());
        }

        for (WorkflowTransitionEntity existing : existingTransitions) {
            if (!activeTransitionKeys.contains(existing.getTransitionKey())) {
                transitionRepository.delete(existing);
            }
        }
        transitionRepository.flush();

        // 3. Synchronize states (upsert active states, then delete removed states)
        List<WorkflowStateEntity> existingStates = stateRepository.findByVersionIdOrderByDisplayOrderAsc(versionId);
        Map<String, WorkflowStateEntity> existingStateMap = new HashMap<>();
        for (WorkflowStateEntity s : existingStates) {
            existingStateMap.put(s.getStateKey(), s);
        }

        Map<StateKey, Long> stateIdMap = new HashMap<>();
        Set<String> activeStateKeys = new HashSet<>();

        for (WorkflowState state : version.getStates().values()) {
            activeStateKeys.add(state.getStateKey().value());
            WorkflowStateEntity stateEntity = existingStateMap.get(state.getStateKey().value());
            if (stateEntity == null) {
                stateEntity = mapper.toStateEntity(state, versionId);
            } else {
                stateEntity.setName(state.getName());
                stateEntity.setDescription(state.getDescription());
                stateEntity.setStateType(state.getStateType().name());
                stateEntity.setDisplayOrder(state.getDisplayOrder());
                stateEntity.setUiMetadataJson(state.getUiMetadata());
            }
            WorkflowStateEntity savedState = stateRepository.save(stateEntity);
            state.setId(savedState.getId());
            state.setVersionId(versionId);
            stateIdMap.put(state.getStateKey(), savedState.getId());
        }
        stateRepository.flush();

        // Delete states that were removed (safe now because referencing transitions were deleted first)
        for (WorkflowStateEntity existing : existingStates) {
            if (!activeStateKeys.contains(existing.getStateKey())) {
                stateRepository.delete(existing);
            }
        }
        stateRepository.flush();

        // 4. Synchronize active transitions
        Map<String, WorkflowTransitionEntity> existingTransitionMap = new HashMap<>();
        for (WorkflowTransitionEntity t : existingTransitions) {
            if (activeTransitionKeys.contains(t.getTransitionKey())) {
                existingTransitionMap.put(t.getTransitionKey(), t);
            }
        }

        for (WorkflowTransition transition : version.getTransitions().values()) {
            Long fromStateId = stateIdMap.get(transition.getFromStateKey());
            Long toStateId = stateIdMap.get(transition.getToStateKey());

            WorkflowTransitionEntity transitionEntity = existingTransitionMap.get(transition.getTransitionKey().value());
            String guardJson = transition.getGuardExpression() != null
                    ? transition.getGuardExpression().rawJson()
                    : null;

            if (transitionEntity == null) {
                transitionEntity = mapper.toTransitionEntity(transition, versionId, fromStateId, toStateId);
            } else {
                transitionEntity.setName(transition.getName());
                transitionEntity.setDescription(transition.getDescription());
                transitionEntity.setFromStateId(fromStateId);
                transitionEntity.setToStateId(toStateId);
                transitionEntity.setGuardExpressionJson(guardJson);
                transitionEntity.setGuardSchemaVersion((short) transition.getGuardSchemaVersion());
                transitionEntity.setDisplayOrder(transition.getDisplayOrder());
            }
            WorkflowTransitionEntity savedTransition = transitionRepository.save(transitionEntity);
            transition.setId(savedTransition.getId());
            transition.setVersionId(versionId);
            transition.setFromStateId(fromStateId);
            transition.setToStateId(toStateId);
        }
        transitionRepository.flush();

        // 4. Return reconstituted aggregate
        List<WorkflowStateEntity> finalStates = stateRepository.findByVersionIdOrderByDisplayOrderAsc(versionId);
        List<WorkflowTransitionEntity> finalTransitions = transitionRepository.findByVersionIdOrderByDisplayOrderAsc(versionId);
        return mapper.toDomain(savedVersion, finalStates, finalTransitions);
    }

    @Override
    public Optional<WorkflowVersion> findById(Long id) {
        return versionRepository.findById(id).map(versionEntity -> {
            List<WorkflowStateEntity> states = stateRepository.findByVersionIdOrderByDisplayOrderAsc(id);
            List<WorkflowTransitionEntity> transitions = transitionRepository.findByVersionIdOrderByDisplayOrderAsc(id);
            return mapper.toDomain(versionEntity, states, transitions);
        });
    }

    @Override
    public Optional<WorkflowVersion> findByDefinitionIdAndVersionNumber(Long definitionId, VersionNumber versionNumber) {
        return versionRepository.findByDefinitionIdAndVersionNumber(definitionId, versionNumber.value())
                .map(versionEntity -> {
                    List<WorkflowStateEntity> states = stateRepository.findByVersionIdOrderByDisplayOrderAsc(versionEntity.getId());
                    List<WorkflowTransitionEntity> transitions = transitionRepository.findByVersionIdOrderByDisplayOrderAsc(versionEntity.getId());
                    return mapper.toDomain(versionEntity, states, transitions);
                });
    }

    @Override
    public Optional<Integer> findMaxVersionNumberByDefinitionId(Long definitionId) {
        return versionRepository.findFirstByDefinitionIdOrderByVersionNumberDesc(definitionId)
                .map(WorkflowVersionEntity::getVersionNumber);
    }

    @Override
    public List<WorkflowVersion> findAllByDefinitionId(Long definitionId) {
        List<WorkflowVersionEntity> entities = versionRepository.findByDefinitionIdOrderByVersionNumberDesc(definitionId);
        return entities.stream().map(v -> {
            List<WorkflowStateEntity> states = stateRepository.findByVersionIdOrderByDisplayOrderAsc(v.getId());
            List<WorkflowTransitionEntity> transitions = transitionRepository.findByVersionIdOrderByDisplayOrderAsc(v.getId());
            return mapper.toDomain(v, states, transitions);
        }).toList();
    }
}
