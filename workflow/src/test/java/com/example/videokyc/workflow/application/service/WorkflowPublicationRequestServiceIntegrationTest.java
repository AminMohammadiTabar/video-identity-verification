package com.example.videokyc.workflow.application.service;

import com.example.videokyc.workflow.application.port.in.CreateDraftVersionUseCase.CreateDraftVersionCommand;
import com.example.videokyc.workflow.application.port.in.CreateWorkflowDefinitionUseCase.CreateDefinitionCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowStatesUseCase.AddStateCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowTransitionsUseCase.AddTransitionCommand;
import com.example.videokyc.workflow.application.port.in.RequestPublicationUseCase.RequestPublicationCommand;
import com.example.videokyc.workflow.application.port.in.ValidateDraftVersionUseCase.ValidationReport;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.publication.PublicationJob;
import com.example.videokyc.workflow.domain.publication.PublicationJobStatus;
import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.state.StateType;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;
import com.example.videokyc.workflow.domain.version.WorkflowVersionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("h2")
class WorkflowPublicationRequestServiceIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:workflow_pub_req_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private WorkflowDefinitionService definitionService;

    @Autowired
    private WorkflowDraftService draftService;

    @Autowired
    private WorkflowPublicationRequestService publicationService;

    @Test
    @DisplayName("Should validate draft and request publication end-to-end with persistent snapshot and job")
    void shouldValidateAndRequestPublicationEndToEnd() {
        // 1. Create definition
        WorkflowDefinition def = definitionService.createDefinition(new CreateDefinitionCommand(
                "PUB_FLOW", "Publication Flow", "Desc", "admin"
        ));
        Long defId = def.getId();
        assertThat(defId).isNotNull();

        // 2. Create draft version
        WorkflowVersion draft = draftService.createDraft(new CreateDraftVersionCommand(
                defId, null, null, "admin"
        ));
        Long versionId = draft.getId();
        assertThat(versionId).isNotNull();

        // 3. Add states
        draftService.addState(new AddStateCommand(versionId, "INIT", "Initial Step", null, StateType.INITIAL, 0, null));
        draftService.addState(new AddStateCommand(versionId, "DONE", "Final Step", null, StateType.FINAL, 1, null));

        // 4. Add transition with Guard AST
        String guardJson = """
                {
                  "operator": "GTE",
                  "path": "applicant.age",
                  "value": 18
                }
                """;
        draftService.addTransition(new AddTransitionCommand(
                versionId, "CONFIRM", "Confirm Step", null, "INIT", "DONE", guardJson, 0
        ));

        // 5. Validate draft
        ValidationReport report = publicationService.validateDraft(versionId);
        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();

        // 6. Request publication
        PublicationJob job = publicationService.requestPublication(RequestPublicationCommand.of(versionId));
        assertThat(job.getId()).isNotNull();
        assertThat(job.getVersionId()).isEqualTo(versionId);
        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.PENDING);

        // 7. Verify version status and snapshot in database
        WorkflowVersion loadedVersion = draftService.getById(versionId).orElseThrow();
        assertThat(loadedVersion.getStatus()).isEqualTo(WorkflowVersionStatus.PUBLISH_PENDING);
        assertThat(loadedVersion.getSnapshotJson()).contains("PUB_FLOW");
        assertThat(loadedVersion.getSnapshotJson()).contains("INIT");
        assertThat(loadedVersion.getSnapshotJson()).contains("DONE");
        assertThat(loadedVersion.getChecksum()).isNotNull();
        assertThat(loadedVersion.getChecksum().value()).hasSize(64);

        // 8. Query job by version ID
        Optional<PublicationJob> loadedJob = publicationService.getByVersionId(versionId);
        assertThat(loadedJob).isPresent();
        assertThat(loadedJob.get().getId()).isEqualTo(job.getId());

        // 9. Verify duplicate publication request is blocked while job is in progress
        assertThatThrownBy(() -> publicationService.requestPublication(RequestPublicationCommand.of(versionId)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Publication job is already in progress");
    }
}
