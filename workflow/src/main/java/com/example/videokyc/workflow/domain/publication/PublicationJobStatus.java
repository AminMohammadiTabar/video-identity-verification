package com.example.videokyc.workflow.domain.publication;

/**
 * Execution status for an asynchronous workflow version publication job.
 */
public enum PublicationJobStatus {
    /**
     * Queued for worker processing.
     */
    PENDING,

    /**
     * Claimed by an active worker under a time-bounded lease.
     */
    PROCESSING,

    /**
     * Transient failure encountered; waiting for scheduled backoff retry.
     */
    RETRY_WAIT,

    /**
     * Version snapshot successfully stored in Redis, pointer activated, and Outbox event queued.
     */
    SUCCEEDED,

    /**
     * Automatic retries exhausted or terminal error occurred; requires administrative intervention.
     */
    FAILED
}
