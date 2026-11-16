package com.example.videokyc.workflow.observability.audit;

import org.springframework.data.repository.Repository;

import java.util.UUID;

/** Deliberately exposes insert only; no application-level update/delete operations. */
public interface AuditEventRepository extends Repository<AuditEventEntity, UUID> {
    <S extends AuditEventEntity> S save(S event);
    long countByEventType(String eventType);
}
