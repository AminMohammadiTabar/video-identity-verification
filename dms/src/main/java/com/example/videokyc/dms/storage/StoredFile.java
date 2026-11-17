package com.example.videokyc.dms.storage;

/**
 * Immutable metadata record representing a securely persisted file on local disk.
 *
 * @param originalFilename client-reported original file name (for download headers)
 * @param storedFilename unique generated filename on disk (UUID + validated extension)
 * @param relativePath canonical path relative to storage root (e.g. {@code SUBSYSTEM/YYYY/MM/DD/OWNER/UUID.ext})
 * @param contentType verified MIME type determined from file magic bytes
 * @param sizeBytes exact byte length of the stored file
 * @param sha256 lowercase hexadecimal SHA-256 digest of file content
 */
public record StoredFile(
        String originalFilename,
        String storedFilename,
        String relativePath,
        String contentType,
        long sizeBytes,
        String sha256
) {}
