package com.example.videokyc.workflow.application.port.in;

import com.example.videokyc.workflow.domain.publication.PublicationJob;

/**
 * Inbound port for requesting publication of a draft workflow version or retrying a failed publication.
 */
public interface RequestPublicationUseCase {

    PublicationJob requestPublication(RequestPublicationCommand command);

    PublicationJob retryPublication(Long versionId);

    record RequestPublicationCommand(
            Long versionId,
            int maxAttempts
    ) {
        public RequestPublicationCommand {
            if (versionId == null) {
                throw new IllegalArgumentException("versionId must not be null");
            }
            if (maxAttempts <= 0) {
                maxAttempts = 5;
            }
        }

        public static RequestPublicationCommand of(Long versionId) {
            return new RequestPublicationCommand(versionId, 5);
        }
    }
}
