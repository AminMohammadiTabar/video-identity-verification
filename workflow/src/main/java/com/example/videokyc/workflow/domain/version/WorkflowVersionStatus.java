package com.example.videokyc.workflow.domain.version;

/**
 * Lifecycle status of a workflow version graph.
 */
public enum WorkflowVersionStatus {
    /**
     * Mutable drafting state. States, transitions, and context schemas can be added, updated, or removed.
     */
    DRAFT,

    /**
     * Validated and queued for asynchronous publication to Redis and notification to RabbitMQ. Content is frozen.
     */
    PUBLISH_PENDING,

    /**
     * Successfully published to Redis. Completely immutable; cannot be modified or re-published.
     */
    PUBLISHED,

    /**
     * Publication failed after exhausted retries or terminal error. Content remains immutable; can be retried.
     */
    PUBLICATION_FAILED
}
