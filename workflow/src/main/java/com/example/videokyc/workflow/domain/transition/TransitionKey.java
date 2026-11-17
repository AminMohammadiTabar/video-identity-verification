package com.example.videokyc.workflow.domain.transition;

import com.example.videokyc.workflow.domain.shared.DomainException;

import java.util.regex.Pattern;

/**
 * Value object representing a unique transition identifier within a workflow version.
 * <p>
 * Constraints:
 * <ul>
 *   <li>Non-null, non-blank, trimmed.</li>
 *   <li>Maximum 100 characters.</li>
 *   <li>Format: uppercase letters, digits, underscore, and hyphen ({@code ^[A-Z0-9_-]+$}).</li>
 * </ul>
 */
public record TransitionKey(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9_-]{1,100}$");

    public TransitionKey {
        if (value == null || value.isBlank()) {
            throw new DomainException("INVALID_TRANSITION_KEY", "Transition key must not be null or blank");
        }
        String trimmed = value.trim();
        if (!PATTERN.matcher(trimmed).matches()) {
            throw new DomainException("INVALID_TRANSITION_KEY_FORMAT",
                    "Transition key must match pattern [A-Z0-9_-] with length between 1 and 100 chars: " + trimmed);
        }
        value = trimmed;
    }

    public static TransitionKey of(String key) {
        return new TransitionKey(key);
    }

    @Override
    public String toString() {
        return value;
    }
}
