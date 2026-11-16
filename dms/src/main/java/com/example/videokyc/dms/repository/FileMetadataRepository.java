package com.example.videokyc.dms.repository;

import com.example.videokyc.dms.domain.FileMetadataEntity;
import com.example.videokyc.dms.domain.FileOwnerType;
import com.example.videokyc.dms.domain.FileStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadataEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FileMetadataEntity f where f.id = :id")
    Optional<FileMetadataEntity> findForUpdate(@Param("id") UUID id);

    Optional<FileMetadataEntity> findByIdAndStatus(UUID id, FileStatus status);
    Page<FileMetadataEntity> findBySubsystemKeyAndOwnerTypeAndOwnerReferenceAndStatus(
        String subsystemKey, FileOwnerType ownerType, String ownerReference,
        FileStatus status, Pageable pageable);
}
