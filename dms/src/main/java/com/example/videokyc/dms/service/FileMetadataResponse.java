package com.example.videokyc.dms.service;

import com.example.videokyc.dms.domain.FileMetadataEntity;
import com.example.videokyc.dms.domain.FileOwnerType;
import com.example.videokyc.dms.domain.FileStatus;

import java.time.Instant;
import java.util.UUID;

/** Never expose an OS path through the public metadata API. */
public record FileMetadataResponse(UUID id, String subsystem, FileOwnerType ownerType,
                                   String ownerReference, String originalFilename,
                                   String contentType, long sizeBytes, String checksumSha256,
                                   FileStatus status, Instant createdAt, Instant deletedAt,
                                   Instant purgedAt) {
    public static FileMetadataResponse from(FileMetadataEntity entity) {
        return new FileMetadataResponse(entity.getId(), entity.getSubsystemKey(),
                entity.getOwnerType(), entity.getOwnerReference(), entity.getOriginalFilename(),
                entity.getContentType(), entity.getSizeBytes(), entity.getChecksumSha256(),
                entity.getStatus(), entity.getCreatedAt(), entity.getDeletedAt(), entity.getPurgedAt());
    }
}
