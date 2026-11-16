package com.example.videokyc.auth.observability.audit;

import java.util.Map;
import java.util.Set;

/** Explicit, allow-listed business/security audit event. Never place secrets or raw request data here. */
public record AuditEventCommand(
        String eventType,
        String actorType,
        String actorId,
        String subjectType,
        String subjectId,
        String targetType,
        String targetId,
        String outcome,
        String reasonCode,
        String previousState,
        String newState,
        Map<String, Object> details) {

    public AuditEventCommand {
        if (eventType == null || !eventType.matches("[a-z][a-z0-9._-]{1,119}"))
            throw new IllegalArgumentException("Invalid audit event type");
        if (actorType == null || !actorType.matches("USER|ADMIN|SERVICE|SYSTEM|ANONYMOUS"))
            throw new IllegalArgumentException("Invalid audit actor type");
        if (outcome == null || !outcome.matches("SUCCESS|FAILURE|DENIED"))
            throw new IllegalArgumentException("Invalid audit outcome");
        if (containsEmail(actorId) || containsEmail(subjectId) || containsEmail(targetId))
            throw new IllegalArgumentException("Audit identifiers must not contain email addresses");
        if (reasonCode != null && !reasonCode.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Invalid audit reason code");
        details = details == null ? Map.of() : Map.copyOf(details);
        Set<String> safeKeys = Set.of("attemptNumber", "previousAttemptNumber", "dmsFileId",
                "subsystem", "ownerType", "sizeBytes", "checksumSha256", "notificationType",
                "outboxId", "attemptCount", "failureType");
        for (var entry : details.entrySet()) {
            if (!safeKeys.contains(entry.getKey()))
                throw new IllegalArgumentException("Audit detail key is not allow-listed");
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean))
                throw new IllegalArgumentException("Audit detail value must be scalar");
            if (value instanceof String text && text.length() > 512)
                throw new IllegalArgumentException("Audit detail value is too long");
        }
    }

    private static boolean containsEmail(String value) {
        return value != null && value.contains("@");
    }

    public static AuditEventCommand of(String eventType, String actorType, String actorId,
                                       String subjectType, String subjectId,
                                       String targetType, String targetId, String outcome,
                                       String reasonCode, String previousState, String newState) {
        return new AuditEventCommand(eventType, actorType, actorId, subjectType, subjectId,
                targetType, targetId, outcome, reasonCode, previousState, newState, Map.of());
    }

    public AuditEventCommand withDetails(Map<String, Object> safeDetails) {
        return new AuditEventCommand(eventType, actorType, actorId, subjectType, subjectId,
                targetType, targetId, outcome, reasonCode, previousState, newState, safeDetails);
    }
}
