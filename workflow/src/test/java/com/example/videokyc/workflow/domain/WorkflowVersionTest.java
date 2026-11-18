package com.example.videokyc.workflow.domain;

import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.state.StateKey;
import com.example.videokyc.workflow.domain.state.StateType;
import com.example.videokyc.workflow.domain.state.WorkflowState;
import com.example.videokyc.workflow.domain.transition.TransitionKey;
import com.example.videokyc.workflow.domain.transition.WorkflowTransition;
import com.example.videokyc.workflow.domain.version.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowVersionTest {

    private final Instant now = Instant.parse("2026-11-17T12:00:00Z");
    private final ContextSchema validSchema = ContextSchema.of("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "riskScore": { "type": "integer" }
              }
            }
            """);

    @Test
    @DisplayName("Should successfully construct a DRAFT version and manage states and transitions")
    void shouldManageDraftVersion() {
        WorkflowVersion version = WorkflowVersion.createDraft(1L, VersionNumber.of(1), validSchema, "admin", now);

        assertThat(version.getStatus()).isEqualTo(WorkflowVersionStatus.DRAFT);
        assertThat(version.getVersionNumber().value()).isEqualTo(1);

        WorkflowState start = WorkflowState.create(StateKey.of("START"), "Start", "Initial state", StateType.INITIAL, 0, null);
        WorkflowState end = WorkflowState.create(StateKey.of("END"), "End", "Final state", StateType.FINAL, 1, null);
        version.addState(start);
        version.addState(end);

        WorkflowTransition t1 = WorkflowTransition.create(
                TransitionKey.of("COMPLETE"), "Complete", null, start.getStateKey(), end.getStateKey(), null, 0
        );
        version.addTransition(t1);

        assertThat(version.getStates()).hasSize(2);
        assertThat(version.getTransitions()).hasSize(1);
    }

    @Test
    @DisplayName("Should reject duplicate state keys in the same version")
    void shouldRejectDuplicateStateKeys() {
        WorkflowVersion version = WorkflowVersion.createDraft(1L, VersionNumber.of(1), validSchema, "admin", now);
        version.addState(WorkflowState.create(StateKey.of("START"), "Start", null, StateType.INITIAL, 0, null));

        assertThatThrownBy(() -> version.addState(
                WorkflowState.create(StateKey.of("START"), "Another Start", null, StateType.INTERMEDIATE, 1, null)
        )).isInstanceOf(DomainException.class).hasMessageContaining("State key already exists");
    }

    @Test
    @DisplayName("Should reject deleting state when it is referenced by a transition")
    void shouldRejectDeletingReferencedState() {
        WorkflowVersion version = WorkflowVersion.createDraft(1L, VersionNumber.of(1), validSchema, "admin", now);
        WorkflowState s1 = WorkflowState.create(StateKey.of("S1"), "S1", null, StateType.INITIAL, 0, null);
        WorkflowState s2 = WorkflowState.create(StateKey.of("S2"), "S2", null, StateType.FINAL, 1, null);
        version.addState(s1);
        version.addState(s2);
        version.addTransition(WorkflowTransition.create(TransitionKey.of("T1"), "T1", null, s1.getStateKey(), s2.getStateKey(), null, 0));

        assertThatThrownBy(() -> version.removeState(StateKey.of("S1")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot remove state S1 because transition T1 references it");
    }

    @Test
    @DisplayName("Should reject transition originating from FINAL state")
    void shouldRejectTransitionFromFinalState() {
        WorkflowVersion version = WorkflowVersion.createDraft(1L, VersionNumber.of(1), validSchema, "admin", now);
        WorkflowState s1 = WorkflowState.create(StateKey.of("S1"), "S1", null, StateType.INITIAL, 0, null);
        WorkflowState finalState = WorkflowState.create(StateKey.of("DONE"), "Done", null, StateType.FINAL, 1, null);
        version.addState(s1);
        version.addState(finalState);

        assertThatThrownBy(() -> version.addTransition(
                WorkflowTransition.create(TransitionKey.of("INVALID"), "Invalid", null, finalState.getStateKey(), s1.getStateKey(), null, 0)
        )).isInstanceOf(DomainException.class).hasMessageContaining("Cannot create transition originating from FINAL state");
    }

    @Test
    @DisplayName("Should validate publication requirements (exactly 1 INITIAL, at least 1 FINAL)")
    void shouldValidatePublicationRequirements() {
        WorkflowVersion version = WorkflowVersion.createDraft(1L, VersionNumber.of(1), validSchema, "admin", now);

        // 0 INITIAL, 0 FINAL
        assertThatThrownBy(() -> version.requestPublication(WorkflowKey.of("TEST")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("exactly one INITIAL state, but found: 0");

        // 1 INITIAL, 0 FINAL
        WorkflowState init = WorkflowState.create(StateKey.of("INIT"), "Init", null, StateType.INITIAL, 0, null);
        version.addState(init);
        assertThatThrownBy(() -> version.requestPublication(WorkflowKey.of("TEST")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("at least one FINAL state");

        // 2 INITIAL states
        WorkflowState init2 = WorkflowState.create(StateKey.of("INIT2"), "Init2", null, StateType.INITIAL, 1, null);
        version.addState(init2);
        assertThatThrownBy(() -> version.requestPublication(WorkflowKey.of("TEST")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("exactly one INITIAL state, but found: 2");
    }

    @Test
    @DisplayName("Should build snapshot, compute checksum, and transition to PUBLISH_PENDING and PUBLISHED")
    void shouldTransitionPublicationLifecycle() {
        WorkflowVersion version = WorkflowVersion.createDraft(1L, VersionNumber.of(1), validSchema, "admin", now);
        WorkflowState init = WorkflowState.create(StateKey.of("INIT"), "Init", null, StateType.INITIAL, 0, null);
        WorkflowState done = WorkflowState.create(StateKey.of("DONE"), "Done", null, StateType.FINAL, 1, null);
        version.addState(init);
        version.addState(done);
        version.addTransition(WorkflowTransition.create(TransitionKey.of("PROCEED"), "Proceed", null, init.getStateKey(), done.getStateKey(), null, 0));

        version.requestPublication(WorkflowKey.of("TEST_FLOW"));

        assertThat(version.getStatus()).isEqualTo(WorkflowVersionStatus.PUBLISH_PENDING);
        assertThat(version.getSnapshotJson()).isNotNull().contains("TEST_FLOW");
        assertThat(version.getChecksum()).isNotNull();
        assertThat(version.getChecksum().value()).hasSize(64);

        // Cannot edit while PUBLISH_PENDING
        assertThatThrownBy(() -> version.addState(WorkflowState.create(StateKey.of("EXTRA"), "Extra", null, StateType.INTERMEDIATE, 2, null)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("only DRAFT versions are mutable");

        version.markPublished(now.plusSeconds(10));
        assertThat(version.getStatus()).isEqualTo(WorkflowVersionStatus.PUBLISHED);
        assertThat(version.getPublishedAt()).isEqualTo(now.plusSeconds(10));
    }

    @Test
    @DisplayName("Should support retry after publication failure without content mutation")
    void shouldSupportRetryAfterFailure() {
        WorkflowVersion version = WorkflowVersion.createDraft(1L, VersionNumber.of(1), validSchema, "admin", now);
        version.addState(WorkflowState.create(StateKey.of("INIT"), "Init", null, StateType.INITIAL, 0, null));
        version.addState(WorkflowState.create(StateKey.of("DONE"), "Done", null, StateType.FINAL, 1, null));
        version.requestPublication(WorkflowKey.of("FLOW"));

        version.markPublicationFailed();
        assertThat(version.getStatus()).isEqualTo(WorkflowVersionStatus.PUBLICATION_FAILED);

        // Cannot add states to failed version
        assertThatThrownBy(() -> version.addState(WorkflowState.create(StateKey.of("FAIL_STATE"), "Fail", null, StateType.INTERMEDIATE, 2, null)))
                .isInstanceOf(DomainException.class);

        // Retry returns to PUBLISH_PENDING
        version.retryPublication();
        assertThat(version.getStatus()).isEqualTo(WorkflowVersionStatus.PUBLISH_PENDING);
    }

    @Test
    @DisplayName("Should prevent external mutation of states and transitions via defensive copies")
    void shouldPreventExternalMutationOfStatesAndTransitionsViaDefensiveCopies() {
        WorkflowVersion version = WorkflowVersion.createDraft(1L, VersionNumber.of(1), validSchema, "admin", now);
        WorkflowState init = WorkflowState.create(StateKey.of("INIT"), "Initial State", null, StateType.INITIAL, 0, null);
        WorkflowState done = WorkflowState.create(StateKey.of("DONE"), "Done State", null, StateType.FINAL, 1, null);
        version.addState(init);
        version.addState(done);

        WorkflowTransition transition = WorkflowTransition.create(
                TransitionKey.of("FINISH"), "Finish Flow", null,
                init.getStateKey(), done.getStateKey(), null, 0
        );
        version.addTransition(transition);

        // Mutating a state returned by getStateOrThrow must not mutate the internal aggregate state
        WorkflowState externalState = version.getStateOrThrow(StateKey.of("INIT"));
        externalState.update("Hacked Name", "Hacked Description", StateType.INTERMEDIATE, 99, "{}");
        assertThat(version.getStateOrThrow(StateKey.of("INIT")).getName()).isEqualTo("Initial State");
        assertThat(version.getStateOrThrow(StateKey.of("INIT")).getStateType()).isEqualTo(StateType.INITIAL);

        // Mutating a state obtained from getStates() must not mutate the internal aggregate state
        WorkflowState stateFromMap = version.getStates().get(StateKey.of("DONE"));
        stateFromMap.update("Changed in Map", null, StateType.INTERMEDIATE, 50, null);
        assertThat(version.getStateOrThrow(StateKey.of("DONE")).getName()).isEqualTo("Done State");
        assertThat(version.getStateOrThrow(StateKey.of("DONE")).getStateType()).isEqualTo(StateType.FINAL);

        // Mutating a transition returned by getTransitionOrThrow must not mutate the internal aggregate state
        WorkflowTransition externalTransition = version.getTransitionOrThrow(TransitionKey.of("FINISH"));
        externalTransition.update("Mutated Transition", "Desc", init.getStateKey(), done.getStateKey(), null, 10);
        assertThat(version.getTransitionOrThrow(TransitionKey.of("FINISH")).getName()).isEqualTo("Finish Flow");
        assertThat(version.getTransitionOrThrow(TransitionKey.of("FINISH")).getDisplayOrder()).isEqualTo(0);

        // Mutating a transition obtained from getTransitions() must not mutate the internal aggregate state
        WorkflowTransition transitionFromMap = version.getTransitions().get(TransitionKey.of("FINISH"));
        transitionFromMap.update("Changed via Map", null, init.getStateKey(), done.getStateKey(), null, 25);
        assertThat(version.getTransitionOrThrow(TransitionKey.of("FINISH")).getName()).isEqualTo("Finish Flow");
    }

    @Test
    @DisplayName("Should produce identical canonical snapshot and checksum regardless of JSON formatting and insertion order")
    void shouldProduceIdenticalCanonicalSnapshotAndChecksumRegardlessOfFormattingAndOrdering() {
        ContextSchema formattedSchema1 = ContextSchema.of("{\n  \"type\": \"object\",\n  \"properties\": {\n    \"applicant\": {\"type\": \"string\"}\n  }\n}");
        ContextSchema formattedSchema2 = ContextSchema.of("{\"properties\":{\"applicant\":{\"type\":\"string\"}},\"type\":\"object\"}");

        String guardJson1 = "{\n  \"operator\": \"EQ\",\n  \"path\": \"applicant.status\",\n  \"value\": \"VERIFIED\"\n}";
        String guardJson2 = "{\"value\":\"VERIFIED\",\"path\":\"applicant.status\",\"operator\":\"EQ\"}";

        // Version 1: Add S_B then S_A with displayOrder 0 (different insertion order, tie-breaking on key)
        WorkflowVersion v1 = WorkflowVersion.createDraft(1L, VersionNumber.of(1), formattedSchema1, "admin", now);
        v1.addState(WorkflowState.create(StateKey.of("INIT"), "Init", null, StateType.INITIAL, 0, null));
        v1.addState(WorkflowState.create(StateKey.of("S_B"), "State B", null, StateType.INTERMEDIATE, 1, "{\n  \"step\": 2\n}"));
        v1.addState(WorkflowState.create(StateKey.of("S_A"), "State A", null, StateType.INTERMEDIATE, 1, "{\"step\":2}"));
        v1.addState(WorkflowState.create(StateKey.of("DONE"), "Done", null, StateType.FINAL, 2, null));

        v1.addTransition(WorkflowTransition.create(
                TransitionKey.of("T_B"), "TB", null, StateKey.of("INIT"), StateKey.of("S_B"),
                com.example.videokyc.workflow.domain.transition.guard.GuardExpression.of(guardJson1), 0
        ));
        v1.addTransition(WorkflowTransition.create(
                TransitionKey.of("T_A"), "TA", null, StateKey.of("INIT"), StateKey.of("S_A"),
                com.example.videokyc.workflow.domain.transition.guard.GuardExpression.of(guardJson2), 0
        ));
        v1.addTransition(WorkflowTransition.create(
                TransitionKey.of("T_END"), "TE", null, StateKey.of("S_A"), StateKey.of("DONE"), null, 1
        ));

        // Version 2: Add S_A then S_B with reverse insertion order, alternative JSON formatting
        WorkflowVersion v2 = WorkflowVersion.createDraft(1L, VersionNumber.of(1), formattedSchema2, "admin", now);
        v2.addState(WorkflowState.create(StateKey.of("DONE"), "Done", null, StateType.FINAL, 2, null));
        v2.addState(WorkflowState.create(StateKey.of("S_A"), "State A", null, StateType.INTERMEDIATE, 1, "{\n  \"step\": 2\n}"));
        v2.addState(WorkflowState.create(StateKey.of("S_B"), "State B", null, StateType.INTERMEDIATE, 1, "{\"step\": 2}"));
        v2.addState(WorkflowState.create(StateKey.of("INIT"), "Init", null, StateType.INITIAL, 0, null));

        v2.addTransition(WorkflowTransition.create(
                TransitionKey.of("T_END"), "TE", null, StateKey.of("S_A"), StateKey.of("DONE"), null, 1
        ));
        v2.addTransition(WorkflowTransition.create(
                TransitionKey.of("T_A"), "TA", null, StateKey.of("INIT"), StateKey.of("S_A"),
                com.example.videokyc.workflow.domain.transition.guard.GuardExpression.of(guardJson1), 0
        ));
        v2.addTransition(WorkflowTransition.create(
                TransitionKey.of("T_B"), "TB", null, StateKey.of("INIT"), StateKey.of("S_B"),
                com.example.videokyc.workflow.domain.transition.guard.GuardExpression.of(guardJson2), 0
        ));

        v1.requestPublication(WorkflowKey.of("TEST_CANONICAL"));
        v2.requestPublication(WorkflowKey.of("TEST_CANONICAL"));

        assertThat(v1.getSnapshotJson()).isEqualTo(v2.getSnapshotJson());
        assertThat(v1.getChecksum()).isEqualTo(v2.getChecksum());
    }
}
