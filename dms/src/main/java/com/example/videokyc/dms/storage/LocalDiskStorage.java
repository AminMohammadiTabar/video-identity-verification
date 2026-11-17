package com.example.videokyc.dms.storage;

import com.example.videokyc.dms.domain.FileOwnerType;
import com.example.videokyc.dms.service.DmsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Hardened local filesystem storage implementation for DMS.
 * <p>
 * Enforces strict security invariants:
 * <ul>
 *   <li>Physical file layout: {@code SUBSYSTEM/YYYY/MM/DD/OWNER_ID/UUID.ext}</li>
 *   <li>Magic bytes validation via {@link FileSignaturePolicy} before accepting payload</li>
 *   <li>Path traversal defense by verifying all resolved paths remain strictly beneath the configured root directory</li>
 *   <li>Symlink defense by inspecting the target file and all parent directories up to the filesystem root</li>
 *   <li>Compensation support to clean up partially written or orphaned files upon transaction failure</li>
 *   <li>Streaming SHA-256 calculation without loading the entire payload into heap memory</li>
 * </ul>
 */
@Component
public class LocalDiskStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalDiskStorage.class);
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MiB strict upper bound

    private final Path rootDir;
    private final FileSignaturePolicy signaturePolicy;

    public LocalDiskStorage(@Value("${dms.storage.root:./storage/dms}") String rootPath,
                            FileSignaturePolicy signaturePolicy) {
        this.rootDir = Paths.get(rootPath).toAbsolutePath().normalize();
        this.signaturePolicy = signaturePolicy;
    }

    /**
     * Validates, saves, and computes integrity metadata for an uploaded file.
     *
     * @param file the multipart file payload
     * @param subsystem normalized uppercase subsystem identifier (e.g. "AUTH")
     * @param ownerType owner classification (USER or SYSTEM)
     * @param owner owner identifier (UUID for USER, SYSTEM-* for SYSTEM)
     * @return metadata representing the successfully stored file
     */
    public StoredFile store(MultipartFile file, String subsystem, FileOwnerType ownerType, String owner) {
        if (file == null || file.isEmpty()) {
            throw new DmsException(HttpStatus.BAD_REQUEST, "Uploaded file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new DmsException(HttpStatus.BAD_REQUEST, "File size exceeds the 50MB limit");
        }

        // Inspect magic bytes from the initial 64 bytes without buffering full file in memory
        byte[] header = new byte[64];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read < 4) {
                throw new DmsException(HttpStatus.BAD_REQUEST, "File content is too short");
            }
        } catch (IOException e) {
            throw new DmsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read upload stream", e);
        }

        var detected = signaturePolicy.validate(header, file.getOriginalFilename(), file.getContentType());

        // Standardized path layout: SUBSYSTEM/YYYY/MM/DD/OWNER_ID/UUID.ext
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        String year = String.format("%04d", now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String day = String.format("%02d", now.getDayOfMonth());
        String storedFilename = UUID.randomUUID() + "." + detected.extension();
        String relativePath = subsystem + "/" + year + "/" + month + "/" + day + "/" + owner + "/" + storedFilename;

        Path targetPath = resolveSafe(relativePath);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm unavailable", e);
        }

        try {
            Files.createDirectories(targetPath.getParent());
            try (InputStream in = file.getInputStream();
                 DigestInputStream dis = new DigestInputStream(in, digest);
                 OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                dis.transferTo(out);
            }
        } catch (IOException e) {
            // Delete incomplete artifact if write fails mid-stream
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ignored) {}
            throw new DmsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write file to disk", e);
        }

        String sha256 = HexFormat.of().formatHex(digest.digest());
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : storedFilename;

        return new StoredFile(originalFilename, storedFilename, relativePath, detected.contentType(), file.getSize(), sha256);
    }

    /**
     * Resolves and verifies the physical path for downloading an active file.
     *
     * @param relativePath canonical relative path within storage root
     * @return safe, validated absolute Path to the regular file
     */
    public Path pathForDownload(String relativePath) {
        Path target = resolveSafe(relativePath);
        if (!Files.isRegularFile(target)) {
            throw new DmsException(HttpStatus.NOT_FOUND, "File content not found on disk");
        }
        return target;
    }

    /**
     * Compensates a failed upload by deleting the file from disk if database commit failed.
     *
     * @param relativePath canonical relative path of file to remove
     */
    public void compensateFailedUpload(String relativePath) {
        try {
            Path target = resolveSafe(relativePath);
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.warn("Failed to compensate upload for relative path {}: {}", relativePath, e.getMessage());
        }
    }

    /**
     * Permanently deletes a previously logically deleted file from disk during physical purge.
     *
     * @param relativePath canonical relative path to purge
     */
    public void purge(String relativePath) {
        try {
            Path target = resolveSafe(relativePath);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new DmsException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to physically purge file", e);
        }
    }

    /**
     * Validates that the requested relative path resolves safely inside the storage root
     * and contains no directory traversal elements or symbolic links.
     */
    private Path resolveSafe(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new DmsException(HttpStatus.BAD_REQUEST, "Relative path is required");
        }

        Path resolved = rootDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new DmsException(HttpStatus.BAD_REQUEST, "Invalid path traversal attempt");
        }

        validateNoSymlinks(resolved);
        return resolved;
    }

    /**
     * Traverses upward from the target path to the filesystem root to ensure no component is a symlink.
     * Prevents symlink-based jailbreak attacks even if ancestors of the storage root are targeted.
     */
    private void validateNoSymlinks(Path target) {
        Path current = target;
        while (current != null) {
            if (Files.isSymbolicLink(current)) {
                throw new DmsException(HttpStatus.BAD_REQUEST, "Symbolic links are not allowed in storage paths");
            }
            current = current.getParent();
        }
    }
}
