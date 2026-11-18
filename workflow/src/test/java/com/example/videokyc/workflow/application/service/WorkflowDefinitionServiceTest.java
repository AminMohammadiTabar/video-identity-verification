package com.example.videokyc.workflow.application.service;

import com.example.videokyc.workflow.application.port.in.CreateWorkflowDefinitionUseCase.CreateDefinitionCommand;
import com.example.videokyc.workflow.application.port.in.UpdateWorkflowDefinitionUseCase.UpdateDefinitionCommand;
import com.example.videokyc.workflow.application.port.out.WorkflowDefinitionPort;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinitionStatus;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import com.example.videokyc.workflow.domain.shared.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionServiceTest {

    @Mock
    private WorkflowDefinitionPort definitionPort;

    private final Instant fixedInstant = Instant.parse("2026-11-18T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    private WorkflowDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowDefinitionService(definitionPort, fixedClock);
    }

    @Test
    @DisplayName("Should create workflow definition successfully")
    void shouldCreateDefinition() {
        CreateDefinitionCommand cmd = new CreateDefinitionCommand("NEW_FLOW", "New Workflow", "A description", "admin");

        when(definitionPort.existsByWorkflowKey(WorkflowKey.of("NEW_FLOW"))).thenReturn(false);
        when(definitionPort.save(any(WorkflowDefinition.class))).thenAnswer(invocation -> {
            WorkflowDefinition def = invocation.getArgument(0);
            def.setId(10L);
            return def;
        });

        WorkflowDefinition created = service.createDefinition(cmd);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(10L);
        assertThat(created.getWorkflowKey().value()).isEqualTo("NEW_FLOW");
        assertThat(created.getName()).isEqualTo("New Workflow");
        assertThat(created.getStatus()).isEqualTo(WorkflowDefinitionStatus.ACTIVE);
        assertThat(created.getCreatedAt()).isEqualTo(fixedInstant);
        assertThat(created.getCreatedBy()).isEqualTo("admin");

        verify(definitionPort).save(any(WorkflowDefinition.class));
    }

    @Test
    @DisplayName("Should reject duplicate workflow key on creation")
    void shouldRejectDuplicateWorkflowKey() {
        CreateDefinitionCommand cmd = new CreateDefinitionCommand("DUP_FLOW", "Duplicate Workflow", null, "admin");

        when(definitionPort.existsByWorkflowKey(WorkflowKey.of("DUP_FLOW"))).thenReturn(true);

        assertThatThrownBy(() -> service.createDefinition(cmd))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already exists");

        verify(definitionPort, never()).save(any());
    }

    @Test
    @DisplayName("Should update definition metadata")
    void shouldUpdateMetadata() {
        WorkflowDefinition existing = WorkflowDefinition.create(
                WorkflowKey.of("FLOW_1"), "Old Name", "Old Desc", "admin", fixedInstant
        );
        existing.setId(1L);

        when(definitionPort.findById(1L)).thenReturn(Optional.of(existing));
        when(definitionPort.save(any(WorkflowDefinition.class))).thenAnswer(i -> i.getArgument(0));

        UpdateDefinitionCommand cmd = new UpdateDefinitionCommand(1L, "New Name", "New Desc", "editor");
        WorkflowDefinition updated = service.updateMetadata(cmd);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDescription()).isEqualTo("New Desc");
        assertThat(updated.getUpdatedBy()).isEqualTo("editor");
        assertThat(updated.getUpdatedAt()).isEqualTo(fixedInstant);
    }

    @Test
    @DisplayName("Should throw DEFINITION_NOT_FOUND when updating non-existent definition")
    void shouldThrowWhenNotFound() {
        when(definitionPort.findById(999L)).thenReturn(Optional.empty());

        UpdateDefinitionCommand cmd = new UpdateDefinitionCommand(999L, "Name", null, "editor");
        assertThatThrownBy(() -> service.updateMetadata(cmd))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Should enable, disable, and archive definition lifecycle states")
    void shouldChangeStatus() {
        WorkflowDefinition def = WorkflowDefinition.create(
                WorkflowKey.of("FLOW_STATUS"), "Status Flow", null, "admin", fixedInstant
        );
        def.setId(2L);

        when(definitionPort.findById(2L)).thenReturn(Optional.of(def));
        when(definitionPort.save(any(WorkflowDefinition.class))).thenAnswer(i -> i.getArgument(0));

        // Disable
        WorkflowDefinition disabled = service.disable(2L, "manager");
        assertThat(disabled.getStatus()).isEqualTo(WorkflowDefinitionStatus.DISABLED);

        // Enable
        WorkflowDefinition enabled = service.enable(2L, "manager");
        assertThat(enabled.getStatus()).isEqualTo(WorkflowDefinitionStatus.ACTIVE);

        // Archive
        WorkflowDefinition archived = service.archive(2L, "admin");
        assertThat(archived.getStatus()).isEqualTo(WorkflowDefinitionStatus.ARCHIVED);
    }

    @Test
    @DisplayName("Should reject archiving definition when a pending publication exists")
    void shouldRejectArchiveWithPendingPublication() {
        WorkflowDefinition def = WorkflowDefinition.create(
                WorkflowKey.of("FLOW_PENDING"), "Pending Flow", null, "admin", fixedInstant
        );
        def.setId(3L);
        def.setPendingVersion(100L, "admin", fixedInstant);

        when(definitionPort.findById(3L)).thenReturn(Optional.of(def));

        assertThatThrownBy(() -> service.archive(3L, "admin"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("publication is pending");
    }

    @Test
    @DisplayName("Should query definitions by id, key, and all")
    void shouldQueryDefinitions() {
        WorkflowDefinition def = WorkflowDefinition.create(
                WorkflowKey.of("QUERY_FLOW"), "Query Flow", null, "admin", fixedInstant
        );
        def.setId(4L);

        when(definitionPort.findById(4L)).thenReturn(Optional.of(def));
        when(definitionPort.findByWorkflowKey(WorkflowKey.of("QUERY_FLOW"))).thenReturn(Optional.of(def));
        when(definitionPort.findAll()).thenReturn(List.of(def));

        assertThat(service.getById(4L)).contains(def);
        assertThat(service.getByKey(WorkflowKey.of("QUERY_FLOW"))).contains(def);
        assertThat(service.getAll()).containsExactly(def);
    }
}
