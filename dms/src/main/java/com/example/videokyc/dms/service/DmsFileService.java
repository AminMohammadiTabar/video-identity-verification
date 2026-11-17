package com.example.videokyc.dms.service;

import com.example.videokyc.dms.domain.FileMetadataEntity;
import com.example.videokyc.dms.domain.FileOwnerType;
import com.example.videokyc.dms.domain.FileStatus;
import com.example.videokyc.dms.repository.FileMetadataRepository;
import com.example.videokyc.dms.observability.audit.AuditEventCommand;
import com.example.videokyc.dms.observability.audit.AuditEventRecorder;
import com.example.videokyc.dms.observability.audit.DmsAuditActorProvider;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import com.example.videokyc.dms.storage.LocalDiskStorage;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Core business service managing document and video files in DMS.
 * <p>
 * Key responsibilities and safety invariants:
 * <ul>
 *   <li><b>Upload Flow:</b> Validates subsystem and owner reference, verifies binary signatures via
 *       {@link LocalDiskStorage}, stores physical file, and commits database metadata with compensation
 *       on transaction rollback.</li>
 *   <li><b>Download Flow:</b> Streams file content only if the file status is {@code ACTIVE}.</li>
 *   <li><b>Logical Deletion:</b> Transitions file from {@code ACTIVE} to {@code DELETED} without deleting physical bytes immediately.</li>
 *   <li><b>Two-Phase Physical Purge:</b> Transitions {@code DELETED} to {@code PURGE_PENDING}, physically removes
 *       bytes from disk, and commits final {@code PURGED} status.</li>
 *   <li><b>Audit Integration:</b> Synchronously records audit events for uploads, downloads, logical deletes, and purges.</li>
 * </ul>
 */
@Service
public class DmsFileService {
    private static final Logger log = LoggerFactory.getLogger(DmsFileService.class);
    private static final Pattern SUBSYSTEM = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");
    private static final Pattern SYSTEM_OWNER = Pattern.compile("SYSTEM-[A-Z0-9-]{1,121}");

    private final FileMetadataRepository repository;
    private final LocalDiskStorage disk;
    private final TransactionTemplate transactions;
    private final AuditEventRecorder audit;
    private final DmsAuditActorProvider actors;
    private final ObservationRegistry observations;

    public DmsFileService(FileMetadataRepository repository, LocalDiskStorage disk,
                          TransactionTemplate transactions, AuditEventRecorder audit,
                          DmsAuditActorProvider actors, ObservationRegistry observations) {
        this.repository = repository;
        this.disk = disk;
        this.transactions = transactions;
        this.audit = audit;
        this.actors = actors;
        this.observations = observations;
    }

    public FileMetadataResponse upload(String subsystemInput, FileOwnerType ownerType,
                                       String ownerInput, MultipartFile file) {
        String subsystem;
        String owner;
        com.example.videokyc.dms.storage.StoredFile stored;
        try {
            subsystem = validateSubsystem(subsystemInput);
            owner = validateOwner(ownerType, ownerInput);
            stored = Observation.createNotStarted("dms.file.upload", observations)
                    .lowCardinalityKeyValue("subsystem", subsystem)
                    .observe(() -> disk.store(file, subsystem, ownerType, owner));
        } catch (RuntimeException ex) {
            recordFailure("dms.file.upload.failed", null, "UPLOAD_FAILED", ex);
            log.atWarn().addKeyValue("event", "dms.action").addKeyValue("action", "UPLOAD")
                    .addKeyValue("outcome", "FAILED").addKeyValue("failureType", ex.getClass().getSimpleName())
                    .log("DMS file write failed");
            throw ex;
        }
        try {
            FileMetadataResponse created = transactions.execute(tx -> {
                var metadata = new FileMetadataEntity(subsystem, ownerType, owner,
                        null, stored.originalFilename(), stored.storedFilename(),
                        stored.relativePath(), stored.contentType(), stored.sizeBytes(), stored.sha256());
                var saved = repository.saveAndFlush(metadata);
                record("dms.file.uploaded", ownerType == FileOwnerType.USER ? "USER" : null,
                        ownerType == FileOwnerType.USER ? owner : null, saved.getId().toString(),
                        "SUCCESS", null, null, saved.getStatus().name(), java.util.Map.of(
                                "subsystem", subsystem, "ownerType", ownerType.name(),
                                "sizeBytes", stored.sizeBytes(), "checksumSha256", stored.sha256()));
                return FileMetadataResponse.from(saved);
            });
            log.atInfo().addKeyValue("event", "dms.action").addKeyValue("action", "UPLOAD")
                    .addKeyValue("outcome", "SUCCESS").addKeyValue("fileId", created.id().toString())
                    .addKeyValue("subsystem", subsystem).addKeyValue("ownerType", ownerType.name())
                    .addKeyValue("bytes", stored.sizeBytes()).log("DMS file created");
            return created;
        } catch (RuntimeException ex) {
            disk.compensateFailedUpload(stored.relativePath());
            recordFailure("dms.file.upload.failed", null, "METADATA_PERSISTENCE_FAILED", ex);
            log.atWarn().addKeyValue("event", "dms.action").addKeyValue("action", "UPLOAD")
                    .addKeyValue("outcome", "FAILED").addKeyValue("failureType", ex.getClass().getSimpleName())
                    .log("DMS upload failed");
            throw ex;
        }
    }

