package com.example.videokyc.workflow.adapter.out.persistence;

import com.example.videokyc.workflow.application.port.out.PublicationJobPort;
import com.example.videokyc.workflow.application.port.out.WorkflowDefinitionPort;
import com.example.videokyc.workflow.application.port.out.WorkflowVersionPort;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import com.example.videokyc.workflow.domain.publication.PublicationJob;
import com.example.videokyc.workflow.domain.publication.PublicationJobStatus;
import com.example.videokyc.workflow.domain.state.StateKey;
import com.example.videokyc.workflow.domain.state.StateType;
import com.example.videokyc.workflow.domain.state.WorkflowState;
import com.example.videokyc.workflow.domain.transition.TransitionKey;
import com.example.videokyc.workflow.domain.transition.WorkflowTransition;
import com.example.videokyc.workflow.domain.transition.guard.GuardExpression;
import com.example.videokyc.workflow.domain.version.ContextSchema;
import com.example.videokyc.workflow.domain.version.VersionNumber;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
class WorkflowPersistenceAdapterIntegrationTest {

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:workflow_adapter_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private WorkflowDefinitionPort definitionPort;

    @Autowired
    private WorkflowVersionPort versionPort;

    @Autowired
    private PublicationJobPort publicationJobPort;

    @Test
    @DisplayName("Should save, retrieve, and update WorkflowDefinition via persistence adapter")
    void shouldSaveAndRetrieveWorkflowDefinition() {
        Instant now = Instant.now();
        WorkflowKey key = WorkflowKey.of("ADAPTER_DEF_FLOW");
        WorkflowDefinition def = WorkflowDefinition.create(key, "Adapter Flow", "Test description", "admin", now);

        WorkflowDefinition saved = definitionPort.save(def);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getWorkflowKey()).isEqualTo(key);
        assertThat(saved.getName()).isEqualTo("Adapter Flow");

        // Retrieve by ID
        Optional<WorkflowDefinition> byId = definitionPort.findById(saved.getId());
        assertThat(byId).isPresent();
        assertThat(byId.get().getName()).isEqualTo("Adapter Flow");

        // Retrieve by key
        Optional<WorkflowDefinition> byKey = definitionPort.findByWorkflowKey(key);
        assertThat(byKey).isPresent();
        assertThat(byKey.get().getId()).isEqualTo(saved.getId());

        // Check existence
        assertThat(definitionPort.existsByWorkflowKey(key)).isTrue();
        assertThat(definitionPort.existsByWorkflowKey(WorkflowKey.of("NON_EXISTENT"))).isFalse();

