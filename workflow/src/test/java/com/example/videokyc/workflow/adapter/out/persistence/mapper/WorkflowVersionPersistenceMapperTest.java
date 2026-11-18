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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowVersionPersistenceMapperTest {

    private final WorkflowVersionPersistenceMapper mapper = new WorkflowVersionPersistenceMapper();

    @Test
    @DisplayName("Should map WorkflowVersion domain aggregate to entities and back with states and transitions")
    void shouldMapVersionWithStatesAndTransitions() {
        Instant now = Instant.parse("2026-11-18T10:00:00Z");
        SnapshotChecksum checksum = SnapshotChecksum.of("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        WorkflowState state1 = new WorkflowState(10L, 100L, StateKey.of("INIT"), "Initial State", "Desc", StateType.INITIAL, 0, "{}");
        WorkflowState state2 = new WorkflowState(20L, 100L, StateKey.of("DONE"), "Done State", "Desc", StateType.FINAL, 1, "{}");

        String guardJson = "{\"path\":\"user.verified\",\"operator\":\"EQ\",\"value\":true}";
        WorkflowTransition transition = new WorkflowTransition(
                50L, 100L, TransitionKey.of("SUBMIT"), "Submit Transition", "Desc",
                10L, 20L, StateKey.of("INIT"), StateKey.of("DONE"),
                GuardExpression.of(guardJson), 1, 0
        );

        WorkflowVersion domain = WorkflowVersion.reconstitute(
                100L,
                1L,
                VersionNumber.of(2),
                WorkflowVersionStatus.PUBLISHED,
                ContextSchema.of("{\"type\":\"object\"}"),
                "{\"version\":2}",
                checksum,
                1,
                now,
                "admin",
                now.plusSeconds(30),
                5L,
                List.of(state1, state2),
                List.of(transition)
        );

        // Convert domain -> entities
        WorkflowVersionEntity versionEntity = mapper.toVersionEntity(domain);
        assertThat(versionEntity.getId()).isEqualTo(100L);
        assertThat(versionEntity.getDefinitionId()).isEqualTo(1L);
        assertThat(versionEntity.getVersionNumber()).isEqualTo(2);
        assertThat(versionEntity.getStatus()).isEqualTo("PUBLISHED");
        assertThat(versionEntity.getContextSchemaJson()).isEqualTo("{\"type\":\"object\"}");
        assertThat(versionEntity.getSnapshotJson()).isEqualTo("{\"version\":2}");
        assertThat(versionEntity.getChecksum()).isEqualTo(checksum.value());
        assertThat(versionEntity.getCreatedAt()).isEqualTo(now);
        assertThat(versionEntity.getPublishedAt()).isEqualTo(now.plusSeconds(30));
        assertThat(versionEntity.getRowVersion()).isEqualTo(5L);

        WorkflowStateEntity s1Entity = mapper.toStateEntity(state1, 100L);
        WorkflowStateEntity s2Entity = mapper.toStateEntity(state2, 100L);
        WorkflowTransitionEntity tEntity = mapper.toTransitionEntity(transition, 100L, 10L, 20L);

        // Convert entities -> domain
        WorkflowVersion roundtripped = mapper.toDomain(versionEntity, List.of(s1Entity, s2Entity), List.of(tEntity));

        assertThat(roundtripped).isNotNull();
        assertThat(roundtripped.getId()).isEqualTo(100L);
        assertThat(roundtripped.getDefinitionId()).isEqualTo(1L);
        assertThat(roundtripped.getVersionNumber().value()).isEqualTo(2);
        assertThat(roundtripped.getStatus()).isEqualTo(WorkflowVersionStatus.PUBLISHED);
        assertThat(roundtripped.getChecksum()).isEqualTo(checksum);
        assertThat(roundtripped.getStates()).hasSize(2);
        assertThat(roundtripped.getStates().get(StateKey.of("INIT")).getName()).isEqualTo("Initial State");
        assertThat(roundtripped.getTransitions()).hasSize(1);
        WorkflowTransition rtTransition = roundtripped.getTransitions().get(TransitionKey.of("SUBMIT"));
        assertThat(rtTransition).isNotNull();
        assertThat(rtTransition.getFromStateKey()).isEqualTo(StateKey.of("INIT"));
        assertThat(rtTransition.getToStateKey()).isEqualTo(StateKey.of("DONE"));
        assertThat(rtTransition.getGuardExpression()).isNotNull();
        assertThat(rtTransition.getGuardExpression().rawJson()).isEqualTo(guardJson);
    }

    @Test
    @DisplayName("Should handle null entities gracefully")
    void shouldHandleNull() {
        assertThat(mapper.toDomain(null, null, null)).isNull();
        assertThat(mapper.toVersionEntity(null)).isNull();
        assertThat(mapper.toStateEntity(null, 1L)).isNull();
        assertThat(mapper.toTransitionEntity(null, 1L, 1L, 2L)).isNull();
    }
}
