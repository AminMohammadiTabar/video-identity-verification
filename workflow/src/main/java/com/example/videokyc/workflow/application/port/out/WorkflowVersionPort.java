package com.example.videokyc.workflow.application.port.out;

import com.example.videokyc.workflow.domain.version.VersionNumber;
import com.example.videokyc.workflow.domain.version.WorkflowVersion;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and retrieving {@link WorkflowVersion} aggregate roots
 * along with their state and transition graphs.
 */
public interface WorkflowVersionPort {

    /**
     * Saves or updates a workflow version aggregate along with its states and transitions.
     */
    WorkflowVersion save(WorkflowVersion version);

    /**
     * Finds a workflow version by its internal ID (including states and transitions).
     */
    Optional<WorkflowVersion> findById(Long id);

    /**
     * Finds a workflow version by its definition ID and version number.
     */
    Optional<WorkflowVersion> findByDefinitionIdAndVersionNumber(Long definitionId, VersionNumber versionNumber);

    /**
     * Finds the maximum version number for a definition, or empty if none exist yet.
     */
    Optional<Integer> findMaxVersionNumberByDefinitionId(Long definitionId);

    /**
     * Returns all versions for a definition ordered by version number descending.
     */
    List<WorkflowVersion> findAllByDefinitionId(Long definitionId);
}
