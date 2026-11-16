package com.example.videokyc.dms.observability.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditEventRecorder {
    private static final Logger log = LoggerFactory.getLogger(AuditEventRecorder.class);
    private final AuditEventRepository repository;
    private final AuditContextProvider contextProvider;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public AuditEventRecorder(AuditEventRepository repository, AuditContextProvider contextProvider,
                              ObjectMapper objectMapper,
                              @Value("${spring.application.name}") String serviceName) {
        this.repository = repository;
        this.contextProvider = contextProvider;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    /** Joins the current business transaction: successful state change and audit commit together. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditEventCommand command) {
        persist(command);
    }

    /** Persists failure/security events even if the caller's business transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditEventCommand command) {
        persist(command);
    }

    private void persist(AuditEventCommand command) {
        var context = contextProvider.current();
        String details;
        try {
            details = command.details().isEmpty() ? null : objectMapper.writeValueAsString(command.details());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit details could not be serialized", exception);
        }
        if (details != null && details.length() > 4000) {
            throw new IllegalArgumentException("Audit details exceed 4000 characters");
        }
        Instant now = Instant.now();
        repository.save(new AuditEventEntity(null, serviceName, command.eventType(), (short) 1,
                now, now, command.actorType(), limit(command.actorId(), 128),
                limit(command.subjectType(), 64), limit(command.subjectId(), 128),
                limit(command.targetType(), 64), limit(command.targetId(), 128), command.outcome(),
                limit(command.reasonCode(), 64), limit(command.previousState(), 64),
                limit(command.newState(), 64), context.requestId(), context.traceId(), context.spanId(),
                context.clientIp(), context.userAgent(), details));
        log.atDebug().addKeyValue("event", "audit.event.persisted")
                .addKeyValue("auditEventType", command.eventType())
                .addKeyValue("outcome", command.outcome())
                .addKeyValue("traceId", context.traceId() == null ? "-" : context.traceId())
                .log("Durable audit event persisted");
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "_");
        return cleaned.substring(0, Math.min(cleaned.length(), max));
    }
}
