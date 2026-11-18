package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.version.VersionNumber;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for query operations on {@link WorkflowVersion}.
 */
public interface GetWorkflowVersionUseCase {

    Optional<WorkflowVersion> getById(Long versionId);

    Optional<WorkflowVersion> getByDefinitionIdAndVersionNumber(Long definitionId, VersionNumber versionNumber);

    List<WorkflowVersion> getAllByDefinitionId(Long definitionId);
}
