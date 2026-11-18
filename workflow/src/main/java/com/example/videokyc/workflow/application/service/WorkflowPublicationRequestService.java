package com.example.videokyc.workflow.application.service;

import com.example.videokyc.workflow.application.port.in.GetPublicationJobUseCase;
import com.example.videokyc.workflow.application.port.in.RequestPublicationUseCase;
import com.example.videokyc.workflow.application.port.in.ValidateDraftVersionUseCase;
import com.example.videokyc.workflow.application.port.out.PublicationJobPort;
import com.example.videokyc.workflow.application.port.out.WorkflowDefinitionPort;
import com.example.videokyc.workflow.application.port.out.WorkflowVersionPort;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinitionStatus;
import com.example.videokyc.workflow.domain.publication.PublicationJob;
import com.example.videokyc.workflow.domain.publication.PublicationJobStatus;
import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.state.StateType;
import com.example.videokyc.workflow.domain.state.WorkflowState;
import com.example.videokyc.workflow.domain.transition.WorkflowTransition;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;
import com.example.videokyc.workflow.domain.version.WorkflowVersionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application service managing pre-publication validation, publication requests,
 * and retry workflows for {@link WorkflowVersion} graphs and {@link PublicationJob} aggregates.
 */
@Service
@Transactional
public class WorkflowPublicationRequestService implements
        ValidateDraftVersionUseCase,
        RequestPublicationUseCase,
        GetPublicationJobUseCase {

    private final WorkflowDefinitionPort definitionPort;
    private final WorkflowVersionPort versionPort;
    private final PublicationJobPort publicationJobPort;
    private final Clock clock;

    public WorkflowPublicationRequestService(WorkflowDefinitionPort definitionPort,
                                             WorkflowVersionPort versionPort,
                                             PublicationJobPort publicationJobPort) {
        this(definitionPort, versionPort, publicationJobPort, Clock.systemUTC());
    }

    @Autowired
    public WorkflowPublicationRequestService(WorkflowDefinitionPort definitionPort,
                                             WorkflowVersionPort versionPort,
                                             PublicationJobPort publicationJobPort,
                                             @Autowired(required = false) Clock clock) {
        this.definitionPort = definitionPort;
        this.versionPort = versionPort;
        this.publicationJobPort = publicationJobPort;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationReport validateDraft(Long versionId) {
        WorkflowVersion version = versionPort.findById(versionId)
                .orElseThrow(() -> new DomainException("VERSION_NOT_FOUND",
                        "Workflow version not found with ID: " + versionId));

        List<String> errors = new ArrayList<>();
        if (version.getStatus() != WorkflowVersionStatus.DRAFT) {
            errors.add("Version status is " + version.getStatus() + "; only DRAFT versions can be validated for publication");
        }

        long initialCount = version.getStates().values().stream()
                .filter(s -> s.getStateType() == StateType.INITIAL)
                .count();
        if (initialCount != 1) {
            errors.add("Workflow version must have exactly one INITIAL state, but found: " + initialCount);
        }

        long finalCount = version.getStates().values().stream()
                .filter(s -> s.getStateType() == StateType.FINAL)
                .count();
        if (finalCount < 1) {
            errors.add("Workflow version must have at least one FINAL state");
        }

        for (WorkflowTransition transition : version.getTransitions().values()) {
            WorkflowState fromState = version.getStates().get(transition.getFromStateKey());
            WorkflowState toState = version.getStates().get(transition.getToStateKey());
            if (fromState == null) {
                errors.add("Transition " + transition.getTransitionKey().value() + " originates from non-existent state: " + transition.getFromStateKey().value());
            } else if (fromState.getStateType() == StateType.FINAL) {
                errors.add("Transition " + transition.getTransitionKey().value() + " originates from FINAL state: " + fromState.getStateKey().value());
            }
            if (toState == null) {
                errors.add("Transition " + transition.getTransitionKey().value() + " targets non-existent state: " + transition.getToStateKey().value());
            }
        }

        if (version.getContextSchema() == null || version.getContextSchema().schemaJson() == null || version.getContextSchema().schemaJson().isBlank()) {
            errors.add("Workflow version must have a valid context schema");
        }

        return errors.isEmpty() ? ValidationReport.success() : ValidationReport.failure(errors);
    }

    @Override
    public PublicationJob requestPublication(RequestPublicationCommand command) {
        WorkflowVersion version = versionPort.findById(command.versionId())
                .orElseThrow(() -> new DomainException("VERSION_NOT_FOUND",
                        "Workflow version not found with ID: " + command.versionId()));

        WorkflowDefinition definition = definitionPort.findById(version.getDefinitionId())
                .orElseThrow(() -> new DomainException("DEFINITION_NOT_FOUND",
                        "Workflow definition not found with ID: " + version.getDefinitionId()));

        if (definition.getStatus() == WorkflowDefinitionStatus.ARCHIVED) {
            throw new DomainException("DEFINITION_ARCHIVED",
                    "Cannot publish version for archived workflow definition: " + definition.getId());
        }

        Instant now = clock.instant();
        Optional<PublicationJob> existingJobOpt = publicationJobPort.findByVersionId(version.getId());

        if (existingJobOpt.isPresent()) {
            PublicationJob existingJob = existingJobOpt.get();
            if (existingJob.getStatus() == PublicationJobStatus.FAILED) {
                return retryPublicationInternal(version, existingJob, now);
            } else if (existingJob.getStatus() == PublicationJobStatus.SUCCEEDED) {
                throw new DomainException("VERSION_ALREADY_PUBLISHED",
                        "Workflow version " + version.getId() + " is already published");
            } else {
                throw new DomainException("PUBLICATION_IN_PROGRESS",
                        "Publication job is already in progress with status: " + existingJob.getStatus());
            }
        }

        // Validate, generate snapshot canonical JSON, calculate SHA-256 checksum, and update status to PUBLISH_PENDING
        version.requestPublication(definition.getWorkflowKey());
        versionPort.save(version);

        PublicationJob job = PublicationJob.create(version.getId(), command.maxAttempts(), now);
        return publicationJobPort.save(job);
    }

    @Override
    public PublicationJob retryPublication(Long versionId) {
        WorkflowVersion version = versionPort.findById(versionId)
                .orElseThrow(() -> new DomainException("VERSION_NOT_FOUND",
                        "Workflow version not found with ID: " + versionId));

        PublicationJob existingJob = publicationJobPort.findByVersionId(versionId)
                .orElseThrow(() -> new DomainException("PUBLICATION_JOB_NOT_FOUND",
                        "No publication job found for version ID: " + versionId));

        Instant now = clock.instant();
        return retryPublicationInternal(version, existingJob, now);
    }

    private PublicationJob retryPublicationInternal(WorkflowVersion version, PublicationJob job, Instant now) {
        if (job.getStatus() != PublicationJobStatus.FAILED) {
            throw new DomainException("INVALID_JOB_STATUS_FOR_RETRY",
                    "Can only retry a FAILED publication job, current status: " + job.getStatus());
        }

        if (version.getStatus() == WorkflowVersionStatus.PUBLICATION_FAILED) {
            version.retryPublication();
            versionPort.save(version);
        }

        job.manualRetry(now);
        return publicationJobPort.save(job);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicationJob> getById(Long jobId) {
        return publicationJobPort.findById(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicationJob> getByVersionId(Long versionId) {
        return publicationJobPort.findByVersionId(versionId);
    }
}
