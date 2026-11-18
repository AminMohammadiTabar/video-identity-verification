package com.example.videokyc.workflow.adapter.out.persistence.repository;

import com.example.videokyc.workflow.adapter.out.persistence.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("h2")
class WorkflowRepositoryIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () ->
                "jdbc:h2:mem:workflow_repo_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private WorkflowDefinitionRepository definitionRepository;

    @Autowired
    private WorkflowVersionRepository versionRepository;

    @Autowired
    private WorkflowStateRepository stateRepository;

    @Autowired
    private WorkflowTransitionRepository transitionRepository;

    @Autowired
    private PublicationJobRepository publicationJobRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("Should persist and retrieve WorkflowDefinition and verify optimistic locking with row_version")
    void shouldPersistDefinitionAndCheckOptimisticLocking() {
        Instant now = Instant.now();
        WorkflowDefinitionEntity def = new WorkflowDefinitionEntity(
                "DEF_TEST_LOCK", "Test Lock Flow", "Description", "ACTIVE",
                null, null, now, "admin", now, "admin"
        );
        WorkflowDefinitionEntity saved = definitionRepository.saveAndFlush(def);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRowVersion()).isEqualTo(0L);

        // Update entity and verify version increments
        saved.setName("Updated Lock Flow");
        WorkflowDefinitionEntity updated = definitionRepository.saveAndFlush(saved);
        assertThat(updated.getRowVersion()).isEqualTo(1L);

        // Simulate concurrent modification conflict
        WorkflowDefinitionEntity staleCopy = new WorkflowDefinitionEntity(
                updated.getWorkflowKey(), updated.getName(), updated.getDescription(),
                updated.getStatus(), updated.getActiveVersionId(), updated.getPendingVersionId(),
                updated.getCreatedAt(), updated.getCreatedBy(), updated.getUpdatedAt(), updated.getUpdatedBy()
        );
        staleCopy.setId(updated.getId());
        staleCopy.setRowVersion(0L); // Stale version

        assertThatThrownBy(() -> definitionRepository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Should query definition by workflowKey and existsByWorkflowKey")
    void shouldQueryDefinitionByKey() {
        Instant now = Instant.now();
        WorkflowDefinitionEntity def = new WorkflowDefinitionEntity(
                "DEF_QUERY_KEY", "Query Flow", null, "ACTIVE",
                null, null, now, "admin", now, "admin"
        );
        definitionRepository.saveAndFlush(def);

        Optional<WorkflowDefinitionEntity> found = definitionRepository.findByWorkflowKey("DEF_QUERY_KEY");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Query Flow");

        assertThat(definitionRepository.existsByWorkflowKey("DEF_QUERY_KEY")).isTrue();
        assertThat(definitionRepository.existsByWorkflowKey("NON_EXISTING")).isFalse();
    }

    @Test
    @DisplayName("Should persist and query WorkflowVersion, States, and Transitions")
    void shouldPersistAndQueryVersionHierarchy() {
        Instant now = Instant.now();
        WorkflowDefinitionEntity def = definitionRepository.saveAndFlush(new WorkflowDefinitionEntity(
                "DEF_HIERARCHY", "Hierarchy Flow", null, "ACTIVE",
                null, null, now, "admin", now, "admin"
        ));

        WorkflowVersionEntity version = versionRepository.saveAndFlush(new WorkflowVersionEntity(
                def.getId(), 1, "DRAFT", "{}", null, null, (short) 1,
                now, "admin", null
        ));
        assertThat(version.getId()).isNotNull();

        // Create States
        WorkflowStateEntity s1 = stateRepository.saveAndFlush(new WorkflowStateEntity(
                version.getId(), "INITIAL_STATE", "Initial", null, "INITIAL", 0, "{}"
        ));
        WorkflowStateEntity s2 = stateRepository.saveAndFlush(new WorkflowStateEntity(
                version.getId(), "FINAL_STATE", "Final", null, "FINAL", 1, "{}"
        ));

        // Create Transition
        WorkflowTransitionEntity tr = transitionRepository.saveAndFlush(new WorkflowTransitionEntity(
                version.getId(), "TR_1", "Transition 1", null,
                s1.getId(), s2.getId(), null, (short) 1, 0
        ));

        // Verify queries
        List<WorkflowStateEntity> states = stateRepository.findByVersionIdOrderByDisplayOrderAsc(version.getId());
        assertThat(states).hasSize(2);
        assertThat(states.get(0).getStateKey()).isEqualTo("INITIAL_STATE");
        assertThat(states.get(1).getStateKey()).isEqualTo("FINAL_STATE");

        List<WorkflowTransitionEntity> transitions =
                transitionRepository.findByVersionIdAndFromStateIdOrderByDisplayOrderAsc(version.getId(), s1.getId());
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).getTransitionKey()).isEqualTo("TR_1");
    }

    @Test
    @DisplayName("Should persist and query PublicationJob")
    void shouldPersistAndQueryPublicationJob() {
        Instant now = Instant.now();
        WorkflowDefinitionEntity def = definitionRepository.saveAndFlush(new WorkflowDefinitionEntity(
                "DEF_JOB_TEST", "Job Flow", null, "ACTIVE",
                null, null, now, "admin", now, "admin"
        ));

        WorkflowVersionEntity version = versionRepository.saveAndFlush(new WorkflowVersionEntity(
                def.getId(), 1, "PUBLISH_PENDING", "{}", null, null, (short) 1,
                now, "admin", null
        ));

        PublicationJobEntity job = publicationJobRepository.saveAndFlush(new PublicationJobEntity(
                version.getId(), "PENDING", 0, 5, now.minusSeconds(10),
                null, null, null, null,
                now, null, null, now
        ));
        assertThat(job.getId()).isNotNull();

        Optional<PublicationJobEntity> found = publicationJobRepository.findByVersionId(version.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("PENDING");

        List<PublicationJobEntity> dueJobs = publicationJobRepository
                .findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        List.of("PENDING", "RETRY_WAIT"), Instant.now()
                );
        assertThat(dueJobs).extracting(PublicationJobEntity::getVersionId).contains(version.getId());
    }

    @Test
    @DisplayName("Should persist and query OutboxEvent")
    void shouldPersistAndQueryOutboxEvent() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        OutboxEventEntity event = outboxEventRepository.saveAndFlush(new OutboxEventEntity(
                eventId, "WORKFLOW_DEFINITION", 999L, "workflow.definition.published",
                (short) 1, "workflow.definition.published", "{\"test\":true}", null,
                UUID.randomUUID(), UUID.randomUUID(), null,
                "PENDING", 0, now.minusSeconds(5), null, null,
                now, null, null, null
        ));
        assertThat(event.getId()).isNotNull();

        Optional<OutboxEventEntity> found = outboxEventRepository.findByEventId(eventId);
        assertThat(found).isPresent();
        assertThat(found.get().getAggregateType()).isEqualTo("WORKFLOW_DEFINITION");

        List<OutboxEventEntity> eventsByAgg = outboxEventRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("WORKFLOW_DEFINITION", 999L);
        assertThat(eventsByAgg).hasSize(1);
        assertThat(eventsByAgg.get(0).getEventId()).isEqualTo(eventId);
    }
}
