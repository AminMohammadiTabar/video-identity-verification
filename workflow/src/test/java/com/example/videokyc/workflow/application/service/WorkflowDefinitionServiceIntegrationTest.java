package com.example.videokyc.workflow.application.service;

import com.example.videokyc.workflow.application.port.in.CreateWorkflowDefinitionUseCase.CreateDefinitionCommand;
import com.example.videokyc.workflow.application.port.in.UpdateWorkflowDefinitionUseCase.UpdateDefinitionCommand;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinitionStatus;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("h2")
class WorkflowDefinitionServiceIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:workflow_def_svc_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private WorkflowDefinitionService service;

    @Test
    @DisplayName("Should execute definition management lifecycle end-to-end via service")
    void shouldExecuteLifecycleEndToEnd() {
        // 1. Create definition
        CreateDefinitionCommand createCmd = new CreateDefinitionCommand(
                "SVC_INTEG_FLOW",
                "Service Integration Flow",
                "Testing service lifecycle",
                "admin-user"
        );
        WorkflowDefinition created = service.createDefinition(createCmd);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(WorkflowDefinitionStatus.ACTIVE);

        Long defId = created.getId();

        // 2. Query definition by ID and key
        Optional<WorkflowDefinition> byId = service.getById(defId);
        assertThat(byId).isPresent();
        assertThat(byId.get().getName()).isEqualTo("Service Integration Flow");

        Optional<WorkflowDefinition> byKey = service.getByKey(WorkflowKey.of("SVC_INTEG_FLOW"));
        assertThat(byKey).isPresent();
        assertThat(byKey.get().getId()).isEqualTo(defId);

        // 3. Update metadata
        UpdateDefinitionCommand updateCmd = new UpdateDefinitionCommand(
                defId,
                "Updated Service Flow",
                "Updated description",
                "editor-user"
        );
        WorkflowDefinition updated = service.updateMetadata(updateCmd);
        assertThat(updated.getName()).isEqualTo("Updated Service Flow");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getUpdatedBy()).isEqualTo("editor-user");

        // 4. Change status to DISABLED then re-enable then ARCHIVE
        WorkflowDefinition disabled = service.disable(defId, "operator-1");
        assertThat(disabled.getStatus()).isEqualTo(WorkflowDefinitionStatus.DISABLED);

        WorkflowDefinition enabled = service.enable(defId, "operator-2");
        assertThat(enabled.getStatus()).isEqualTo(WorkflowDefinitionStatus.ACTIVE);

        WorkflowDefinition archived = service.archive(defId, "admin-user");
        assertThat(archived.getStatus()).isEqualTo(WorkflowDefinitionStatus.ARCHIVED);

        // Verify state persisted in DB
        WorkflowDefinition persisted = service.getById(defId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(WorkflowDefinitionStatus.ARCHIVED);
        assertThat(persisted.getName()).isEqualTo("Updated Service Flow");

        // 5. Query all definitions
        List<WorkflowDefinition> all = service.getAll();
        assertThat(all).extracting(WorkflowDefinition::getId).contains(defId);
    }
}
