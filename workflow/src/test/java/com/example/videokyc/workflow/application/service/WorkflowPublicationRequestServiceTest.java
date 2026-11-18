package com.example.videokyc.workflow.application.service;

import com.example.videokyc.workflow.application.port.in.RequestPublicationUseCase.RequestPublicationCommand;
import com.example.videokyc.workflow.application.port.in.ValidateDraftVersionUseCase.ValidationReport;
import com.example.videokyc.workflow.application.port.out.PublicationJobPort;
import com.example.videokyc.workflow.application.port.out.WorkflowDefinitionPort;
import com.example.videokyc.workflow.application.port.out.WorkflowVersionPort;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import com.example.videokyc.workflow.domain.publication.PublicationJob;
import com.example.videokyc.workflow.domain.publication.PublicationJobStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowPublicationRequestServiceTest {

    @Mock
    private WorkflowDefinitionPort definitionPort;

    @Mock
    private WorkflowVersionPort versionPort;

    @Mock
    private PublicationJobPort publicationJobPort;

    private final Instant fixedInstant = Instant.parse("2026-11-18T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    private WorkflowPublicationRequestService service;
    private WorkflowDefinition definition;
    private WorkflowVersion draftVersion;

    @BeforeEach
    void setUp() {
        service = new WorkflowPublicationRequestService(definitionPort, versionPort, publicationJobPort, fixedClock);

        definition = WorkflowDefinition.create(
                WorkflowKey.of("ID_VERIFY"), "Identity Verification", "Desc", "admin", fixedInstant
        );
        definition.setId(10L);

        draftVersion = WorkflowVersion.createDraft(
                10L, VersionNumber.of(1), ContextSchema.defaultSchema(), "admin", fixedInstant
        );
        draftVersion.setId(20L);

        WorkflowState start = WorkflowState.create(StateKey.of("START"), "Start", null, StateType.INITIAL, 0, null);
        WorkflowState end = WorkflowState.create(StateKey.of("END"), "End", null, StateType.FINAL, 1, null);
        draftVersion.addState(start);
        draftVersion.addState(end);

        WorkflowTransition t1 = WorkflowTransition.create(
                TransitionKey.of("COMPLETE"), "Complete", null, start.getStateKey(), end.getStateKey(), null, 0
        );
        draftVersion.addTransition(t1);
    }

    @Test
    @DisplayName("Should validate draft version successfully when graph invariants are satisfied")
    void shouldValidateValidDraft() {
        when(versionPort.findById(20L)).thenReturn(Optional.of(draftVersion));

        ValidationReport report = service.validateDraft(20L);

        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
    }

    @Test
    @DisplayName("Should report validation errors when graph has structural violations")
    void shouldReportValidationErrors() {
        WorkflowVersion invalid = WorkflowVersion.createDraft(
                10L, VersionNumber.of(1), ContextSchema.defaultSchema(), "admin", fixedInstant
        );
        invalid.setId(30L);
        // Only intermediate state, no initial or final
        invalid.addState(WorkflowState.create(StateKey.of("INTERMEDIATE"), "Step", null, StateType.INTERMEDIATE, 0, null));

        when(versionPort.findById(30L)).thenReturn(Optional.of(invalid));

        ValidationReport report = service.validateDraft(30L);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).hasSize(2);
        assertThat(report.errors()).anyMatch(e -> e.contains("INITIAL state"));
        assertThat(report.errors()).anyMatch(e -> e.contains("FINAL state"));
    }

    @Test
    @DisplayName("Should successfully request publication: generates snapshot, SHA-256, and creates PENDING job")
    void shouldRequestPublicationSuccessfully() {
        when(versionPort.findById(20L)).thenReturn(Optional.of(draftVersion));
        when(definitionPort.findById(10L)).thenReturn(Optional.of(definition));
        when(publicationJobPort.findByVersionId(20L)).thenReturn(Optional.empty());
        when(versionPort.save(any(WorkflowVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(publicationJobPort.save(any(PublicationJob.class))).thenAnswer(invocation -> {
            PublicationJob job = invocation.getArgument(0);
            job.setId(100L);
            return job;
        });

        PublicationJob job = service.requestPublication(RequestPublicationCommand.of(20L));

        assertThat(job).isNotNull();
        assertThat(job.getId()).isEqualTo(100L);
        assertThat(job.getStatus()).isEqualTo(PublicationJobStatus.PENDING);
        assertThat(job.getVersionId()).isEqualTo(20L);

        assertThat(draftVersion.getStatus()).isEqualTo(WorkflowVersionStatus.PUBLISH_PENDING);
        assertThat(draftVersion.getSnapshotJson()).isNotNull();
        assertThat(draftVersion.getChecksum()).isNotNull();
        assertThat(draftVersion.getChecksum().value()).hasSize(64);

        verify(versionPort).save(draftVersion);
        verify(publicationJobPort).save(any(PublicationJob.class));
    }

    @Test
    @DisplayName("Should reject publication request if definition is archived")
    void shouldRejectPublicationIfDefinitionArchived() {
        definition.archive("admin", fixedInstant);
        when(versionPort.findById(20L)).thenReturn(Optional.of(draftVersion));
        when(definitionPort.findById(10L)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.requestPublication(RequestPublicationCommand.of(20L)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot publish version for archived workflow definition");
    }

    @Test
    @DisplayName("Should reject publication request if a job is already in progress")
    void shouldRejectPublicationIfAlreadyInProgress() {
        PublicationJob activeJob = PublicationJob.create(20L, 5, fixedInstant);
        when(versionPort.findById(20L)).thenReturn(Optional.of(draftVersion));
        when(definitionPort.findById(10L)).thenReturn(Optional.of(definition));
        when(publicationJobPort.findByVersionId(20L)).thenReturn(Optional.of(activeJob));

        assertThatThrownBy(() -> service.requestPublication(RequestPublicationCommand.of(20L)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Publication job is already in progress");
    }

    @Test
    @DisplayName("Should automatically retry if existing publication job is FAILED")
    void shouldAutoRetryIfExistingJobFailed() {
        PublicationJob failedJob = PublicationJob.create(20L, 5, fixedInstant);
        failedJob.setId(101L);
        failedJob.markFailed("NETWORK_ERR", "Timeout connecting to Redis", fixedInstant);

        draftVersion.requestPublication(definition.getWorkflowKey());
        draftVersion.markPublicationFailed();

        when(versionPort.findById(20L)).thenReturn(Optional.of(draftVersion));
        when(definitionPort.findById(10L)).thenReturn(Optional.of(definition));
        when(publicationJobPort.findByVersionId(20L)).thenReturn(Optional.of(failedJob));
        when(publicationJobPort.save(any(PublicationJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicationJob retried = service.requestPublication(RequestPublicationCommand.of(20L));

        assertThat(retried.getStatus()).isEqualTo(PublicationJobStatus.PENDING);
        assertThat(draftVersion.getStatus()).isEqualTo(WorkflowVersionStatus.PUBLISH_PENDING);
        verify(versionPort).save(draftVersion);
        verify(publicationJobPort).save(failedJob);
    }

    @Test
    @DisplayName("Should execute manual retry on FAILED publication job")
    void shouldExecuteManualRetry() {
        PublicationJob failedJob = PublicationJob.create(20L, 5, fixedInstant);
        failedJob.setId(102L);
        failedJob.markFailed("REDIS_ERR", "Cluster unavailable", fixedInstant);

        draftVersion.requestPublication(definition.getWorkflowKey());
        draftVersion.markPublicationFailed();

        when(versionPort.findById(20L)).thenReturn(Optional.of(draftVersion));
        when(publicationJobPort.findByVersionId(20L)).thenReturn(Optional.of(failedJob));
        when(publicationJobPort.save(any(PublicationJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicationJob retried = service.retryPublication(20L);

        assertThat(retried.getStatus()).isEqualTo(PublicationJobStatus.PENDING);
        assertThat(draftVersion.getStatus()).isEqualTo(WorkflowVersionStatus.PUBLISH_PENDING);
    }

    @Test
    @DisplayName("Should query publication jobs by ID and version ID")
    void shouldQueryPublicationJobs() {
        PublicationJob job = PublicationJob.create(20L, 5, fixedInstant);
        job.setId(200L);

        when(publicationJobPort.findById(200L)).thenReturn(Optional.of(job));
        when(publicationJobPort.findByVersionId(20L)).thenReturn(Optional.of(job));

        assertThat(service.getById(200L)).contains(job);
        assertThat(service.getByVersionId(20L)).contains(job);
    }

    @Test
    @DisplayName("Should reject publication when transition Guard is incompatible with ContextSchema")
    void shouldRejectPublicationWhenGuardIncompatibleWithSchema() {
        ContextSchema strictSchema = ContextSchema.of("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "applicant": {
                      "type": "object",
                      "properties": {
                        "name": { "type": "string" }
                      },
                      "additionalProperties": false
                    }
                  },
                  "additionalProperties": false
                }
                """);
        WorkflowVersion v = WorkflowVersion.createDraft(10L, VersionNumber.of(1), strictSchema, "admin", fixedInstant);
        v.setId(50L);
        WorkflowState s1 = WorkflowState.create(StateKey.of("S1"), "S1", null, StateType.INITIAL, 0, null);
        WorkflowState s2 = WorkflowState.create(StateKey.of("S2"), "S2", null, StateType.FINAL, 1, null);
        v.addState(s1);
        v.addState(s2);

        // Numeric GT operator applied to string property 'applicant.name'
        WorkflowTransition t = WorkflowTransition.create(
                TransitionKey.of("T1"), "T1", null, s1.getStateKey(), s2.getStateKey(),
                com.example.videokyc.workflow.domain.transition.guard.GuardExpression.of(
                        "{\"operator\": \"GT\", \"path\": \"applicant.name\", \"value\": 100}"
                ), 0
        );
        v.addTransition(t);

        when(versionPort.findById(50L)).thenReturn(Optional.of(v));
        when(definitionPort.findById(10L)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.requestPublication(RequestPublicationCommand.of(50L)))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("incompatible with schema type 'string'");
    }
}
