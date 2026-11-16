package com.example.videokyc.auth.repository;

import com.example.videokyc.auth.domain.EmailOutboxEntity;
import com.example.videokyc.auth.domain.EmailOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailOutboxEntity> findTop10ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            EmailOutboxStatus status, Instant now);

    java.util.List<EmailOutboxEntity> findByVerificationRequestId(java.util.UUID verificationRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<EmailOutboxEntity> findTop10ByStatusAndLockedUntilLessThanEqualOrderByCreatedAtAsc(
            EmailOutboxStatus status, Instant now);
}
