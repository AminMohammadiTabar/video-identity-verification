package com.example.videokyc.dms.service;

import com.example.videokyc.dms.domain.FileMetadataEntity;
import com.example.videokyc.dms.domain.FileOwnerType;
import com.example.videokyc.dms.domain.FileStatus;
import com.example.videokyc.dms.repository.FileMetadataRepository;
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

@Service
public class DmsFileService {
    private static final Logger log = LoggerFactory.getLogger(DmsFileService.class);
    private static final Pattern SUBSYSTEM = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");
    private static final Pattern SYSTEM_OWNER = Pattern.compile("SYSTEM-[A-Z0-9-]{1,121}");

    private final FileMetadataRepository repository;
    private final LocalDiskStorage disk;
    private final TransactionTemplate transactions;

    public DmsFileService(FileMetadataRepository repository, LocalDiskStorage disk,
                          TransactionTemplate transactions) {
        this.repository = repository;
        this.disk = disk;
        this.transactions = transactions;
    }

    public FileMetadataResponse upload(String subsystemInput, FileOwnerType ownerType,
                                       String ownerInput, MultipartFile file) {
        String subsystem = validateSubsystem(subsystemInput);
        String owner = validateOwner(ownerType, ownerInput);
        com.example.videokyc.dms.storage.StoredFile stored;
        try {
            stored = disk.store(file, subsystem, ownerType, owner);
        } catch (RuntimeException ex) {
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
                return FileMetadataResponse.from(repository.saveAndFlush(metadata));
            });
            log.atInfo().addKeyValue("event", "dms.action").addKeyValue("action", "UPLOAD")
                    .addKeyValue("outcome", "SUCCESS").addKeyValue("fileId", created.id().toString())
                    .addKeyValue("subsystem", subsystem).addKeyValue("ownerType", ownerType.name())
                    .addKeyValue("bytes", stored.sizeBytes()).log("DMS file created");
            return created;
        } catch (RuntimeException ex) {
            disk.compensateFailedUpload(stored.relativePath());
            log.atWarn().addKeyValue("event", "dms.action").addKeyValue("action", "UPLOAD")
                    .addKeyValue("outcome", "FAILED").addKeyValue("failureType", ex.getClass().getSimpleName())
                    .log("DMS upload failed");
            throw ex;
        }
    }

    public FileMetadataResponse metadata(UUID id) {
        FileMetadataResponse response = FileMetadataResponse.from(find(id));
        log.atInfo().addKeyValue("event", "dms.action").addKeyValue("action", "READ_METADATA")
                .addKeyValue("outcome", "SUCCESS").addKeyValue("fileId", id.toString())
                .log("DMS metadata read");
        return response;
    }

    public DownloadableFile download(UUID id) {
        FileMetadataEntity entity = find(id);
        if (entity.getStatus() != FileStatus.ACTIVE) {
            throw new DmsException(HttpStatus.NOT_FOUND, "File content is unavailable");
        }
        Path path = disk.pathForDownload(entity.getRelativePath());
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
            if (entity.getStatus() == FileStatus.ACTIVE) entity.markDeleted();
            else if (entity.getStatus() != FileStatus.DELETED)
                throw new DmsException(HttpStatus.CONFLICT, "File cannot be logically deleted in this state");
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
            if (entity.getStatus() == FileStatus.DELETED) entity.markPurgePending();
            else if (entity.getStatus() != FileStatus.PURGE_PENDING)
                throw new DmsException(HttpStatus.CONFLICT, "Logical deletion is required before purge");
            return entity.getRelativePath();
        });
        if (path == null) return;
        // If deletion or the following transaction fails, PURGE_PENDING is safe to retry.
        disk.purge(path);
        transactions.executeWithoutResult(tx -> {
            var entity = repository.findForUpdate(id)
                    .orElseThrow(() -> new DmsException(HttpStatus.NOT_FOUND, "File not found"));
            if (entity.getStatus() == FileStatus.PURGE_PENDING) entity.markPurged();
            else if (entity.getStatus() != FileStatus.PURGED)
                throw new DmsException(HttpStatus.CONFLICT, "Concurrent state change during purge");
        });
        log.atInfo().addKeyValue("event", "dms.action").addKeyValue("action", "PHYSICAL_PURGE")
                .addKeyValue("outcome", "SUCCESS").addKeyValue("fileId", id.toString())
                .log("DMS physical purge completed");
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
