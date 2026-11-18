package com.example.videokyc.workflow.adapter.out.persistence.repository;

import com.example.videokyc.workflow.adapter.out.persistence.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    Optional<OutboxEventEntity> findByEventId(UUID eventId);

    boolean existsByEventId(UUID eventId);

    List<OutboxEventEntity> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses, Instant cutoff);

    List<OutboxEventEntity> findByStatusAndLockedUntilLessThan(String status, Instant cutoff);

    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            String aggregateType, Long aggregateId);
}
