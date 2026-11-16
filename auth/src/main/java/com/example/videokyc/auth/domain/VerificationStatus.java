package com.example.videokyc.auth.domain;

/**
 * Hard-coded AUTH lifecycle statuses for the initial verification lifecycle.
 * The dynamic workflow engine belongs to a later phase.
 * Transitions are enforced by VerificationStateService; the dynamic WORKFLOW engine is still not implemented.
 */
public enum VerificationStatus {
    NOT_SUBMITTED,
    UPLOADING,
    UPLOAD_FAILED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
