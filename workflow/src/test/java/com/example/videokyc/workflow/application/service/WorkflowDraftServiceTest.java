package com.example.videokyc.workflow.application.service;

import com.example.videokyc.workflow.application.port.in.CreateDraftVersionUseCase.CreateDraftVersionCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowStatesUseCase.AddStateCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowStatesUseCase.RemoveStateCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowStatesUseCase.UpdateStateCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowTransitionsUseCase.AddTransitionCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowTransitionsUseCase.RemoveTransitionCommand;
import com.example.videokyc.workflow.application.port.in.ManageWorkflowTransitionsUseCase.UpdateTransitionCommand;
import com.example.videokyc.workflow.application.port.in.UpdateDraftContextSchemaUseCase.UpdateContextSchemaCommand;
import com.example.videokyc.workflow.application.port.out.WorkflowDefinitionPort;
import com.example.videokyc.workflow.application.port.out.WorkflowVersionPort;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.state.StateKey;
import com.example.videokyc.workflow.domain.state.StateType;
import com.example.videokyc.workflow.domain.state.WorkflowState;
import com.example.videokyc.workflow.domain.transition.TransitionKey;
import com.example.videokyc.workflow.domain.transition.WorkflowTransition;
import com.example.videokyc.workflow.domain.version.ContextSchema;
import com.example.videokyc.workflow.domain.version.VersionNumber;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;
import com.example.videokyc.workflow.domain.version.WorkflowVersionStatus;
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
class WorkflowDraftServiceTest {

    @Mock
    private WorkflowDefinitionPort definitionPort;

    @Mock
    private WorkflowVersionPort versionPort;