        // Update metadata
        saved.updateMetadata("Updated Name", "Updated Desc", "editor", Instant.now());
        WorkflowDefinition updated = definitionPort.save(saved);
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getDescription()).isEqualTo("Updated Desc");
    }

    @Test
    @DisplayName("Should save, retrieve, and synchronize version graph (states & transitions) via persistence adapter")
    void shouldSaveAndRetrieveVersionGraph() {
        Instant now = Instant.now();
        WorkflowDefinition def = definitionPort.save(
                WorkflowDefinition.create(WorkflowKey.of("GRAPH_DEF"), "Graph Flow", "Desc", "admin", now)
        );

        WorkflowVersion draft = WorkflowVersion.createDraft(
                def.getId(),
                VersionNumber.of(1),
                ContextSchema.of("{\"type\":\"object\"}"),
                "admin",
                now
        );

        WorkflowState s1 = WorkflowState.create(StateKey.of("INIT"), "Initial", "Desc", StateType.INITIAL, 0, "{}");
        WorkflowState s2 = WorkflowState.create(StateKey.of("FINAL"), "Completed", "Desc", StateType.FINAL, 1, "{}");
        draft.addState(s1);
        draft.addState(s2);

        String guardJson = "{\"path\":\"data.verified\",\"operator\":\"EQ\",\"value\":true}";
        WorkflowTransition t1 = WorkflowTransition.create(
                TransitionKey.of("APPROVE"),
                "Approve",
                "Desc",
                StateKey.of("INIT"),
                StateKey.of("FINAL"),
                GuardExpression.of(guardJson),
                0
        );
        draft.addTransition(t1);

        // Save graph
        WorkflowVersion saved = versionPort.save(draft);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStates()).hasSize(2);
        assertThat(saved.getTransitions()).hasSize(1);

        WorkflowState savedS1 = saved.getStates().get(StateKey.of("INIT"));
        WorkflowState savedS2 = saved.getStates().get(StateKey.of("FINAL"));
        WorkflowTransition savedT1 = saved.getTransitions().get(TransitionKey.of("APPROVE"));

        assertThat(savedS1.getId()).isNotNull();
        assertThat(savedS2.getId()).isNotNull();
        assertThat(savedT1.getId()).isNotNull();
        assertThat(savedT1.getFromStateId()).isEqualTo(savedS1.getId());
        assertThat(savedT1.getToStateId()).isEqualTo(savedS2.getId());

        // Retrieve by ID
        Optional<WorkflowVersion> retrieved = versionPort.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStates()).hasSize(2);
        assertThat(retrieved.get().getTransitions()).hasSize(1);
        WorkflowTransition retrievedT1 = retrieved.get().getTransitions().get(TransitionKey.of("APPROVE"));
        assertThat(retrievedT1.getFromStateKey()).isEqualTo(StateKey.of("INIT"));
        assertThat(retrievedT1.getToStateKey()).isEqualTo(StateKey.of("FINAL"));
        assertThat(retrievedT1.getGuardExpression().rawJson()).isEqualTo(guardJson);

        // Retrieve by definition ID and version number
        Optional<WorkflowVersion> byNum = versionPort.findByDefinitionIdAndVersionNumber(def.getId(), VersionNumber.of(1));
        assertThat(byNum).isPresent();
        assertThat(byNum.get().getId()).isEqualTo(saved.getId());

        // Max version number
        Optional<Integer> maxVer = versionPort.findMaxVersionNumberByDefinitionId(def.getId());
        assertThat(maxVer).contains(1);

        // Modify graph: add intermediate state and transition
        WorkflowVersion loaded = retrieved.get();
        loaded.removeTransition(TransitionKey.of("APPROVE"));

        WorkflowState sInter = WorkflowState.create(StateKey.of("REVIEW"), "In Review", "Desc", StateType.INTERMEDIATE, 1, "{}");
        loaded.addState(sInter);

        WorkflowTransition tNew1 = WorkflowTransition.create(
                TransitionKey.of("TO_REVIEW"), "To Review", "Desc",
                StateKey.of("INIT"), StateKey.of("REVIEW"), null, 0
        );
        WorkflowTransition tNew2 = WorkflowTransition.create(
                TransitionKey.of("TO_FINAL"), "To Final", "Desc",
                StateKey.of("REVIEW"), StateKey.of("FINAL"), null, 1
        );
        loaded.addTransition(tNew1);
        loaded.addTransition(tNew2);

        WorkflowVersion updatedGraph = versionPort.save(loaded);
        assertThat(updatedGraph.getStates()).hasSize(3);
        assertThat(updatedGraph.getTransitions()).hasSize(2);
        assertThat(updatedGraph.getTransitions().containsKey(TransitionKey.of("APPROVE"))).isFalse();

        // Verify changes persisted cleanly
        WorkflowVersion reloaded = versionPort.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStates()).hasSize(3);
        assertThat(reloaded.getTransitions()).hasSize(2);
        assertThat(reloaded.getTransitions().get(TransitionKey.of("TO_REVIEW")).getToStateKey()).isEqualTo(StateKey.of("REVIEW"));
    }

    @Test
    @DisplayName("Should save, retrieve, and update PublicationJob via persistence adapter")
    void shouldSaveAndRetrievePublicationJob() {
        Instant now = Instant.now();
        WorkflowDefinition def = definitionPort.save(
                WorkflowDefinition.create(WorkflowKey.of("JOB_DEF"), "Job Flow", "Desc", "admin", now)
        );
        WorkflowVersion version = versionPort.save(
                WorkflowVersion.createDraft(def.getId(), VersionNumber.of(1), ContextSchema.of("{}"), "admin", now)
        );

        PublicationJob job = PublicationJob.create(version.getId(), 5, now);

        PublicationJob saved = publicationJobPort.save(job);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersionId()).isEqualTo(version.getId());
        assertThat(saved.getStatus()).isEqualTo(PublicationJobStatus.PENDING);

        Optional<PublicationJob> byId = publicationJobPort.findById(saved.getId());
        assertThat(byId).isPresent();
        assertThat(byId.get().getVersionId()).isEqualTo(version.getId());

        Optional<PublicationJob> byVersionId = publicationJobPort.findByVersionId(version.getId());
        assertThat(byVersionId).isPresent();
        assertThat(byVersionId.get().getId()).isEqualTo(saved.getId());
    }
}
