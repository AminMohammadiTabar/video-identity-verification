package com.example.videokyc.workflow.domain.definition;

import com.example.videokyc.workflow.domain.shared.DomainException;

import java.util.regex.Pattern;

/**
 * Stable, external business identifier for a workflow definition.
 * <p>
 * Constraints:
 * <ul>
 *   <li>Non-null, non-blank, trimmed.</li>
 *   <li>Maximum 100 characters.</li>
 *   <li>Format: uppercase letters, digits, underscore, and hyphen ({@code ^[A-Z0-9_-]+$}).</li>
 * </ul>
 */
public record WorkflowKey(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9_-]{1,100}$");

    public WorkflowKey {
        if (value == null || value.isBlank()) {
            throw new DomainException("INVALID_WORKFLOW_KEY", "Workflow key must not be null or blank");
        }
        String trimmed = value.trim();
        if (!PATTERN.matcher(trimmed).matches()) {
            throw new DomainException("INVALID_WORKFLOW_KEY_FORMAT",
                    "Workflow key must match pattern [A-Z0-9_-] with length between 1 and 100 chars: " + trimmed);
        }
        value = trimmed;
    }

    public static WorkflowKey of(String key) {
        return new WorkflowKey(key);
    }

    @Override
    public String toString() {
        return value;
    }
}
