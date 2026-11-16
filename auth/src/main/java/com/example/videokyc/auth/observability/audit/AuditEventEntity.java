package com.example.videokyc.auth.observability.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable application-level audit record. Database runtime permissions must also deny UPDATE/DELETE. */
@Entity
@Table(schema = "auth", name = "audit_events")
public class AuditEventEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "service_name", nullable = false, updatable = false, length = 32)
    private String serviceName;
    @Column(name = "event_type", nullable = false, updatable = false, length = 120)
    private String eventType;
    @Column(name = "event_version", nullable = false, updatable = false)
    private short eventVersion;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
    @Column(name = "actor_type", nullable = false, updatable = false, length = 24)
    private String actorType;
    @Column(name = "actor_id", updatable = false, length = 128)
    private String actorId;
    @Column(name = "subject_type", updatable = false, length = 64)
    private String subjectType;
    @Column(name = "subject_id", updatable = false, length = 128)
    private String subjectId;
    @Column(name = "target_type", updatable = false, length = 64)
    private String targetType;
    @Column(name = "target_id", updatable = false, length = 128)
    private String targetId;
    @Column(nullable = false, updatable = false, length = 16)
    private String outcome;
    @Column(name = "reason_code", updatable = false, length = 64)
    private String reasonCode;
    @Column(name = "previous_state", updatable = false, length = 64)
    private String previousState;
    @Column(name = "new_state", updatable = false, length = 64)
    private String newState;
    @Column(name = "request_id", updatable = false, length = 64)
    private String requestId;
    @Column(name = "trace_id", updatable = false, length = 32)
    private String traceId;
    @Column(name = "span_id", updatable = false, length = 16)
    private String spanId;
    @Column(name = "client_ip", updatable = false, length = 45)
    private String clientIp;
    @Column(name = "user_agent", updatable = false, length = 512)
    private String userAgent;
    @Column(name = "details_json", updatable = false, length = 4000)
    private String detailsJson;

    protected AuditEventEntity() { }

    public AuditEventEntity(UUID id, String serviceName, String eventType, short eventVersion,
                            Instant occurredAt, Instant recordedAt, String actorType, String actorId,
                            String subjectType, String subjectId, String targetType, String targetId,
                            String outcome, String reasonCode, String previousState, String newState,
                            String requestId, String traceId, String spanId, String clientIp,
                            String userAgent, String detailsJson) {
        this.id = id;
        this.serviceName = serviceName;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.actorType = actorType;
        this.actorId = actorId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.reasonCode = reasonCode;
        this.previousState = previousState;
        this.newState = newState;
        this.requestId = requestId;
        this.traceId = traceId;
        this.spanId = spanId;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.detailsJson = detailsJson;
    }

    @PrePersist
    private void assignId() {
        if (id == null) id = UUID.randomUUID();
    }

    @PreUpdate
    private void rejectUpdate() {
        throw new IllegalStateException("Audit events are append-only");
    }

    @PreRemove
    private void rejectRemove() {
        throw new IllegalStateException("Audit events are append-only");
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getOutcome() { return outcome; }
    public String getActorType() { return actorType; }
    public String getActorId() { return actorId; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getRequestId() { return requestId; }
    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
}