    public FileMetadataResponse metadata(UUID id) {
        return metadata(id, true);
    }

    /** Metadata lookup for authorization decisions; the user-facing read is audited after authorization. */
    public FileMetadataResponse metadataForAuthorization(UUID id) {
        return metadata(id, false);
    }

    private FileMetadataResponse metadata(UUID id, boolean auditRead) {
        FileMetadataEntity entity = find(id);
        FileMetadataResponse response = FileMetadataResponse.from(entity);
        if (auditRead) record("dms.file.metadata.read", entity.getOwnerType() == FileOwnerType.USER ? "USER" : null,
                entity.getOwnerType() == FileOwnerType.USER ? entity.getOwnerReference() : null,
                id.toString(), "SUCCESS", null, null, null, java.util.Map.of());
        log.atInfo().addKeyValue("event", "dms.action").addKeyValue("action", "READ_METADATA")
                .addKeyValue("outcome", "SUCCESS").addKeyValue("fileId", id.toString())
                .log("DMS metadata read");
        return response;
    }

    public DownloadableFile download(UUID id) {
        FileMetadataEntity entity = find(id);
        if (entity.getStatus() != FileStatus.ACTIVE) {
            recordFailure("dms.file.download.failed", id.toString(), "FILE_NOT_ACTIVE", null);
            throw new DmsException(HttpStatus.NOT_FOUND, "File content is unavailable");
        }
        Path path;
        try {
            path = Observation.createNotStarted("dms.file.download", observations)
                    .lowCardinalityKeyValue("outcome", "allowed")
                    .observe(() -> disk.pathForDownload(entity.getRelativePath()));
        } catch (RuntimeException failure) {
            recordFailure("dms.file.download.failed", id.toString(), "STORAGE_READ_FAILED", failure);
            throw failure;
        }
        record("dms.file.download.started", entity.getOwnerType() == FileOwnerType.USER ? "USER" : null,
                entity.getOwnerType() == FileOwnerType.USER ? entity.getOwnerReference() : null,
                id.toString(), "SUCCESS", null, entity.getStatus().name(), entity.getStatus().name(),
                java.util.Map.of("sizeBytes", entity.getSizeBytes()));
        log.atInfo().addKeyValue("event", "dms.action").addKeyValue("action", "DOWNLOAD_REQUESTED")
                .addKeyValue("outcome", "ALLOWED").addKeyValue("fileId", id.toString())
                .addKeyValue("bytes", entity.getSizeBytes()).log("DMS download authorized");
        return new DownloadableFile(path, entity.getOriginalFilename(),
                entity.getContentType(), entity.getSizeBytes());
    }

    public void logicalDelete(UUID id) {
        transactions.executeWithoutResult(tx -> {
            var entity = repository.findForUpdate(id)
                    .orElseThrow(() -> new DmsException(HttpStatus.NOT_FOUND, "File not found"));
            if (entity.getStatus() == FileStatus.ACTIVE) {
                String previous = entity.getStatus().name();
                entity.markDeleted();
                record("dms.file.logical_deleted", entity.getOwnerType() == FileOwnerType.USER ? "USER" : null,
                        entity.getOwnerType() == FileOwnerType.USER ? entity.getOwnerReference() : null,
                        id.toString(), "SUCCESS", null, previous, entity.getStatus().name(), java.util.Map.of());
            } else if (entity.getStatus() != FileStatus.DELETED) {
                recordFailure("dms.file.logical_delete.failed", id.toString(), "INVALID_FILE_STATE", null);
                throw new DmsException(HttpStatus.CONFLICT, "File cannot be logically deleted in this state");
            }
        });
        log.atInfo().addKeyValue("event", "dms.action").addKeyValue("action", "LOGICAL_DELETE")
                .addKeyValue("outcome", "SUCCESS").addKeyValue("fileId", id.toString())
                .log("DMS logical deletion completed");
    }

