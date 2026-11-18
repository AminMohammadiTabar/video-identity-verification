package com.example.videokyc.workflow.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping for {@code workflow.workflow_outbox_events}.
 */
@Entity
@Table(schema = "workflow", name = "workflow_outbox_events")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private short eventVersion;

    @Column(name = "routing_key", nullable = false, length = 200)
    private String routingKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "headers_json", columnDefinition = "TEXT")
    private String headersJson;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "causation_id")
    private UUID causationId;

    @Column(name = "traceparent", length = 128)
    private String traceparent;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "locked_by", length = 128)
    private String lockedBy;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    public OutboxEventEntity(UUID eventId,
                            String aggregateType,
                            Long aggregateId,
                            String eventType,
                            short eventVersion,
                            String routingKey,
                            String payloadJson,
                            String headersJson,
                            UUID correlationId,
                            UUID causationId,
                            String traceparent,
                            String status,
                            int attemptCount,
                            Instant nextAttemptAt,
                            String lockedBy,
                            Instant lockedUntil,
                            Instant createdAt,
                            Instant publishedAt,
                            String lastErrorCode,
                            String lastErrorMessage) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.routingKey = routingKey;
        this.payloadJson = payloadJson;
        this.headersJson = headersJson;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.traceparent = traceparent;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.lockedBy = lockedBy;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
    }
}
