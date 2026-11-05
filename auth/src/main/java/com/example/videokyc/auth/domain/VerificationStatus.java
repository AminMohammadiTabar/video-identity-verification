package com.example.videokyc.auth.domain;

/**
 * Agreed hard-coded workflow statuses for phase one.
 * The dynamic workflow engine belongs to a later phase.
 * No transition logic is implemented in this step.
 */
public enum VerificationStatus {
    NOT_SUBMITTED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
