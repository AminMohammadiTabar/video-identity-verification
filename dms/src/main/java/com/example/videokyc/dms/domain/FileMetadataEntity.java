package com.example.videokyc.dms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stores file metadata only. File bytes are managed by the local disk storage implementation. */
@Entity
@Table(schema = "dms", name = "files")
public class FileMetadataEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "subsystem_key", nullable = false, length = 64)
    private String subsystemKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private FileOwnerType ownerType;

    @Column(name = "owner_reference", nullable = false, length = 128)
    private String ownerReference;

    @Column(name = "uploaded_by_user_id")
    private UUID uploadedByUserId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 128)
    private String storedFilename;

    /** Path is relative to configured DMS root: SUBSYSTEM/YYYY/MM/DD/OWNER/UUID.ext. */
    @Column(name = "relative_path", nullable = false, unique = true, length = 1024)
    private String relativePath;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", length = 64, columnDefinition = "char(64)")
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FileStatus status = FileStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "purged_at")
    private Instant purgedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FileMetadataEntity() {
        // JPA
    }

    public FileMetadataEntity(String subsystemKey, FileOwnerType ownerType, String ownerReference,
            UUID uploadedByUserId, String originalFilename, String storedFilename,
            String relativePath, String contentType, long sizeBytes, String checksumSha256) {
        this.subsystemKey = nonBlank(subsystemKey, "subsystemKey");
        this.ownerType = Objects.requireNonNull(ownerType, "ownerType");
        this.ownerReference = nonBlank(ownerReference, "ownerReference");
        this.uploadedByUserId = uploadedByUserId;
        this.originalFilename = nonBlank(originalFilename, "originalFilename");
        this.storedFilename = nonBlank(storedFilename, "storedFilename");
        this.relativePath = nonBlank(relativePath, "relativePath");
        this.contentType = contentType;
        if (sizeBytes < 0) throw new IllegalArgumentException("sizeBytes must be non-negative");
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
    }

    private static String nonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    @PrePersist
    private void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public void markDeleted() {
        if (status != FileStatus.ACTIVE) throw new IllegalStateException("Expected ACTIVE");
        status = FileStatus.DELETED;
        deletedAt = Instant.now();
    }

    public void markPurgePending() {
        if (status != FileStatus.DELETED) throw new IllegalStateException("Expected DELETED");
        status = FileStatus.PURGE_PENDING;
    }

    public void markPurged() {
        if (status != FileStatus.PURGE_PENDING) throw new IllegalStateException("Expected PURGE_PENDING");
        status = FileStatus.PURGED;
        purgedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSubsystemKey() { return subsystemKey; }
    public FileOwnerType getOwnerType() { return ownerType; }
    public String getOwnerReference() { return ownerReference; }
    public UUID getUploadedByUserId() { return uploadedByUserId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public String getRelativePath() { return relativePath; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public FileStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getPurgedAt() { return purgedAt; }
    public long getVersion() { return version; }
}
