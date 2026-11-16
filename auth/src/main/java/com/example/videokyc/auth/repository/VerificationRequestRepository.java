package com.example.videokyc.auth.repository;

import com.example.videokyc.auth.domain.VerificationRequestEntity;
import com.example.videokyc.auth.domain.VerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequestEntity, UUID> {
    Optional<VerificationRequestEntity> findTopByUser_IdOrderByAttemptNumberDesc(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<VerificationRequestEntity> findByUser_IdOrderByAttemptNumberDesc(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<VerificationRequestEntity> findByStatusOrderBySubmittedAtAsc(VerificationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from VerificationRequestEntity r where r.id = :id")
    Optional<VerificationRequestEntity> lockById(@Param("id") UUID id);
}
