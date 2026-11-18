package com.example.videokyc.workflow.application.port.in;

import java.util.List;

/**
 * Inbound port for validating a draft workflow version before publication.
 */
public interface ValidateDraftVersionUseCase {

    ValidationReport validateDraft(Long versionId);

    record ValidationReport(boolean valid, List<String> errors) {
        public static ValidationReport success() {
            return new ValidationReport(true, List.of());
        }

        public static ValidationReport failure(List<String> errors) {
            return new ValidationReport(false, errors != null ? errors : List.of());
        }
    }
}
