package com.example.videokyc.workflow.application.service;

import com.example.videokyc.workflow.application.port.in.CreateDraftVersionUseCase.CreateDraftVersionCommand;
import com.example.videokyc.workflow.application.port.in.CreateWorkflowDefinitionUseCase.CreateDefinitionCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowStatesUseCase.AddStateCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowTransitionsUseCase.AddTransitionCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowTransitionsUseCase.RemoveTransitionCommand;
import com.example.videokyc.workflow.application.port.in.UpdateDraftContextSchemaUseCase.UpdateContextSchemaCommand;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.state.StateKey;
import com.example.videokyc.workflow.domain.state.StateType;
import com.example.videokyc.workflow.domain.transition.TransitionKey;
import com.example.videokyc.workflow.domain.version.VersionNumber;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;
import com.example.videokyc.workflow.domain.version.WorkflowVersionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
class WorkflowDraftServiceIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:workflow_draft_svc_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private WorkflowDefinitionService definitionService;

    @Autowired
    private WorkflowDraftService draftService;

    @Test
    @DisplayName("Should execute draft creation, graph mutations, cloning, and querying end-to-end")
    void shouldExecuteDraftGraphLifecycleEndToEnd() {
        // 1. Create Workflow Definition
        WorkflowDefinition def = definitionService.createDefinition(new CreateDefinitionCommand(
                "GRAPH_FLOW", "Graph Flow", "Testing graph mutations", "admin"
        ));
        Long defId = def.getId();
        assertThat(defId).isNotNull();

        // 2. Create Draft Version 1
        WorkflowVersion v1 = draftService.createDraft(new CreateDraftVersionCommand(
                defId, null, null, "admin"
        ));
        Long v1Id = v1.getId();
        assertThat(v1Id).isNotNull();
        assertThat(v1.getVersionNumber().value()).isEqualTo(1);
        assertThat(v1.getStatus()).isEqualTo(WorkflowVersionStatus.DRAFT);

        // 3. Add states to v1
        draftService.addState(new AddStateCommand(v1Id, "START", "Start State", "Initial", StateType.INITIAL, 0, null));
        draftService.addState(new AddStateCommand(v1Id, "END", "End State", "Final", StateType.FINAL, 1, null));

        // 4. Add transition with Guard AST to v1
        String guardJson = """
                {
                  "operator": "EQ",
                  "path": "risk.score",
                  "value": 10
                }
                """;
        draftService.addTransition(new AddTransitionCommand(
                v1Id, "TO_END", "Direct Complete", "From start to end", "START", "END", guardJson, 0
        ));

        // 5. Update context schema on v1
        String customSchema = "{\"type\":\"object\",\"properties\":{\"risk\":{\"type\":\"object\"}}}";
        draftService.updateContextSchema(new UpdateContextSchemaCommand(v1Id, customSchema));

        // 6. Verify v1 graph re-loaded from database
        Optional<WorkflowVersion> loadedV1 = draftService.getById(v1Id);
        assertThat(loadedV1).isPresent();
        assertThat(loadedV1.get().getStates()).hasSize(2);
        assertThat(loadedV1.get().getStates()).containsKey(StateKey.of("START"));
        assertThat(loadedV1.get().getStates()).containsKey(StateKey.of("END"));
        assertThat(loadedV1.get().getTransitions()).hasSize(1);
        assertThat(loadedV1.get().getTransitions()).containsKey(TransitionKey.of("TO_END"));
        assertThat(loadedV1.get().getContextSchema().schemaJson()).isEqualTo(customSchema);

        // 7. Create Draft Version 2 cloning from v1
        WorkflowVersion v2 = draftService.createDraft(new CreateDraftVersionCommand(
                defId, null, v1Id, "author2"
        ));
        Long v2Id = v2.getId();
        assertThat(v2Id).isNotNull();
        assertThat(v2.getVersionNumber().value()).isEqualTo(2);

        // Verify cloned graph in v2
        Optional<WorkflowVersion> loadedV2 = draftService.getById(v2Id);
        assertThat(loadedV2).isPresent();
        assertThat(loadedV2.get().getStates()).hasSize(2);
        assertThat(loadedV2.get().getTransitions()).hasSize(1);
        assertThat(loadedV2.get().getContextSchema().schemaJson()).isEqualTo(customSchema);

        // 8. Mutate v2 independently: add intermediate state and remove old transition
        draftService.addState(new AddStateCommand(v2Id, "INTERMEDIATE", "Review Step", "Manual", StateType.INTERMEDIATE, 1, null));
        draftService.removeTransition(new RemoveTransitionCommand(v2Id, "TO_END"));
        draftService.addTransition(new AddTransitionCommand(
                v2Id, "TO_REVIEW", "To Review", null, "START", "INTERMEDIATE", null, 0
        ));
        draftService.addTransition(new AddTransitionCommand(
                v2Id, "TO_FINAL", "To Final", null, "INTERMEDIATE", "END", null, 1
        ));

        // 9. Verify v2 has new topology while v1 remains intact
        WorkflowVersion v1After = draftService.getById(v1Id).orElseThrow();
        assertThat(v1After.getStates()).hasSize(2);
        assertThat(v1After.getTransitions()).hasSize(1);
        assertThat(v1After.getTransitions()).containsKey(TransitionKey.of("TO_END"));

        WorkflowVersion v2After = draftService.getById(v2Id).orElseThrow();
        assertThat(v2After.getStates()).hasSize(3);
        assertThat(v2After.getTransitions()).hasSize(2);
        assertThat(v2After.getTransitions()).containsKey(TransitionKey.of("TO_REVIEW"));
        assertThat(v2After.getTransitions()).containsKey(TransitionKey.of("TO_FINAL"));

        // 10. Query all versions for definition
        List<WorkflowVersion> allVersions = draftService.getAllByDefinitionId(defId);
        assertThat(allVersions).hasSize(2);
        assertThat(allVersions.get(0).getVersionNumber().value()).isEqualTo(2);
        assertThat(allVersions.get(1).getVersionNumber().value()).isEqualTo(1);

        // 11. Query by definition ID and version number
        Optional<WorkflowVersion> byNum = draftService.getByDefinitionIdAndVersionNumber(defId, VersionNumber.of(2));
        assertThat(byNum).isPresent();
        assertThat(byNum.get().getId()).isEqualTo(v2Id);
    }
}