    public void purge(UUID id) {
        String path = transactions.execute(tx -> {
            var entity = repository.findForUpdate(id)
                    .orElseThrow(() -> new DmsException(HttpStatus.NOT_FOUND, "File not found"));
            if (entity.getStatus() == FileStatus.PURGED) return null;
            if (entity.getStatus() == FileStatus.DELETED) {
                String previous = entity.getStatus().name();
                entity.markPurgePending();
                record("dms.file.purge.started", entity.getOwnerType() == FileOwnerType.USER ? "USER" : null,
                        entity.getOwnerType() == FileOwnerType.USER ? entity.getOwnerReference() : null,
                        id.toString(), "SUCCESS", null, previous, entity.getStatus().name(), java.util.Map.of());
            } else if (entity.getStatus() != FileStatus.PURGE_PENDING) {
                recordFailure("dms.file.purge.failed", id.toString(), "LOGICAL_DELETE_REQUIRED", null);
                throw new DmsException(HttpStatus.CONFLICT, "Logical deletion is required before purge");
            }
            return entity.getRelativePath();
        });
        if (path == null) return;
        // If deletion or the following transaction fails, PURGE_PENDING is safe to retry.
        try {
            Observation.createNotStarted("dms.file.purge", observations)
                    .lowCardinalityKeyValue("file.state", "PURGE_PENDING")
                    .observe(() -> {
                        disk.purge(path);
                        return Boolean.TRUE;
                    });
        } catch (RuntimeException failure) {
            recordFailure("dms.file.purge.failed", id.toString(), "STORAGE_PURGE_FAILED", failure);
            throw failure;
        }
        try {
            transactions.executeWithoutResult(tx -> {
                var entity = repository.findForUpdate(id)
                        .orElseThrow(() -> new DmsException(HttpStatus.NOT_FOUND, "File not found"));
                if (entity.getStatus() == FileStatus.PURGE_PENDING) {
                    String previous = entity.getStatus().name();
                    entity.markPurged();
                    record("dms.file.purged", entity.getOwnerType() == FileOwnerType.USER ? "USER" : null,
                            entity.getOwnerType() == FileOwnerType.USER ? entity.getOwnerReference() : null,
                            id.toString(), "SUCCESS", null, previous, entity.getStatus().name(), java.util.Map.of());
                } else if (entity.getStatus() != FileStatus.PURGED) {
                    throw new DmsException(HttpStatus.CONFLICT, "Concurrent state change during purge");
                }
            });
        } catch (RuntimeException failure) {
            recordFailure("dms.file.purge.failed", id.toString(), "METADATA_FINALIZATION_FAILED", failure);
            throw failure;
        }
        log.atInfo().addKeyValue("event", "dms.action").addKeyValue("action", "PHYSICAL_PURGE")
                .addKeyValue("outcome", "SUCCESS").addKeyValue("fileId", id.toString())
                .log("DMS physical purge completed");
    }

    private void record(String eventType, String fileId, String outcome, String reasonCode,
                        String previousState, String newState, java.util.Map<String, Object> details) {
        record(eventType, null, null, fileId, outcome, reasonCode, previousState, newState, details);
    }

    private void record(String eventType, String subjectType, String subjectId, String fileId,
                        String outcome, String reasonCode, String previousState, String newState,
                        java.util.Map<String, Object> details) {
        var actor = actors.current();
        audit.record(new AuditEventCommand(eventType, actor.type(), actor.id(), subjectType, subjectId,
                "DMS_FILE", fileId, outcome, reasonCode, previousState, newState, details));
    }

    private void recordFailure(String eventType, String fileId, String reasonCode, RuntimeException failure) {
        var actor = actors.current();
        audit.recordIndependent(AuditEventCommand.of(eventType, actor.type(), actor.id(), null, null,
                "DMS_FILE", fileId, "FAILURE", reasonCode, null, null)
                .withDetails(failure == null ? java.util.Map.of() : java.util.Map.of(
                        "failureType", failure.getClass().getSimpleName())));
    }

    private FileMetadataEntity find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new DmsException(HttpStatus.NOT_FOUND, "File not found"));
    }

    private String validateSubsystem(String value) {
        if (value == null) throw new DmsException(HttpStatus.BAD_REQUEST, "Subsystem is required");
        String result = value.strip().toUpperCase(Locale.ROOT);
        if (!SUBSYSTEM.matcher(result).matches())
            throw new DmsException(HttpStatus.BAD_REQUEST, "Invalid subsystem identifier");
        return result;
    }

    private String validateOwner(FileOwnerType type, String value) {
        if (type == null || value == null || value.isBlank())
            throw new DmsException(HttpStatus.BAD_REQUEST, "Owner type and reference are required");
        if (type == FileOwnerType.USER) {
            try { return UUID.fromString(value.strip()).toString(); }
            catch (IllegalArgumentException ex) {
                throw new DmsException(HttpStatus.BAD_REQUEST, "USER owner must be a UUID");
            }
        }
        String system = value.strip().toUpperCase(Locale.ROOT);
        if (!SYSTEM_OWNER.matcher(system).matches())
            throw new DmsException(HttpStatus.BAD_REQUEST, "Invalid SYSTEM owner reference");
        return system;
    }

    public record DownloadableFile(Path path, String originalFilename, String contentType, long size) { }
}
