package com.example.videokyc.workflow.domain.version;

import com.example.videokyc.workflow.domain.shared.DomainException;

/**
 * Value object representing a positive, monotonically increasing version number within a definition.
 */
public record VersionNumber(int value) implements Comparable<VersionNumber> {

    public VersionNumber {
        if (value <= 0) {
            throw new DomainException("INVALID_VERSION_NUMBER", "Version number must be greater than zero: " + value);
        }
    }

    public static VersionNumber of(int number) {
        return new VersionNumber(number);
    }

    public VersionNumber next() {
        return new VersionNumber(value + 1);
    }

    @Override
    public int compareTo(VersionNumber other) {
        return Integer.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