    private final Instant fixedInstant = Instant.parse("2026-11-18T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    private WorkflowDraftService service;

    private WorkflowDefinition sampleDefinition;

    @BeforeEach
    void setUp() {
        service = new WorkflowDraftService(definitionPort, versionPort, fixedClock);
        sampleDefinition = WorkflowDefinition.create(
                WorkflowKey.of("KYC_FLOW"), "KYC Flow", "Description", "admin", fixedInstant
        );
        sampleDefinition.setId(100L);
    }

    @Test
    @DisplayName("Should create initial draft version (v1) with default schema")
    void shouldCreateInitialDraftSuccessfully() {
        when(definitionPort.findById(100L)).thenReturn(Optional.of(sampleDefinition));
        when(versionPort.findMaxVersionNumberByDefinitionId(100L)).thenReturn(Optional.empty());
        when(versionPort.save(any(WorkflowVersion.class))).thenAnswer(invocation -> {
            WorkflowVersion v = invocation.getArgument(0);
            v.setId(200L);
            return v;
        });

        CreateDraftVersionCommand command = new CreateDraftVersionCommand(100L, null, null, "author");
        WorkflowVersion created = service.createDraft(command);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(200L);
        assertThat(created.getVersionNumber().value()).isEqualTo(1);
        assertThat(created.getStatus()).isEqualTo(WorkflowVersionStatus.DRAFT);
        assertThat(created.getContextSchema().schemaJson()).contains("https://json-schema.org/draft/2020-12/schema");
        verify(versionPort).save(any(WorkflowVersion.class));
    }

    @Test
    @DisplayName("Should create incremented draft version (e.g. v3 if v2 exists)")
    void shouldCreateIncrementedDraftVersion() {
        when(definitionPort.findById(100L)).thenReturn(Optional.of(sampleDefinition));
        when(versionPort.findMaxVersionNumberByDefinitionId(100L)).thenReturn(Optional.of(2));
        when(versionPort.save(any(WorkflowVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDraftVersionCommand command = new CreateDraftVersionCommand(100L, null, null, "author");
        WorkflowVersion created = service.createDraft(command);

        assertThat(created.getVersionNumber().value()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should reject draft creation if definition is not found")
    void shouldRejectDraftCreationIfDefinitionNotFound() {
        when(definitionPort.findById(999L)).thenReturn(Optional.empty());

        CreateDraftVersionCommand command = new CreateDraftVersionCommand(999L, null, null, "author");

        assertThatThrownBy(() -> service.createDraft(command))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Workflow definition not found with ID: 999");
    }

    @Test
    @DisplayName("Should reject draft creation if definition is archived")
    void shouldRejectDraftCreationIfDefinitionArchived() {
        sampleDefinition.archive("admin", fixedInstant);
        when(definitionPort.findById(100L)).thenReturn(Optional.of(sampleDefinition));

        CreateDraftVersionCommand command = new CreateDraftVersionCommand(100L, null, null, "author");

        assertThatThrownBy(() -> service.createDraft(command))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot create a draft version for an archived workflow definition");
    }

    @Test
    @DisplayName("Should clone states, transitions, and schema from existing version")
    void shouldCloneFromExistingVersion() {
        ContextSchema customSchema = ContextSchema.of("{\"type\": \"object\", \"properties\": {\"age\": {\"type\": \"integer\"}}}");
        WorkflowVersion source = WorkflowVersion.createDraft(100L, VersionNumber.of(1), customSchema, "admin", fixedInstant);
        source.setId(201L);

        WorkflowState start = WorkflowState.create(StateKey.of("START"), "Start", "First", StateType.INITIAL, 0, null);
        WorkflowState end = WorkflowState.create(StateKey.of("END"), "End", "Last", StateType.FINAL, 1, null);
        source.addState(start);
        source.addState(end);

        WorkflowTransition transition = WorkflowTransition.create(
                TransitionKey.of("PROCEED"), "Proceed", null, start.getStateKey(), end.getStateKey(), null, 0
        );
        source.addTransition(transition);

        when(definitionPort.findById(100L)).thenReturn(Optional.of(sampleDefinition));
        when(versionPort.findMaxVersionNumberByDefinitionId(100L)).thenReturn(Optional.of(1));
        when(versionPort.findById(201L)).thenReturn(Optional.of(source));
        when(versionPort.save(any(WorkflowVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateDraftVersionCommand command = new CreateDraftVersionCommand(100L, null, 201L, "author");
        WorkflowVersion cloned = service.createDraft(command);

        assertThat(cloned.getVersionNumber().value()).isEqualTo(2);
        assertThat(cloned.getStates()).hasSize(2);
        assertThat(cloned.getStates()).containsKey(StateKey.of("START"));
        assertThat(cloned.getStates()).containsKey(StateKey.of("END"));
        assertThat(cloned.getTransitions()).hasSize(1);
        assertThat(cloned.getTransitions()).containsKey(TransitionKey.of("PROCEED"));
        assertThat(cloned.getContextSchema()).isEqualTo(customSchema);
    }

    @Test
    @DisplayName("Should reject clone if source version does not belong to same definition")
    void shouldRejectCloneIfSourceBelongsToDifferentDefinition() {
        WorkflowVersion source = WorkflowVersion.createDraft(999L, VersionNumber.of(1), ContextSchema.defaultSchema(), "admin", fixedInstant);
        source.setId(201L);

        when(definitionPort.findById(100L)).thenReturn(Optional.of(sampleDefinition));
        when(versionPort.findMaxVersionNumberByDefinitionId(100L)).thenReturn(Optional.of(0));
        when(versionPort.findById(201L)).thenReturn(Optional.of(source));

        CreateDraftVersionCommand command = new CreateDraftVersionCommand(100L, null, 201L, "author");

        assertThatThrownBy(() -> service.createDraft(command))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Source version does not belong to workflow definition");
    }

    @Test
    @DisplayName("Should add, update, and remove states in draft version")
    void shouldManageStatesInDraftVersion() {
        WorkflowVersion draft = WorkflowVersion.createDraft(100L, VersionNumber.of(1), ContextSchema.defaultSchema(), "admin", fixedInstant);
        draft.setId(301L);

        when(versionPort.findById(301L)).thenReturn(Optional.of(draft));
        when(versionPort.save(any(WorkflowVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Add
        AddStateCommand addCmd = new AddStateCommand(301L, "STEP_1", "Step 1", "Desc", StateType.INITIAL, 0, null);
        WorkflowVersion updatedAfterAdd = service.addState(addCmd);
        assertThat(updatedAfterAdd.getStates()).containsKey(StateKey.of("STEP_1"));

        // Update
        UpdateStateCommand updateCmd = new UpdateStateCommand(301L, "STEP_1", "Updated Step 1", "New Desc", StateType.INTERMEDIATE, 2, "{\"color\":\"blue\"}");
        WorkflowVersion updatedAfterUpdate = service.updateState(updateCmd);
        WorkflowState state = updatedAfterUpdate.getStates().get(StateKey.of("STEP_1"));
        assertThat(state.getName()).isEqualTo("Updated Step 1");
        assertThat(state.getStateType()).isEqualTo(StateType.INTERMEDIATE);
        assertThat(state.getDisplayOrder()).isEqualTo(2);

        // Remove
        RemoveStateCommand removeCmd = new RemoveStateCommand(301L, "STEP_1");
        WorkflowVersion updatedAfterRemove = service.removeState(removeCmd);
        assertThat(updatedAfterRemove.getStates()).doesNotContainKey(StateKey.of("STEP_1"));
    }

    @Test
    @DisplayName("Should add, update, and remove transitions with Guard DSL")
    void shouldManageTransitionsInDraftVersion() {
        WorkflowVersion draft = WorkflowVersion.createDraft(100L, VersionNumber.of(1), ContextSchema.defaultSchema(), "admin", fixedInstant);
        draft.setId(302L);
        draft.addState(WorkflowState.create(StateKey.of("A"), "A", null, StateType.INITIAL, 0, null));
        draft.addState(WorkflowState.create(StateKey.of("B"), "B", null, StateType.INTERMEDIATE, 1, null));
        draft.addState(WorkflowState.create(StateKey.of("C"), "C", null, StateType.FINAL, 2, null));

        when(versionPort.findById(302L)).thenReturn(Optional.of(draft));
        when(versionPort.save(any(WorkflowVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String guardJson = """
                {
                  "operator": "EQ",
                  "path": "user.isVerified",
                  "value": true
                }
                """;

        // Add Transition
        AddTransitionCommand addCmd = new AddTransitionCommand(302L, "T_A_B", "To B", "Desc", "A", "B", guardJson, 0);
        WorkflowVersion updatedAfterAdd = service.addTransition(addCmd);
        assertThat(updatedAfterAdd.getTransitions()).containsKey(TransitionKey.of("T_A_B"));
        WorkflowTransition added = updatedAfterAdd.getTransitions().get(TransitionKey.of("T_A_B"));
        assertThat(added.getGuardExpression()).isNotNull();

        // Update Transition
        UpdateTransitionCommand updateCmd = new UpdateTransitionCommand(302L, "T_A_B", "To C", "Updated", "A", "C", null, 1);
        WorkflowVersion updatedAfterUpdate = service.updateTransition(updateCmd);
        WorkflowTransition updated = updatedAfterUpdate.getTransitions().get(TransitionKey.of("T_A_B"));
        assertThat(updated.getToStateKey().value()).isEqualTo("C");
        assertThat(updated.getGuardExpression()).isNull();

        // Remove Transition
        RemoveTransitionCommand removeCmd = new RemoveTransitionCommand(302L, "T_A_B");
        WorkflowVersion updatedAfterRemove = service.removeTransition(removeCmd);
        assertThat(updatedAfterRemove.getTransitions()).doesNotContainKey(TransitionKey.of("T_A_B"));
    }

    @Test
    @DisplayName("Should update context schema in draft version")
    void shouldUpdateContextSchema() {
        WorkflowVersion draft = WorkflowVersion.createDraft(100L, VersionNumber.of(1), ContextSchema.defaultSchema(), "admin", fixedInstant);
        draft.setId(303L);

        when(versionPort.findById(303L)).thenReturn(Optional.of(draft));
        when(versionPort.save(any(WorkflowVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String newSchemaJson = "{\"type\":\"object\",\"properties\":{\"docType\":{\"type\":\"string\"}}}";
        UpdateContextSchemaCommand cmd = new UpdateContextSchemaCommand(303L, newSchemaJson);

        WorkflowVersion updated = service.updateContextSchema(cmd);
        assertThat(updated.getContextSchema().schemaJson()).isEqualTo(newSchemaJson);
    }

    @Test
    @DisplayName("Should reject state mutation if version is not in DRAFT status")
    void shouldRejectStateMutationOnNonDraftVersion() {
        WorkflowVersion published = new WorkflowVersion(
                401L, 100L, VersionNumber.of(1), WorkflowVersionStatus.PUBLISHED,
                ContextSchema.defaultSchema(), "{}", null, 1, fixedInstant, "admin", fixedInstant, 0L
        );

        when(versionPort.findById(401L)).thenReturn(Optional.of(published));

        AddStateCommand cmd = new AddStateCommand(401L, "NEW_STATE", "Name", null, StateType.INTERMEDIATE, 0, null);

        assertThatThrownBy(() -> service.addState(cmd))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("only DRAFT versions are mutable");
    }

    @Test
    @DisplayName("Should delegate query operations to outbound version port")
    void shouldDelegateQueries() {
        WorkflowVersion v1 = WorkflowVersion.createDraft(100L, VersionNumber.of(1), ContextSchema.defaultSchema(), "admin", fixedInstant);
        v1.setId(501L);

        when(versionPort.findById(501L)).thenReturn(Optional.of(v1));
        when(versionPort.findByDefinitionIdAndVersionNumber(100L, VersionNumber.of(1))).thenReturn(Optional.of(v1));
        when(versionPort.findAllByDefinitionId(100L)).thenReturn(List.of(v1));

        assertThat(service.getById(501L)).contains(v1);
        assertThat(service.getByDefinitionIdAndVersionNumber(100L, VersionNumber.of(1))).contains(v1);
        assertThat(service.getAllByDefinitionId(100L)).containsExactly(v1);
    }
}
