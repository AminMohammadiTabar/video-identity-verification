package com.example.videokyc.workflow.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublicationJobRepository extends JpaRepository<PublicationJobEntity, Long> {

    Optional<PublicationJobEntity> findByVersionId(Long versionId);

    List<PublicationJobEntity> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses, Instant cutoff);

    List<PublicationJobEntity> findByStatusAndLockedUntilLessThan(String status, Instant cutoff);
}
