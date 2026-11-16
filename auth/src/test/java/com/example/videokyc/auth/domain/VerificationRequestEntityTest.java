package com.example.videokyc.auth.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VerificationRequestEntityTest {
    @Test
    void rejectionRequiresReasonAndCannotBeReplayed() {
        var request = new VerificationRequestEntity(new UserEntity("test@example.com", "hashed-password"), 1);
        assertEquals(VerificationStatus.UPLOADING, request.getStatus());
        UUID videoId = UUID.randomUUID();
        request.uploaded(videoId);
        assertEquals(VerificationStatus.PENDING_REVIEW, request.getStatus());
        assertThrows(IllegalArgumentException.class, () -> request.decide(UUID.randomUUID(), false, " "));
        UUID reviewer = UUID.randomUUID();
        request.decide(reviewer, false, "تصویر واضح نیست");
        assertEquals(VerificationStatus.REJECTED, request.getStatus());
        assertEquals(reviewer, request.getReviewedBy());
        assertNotNull(request.getReviewedAt());
        assertThrows(IllegalStateException.class, () -> request.decide(reviewer, true, null));
    }

    @Test
    void uploadFailureIsRetryableButReviewedRequestIsNot() {
        var request = new VerificationRequestEntity(new UserEntity("test@example.com", "hashed-password"), 1);
        request.uploadFailed();
        assertEquals(VerificationStatus.UPLOAD_FAILED, request.getStatus());
        request.retryUpload();
        request.uploaded(UUID.randomUUID());
        request.decide(UUID.randomUUID(), true, null);
        assertEquals(VerificationStatus.APPROVED, request.getStatus());
        assertThrows(IllegalStateException.class, request::retryUpload);
    }
}
