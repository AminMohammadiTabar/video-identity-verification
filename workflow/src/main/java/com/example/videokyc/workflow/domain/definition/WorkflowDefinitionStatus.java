package com.example.videokyc.workflow.domain.definition;

/**
 * Administrative lifecycle status for a workflow definition.
 */
public enum WorkflowDefinitionStatus {
    /**
     * Administratively enabled. If an active version exists, the workflow is executable.
     */
    ACTIVE,

    /**
     * Administratively disabled. Consumers must not initiate new transitions even if an active snapshot exists.
     */
    DISABLED,

    /**
     * Terminal administrative status. Hidden from normal UI operations; definitions are never physically deleted.
     */
    ARCHIVED
}
