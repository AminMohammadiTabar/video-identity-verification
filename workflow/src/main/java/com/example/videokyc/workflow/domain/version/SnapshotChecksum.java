package com.example.videokyc.workflow.domain.version;

import com.example.videokyc.workflow.domain.shared.DomainException;

import java.util.regex.Pattern;

/**
 * Value object representing a 64-character lowercase hexadecimal SHA-256 checksum.
 */
public record SnapshotChecksum(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[a-f0-9]{64}$");

    public SnapshotChecksum {
        if (value == null || value.isBlank()) {
            throw new DomainException("INVALID_SNAPSHOT_CHECKSUM", "Snapshot checksum must not be null or blank");
        }
        String trimmed = value.trim().toLowerCase();
        if (!PATTERN.matcher(trimmed).matches()) {
            throw new DomainException("INVALID_SNAPSHOT_CHECKSUM_FORMAT",
                    "Checksum must be a 64-character lowercase hex SHA-256 string: " + trimmed);
        }
        value = trimmed;
    }

    public static SnapshotChecksum of(String checksum) {
        return new SnapshotChecksum(checksum);
    }

    @Override
    public String toString() {
        return value;
    }
}
