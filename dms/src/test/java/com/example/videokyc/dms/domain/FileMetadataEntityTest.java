package com.example.videokyc.dms.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileMetadataEntityTest {
    @Test
    void supportsSystemOwnerForRegistration() {
        var file = new FileMetadataEntity("AUTH", FileOwnerType.SYSTEM,
            "SYSTEM-AUTH-REGISTER", null, "video.webm", "f2c0.webm",
            "AUTH/2026/11/04/SYSTEM-AUTH-REGISTER/f2c0.webm",
            "video/webm", 123L, null);
        assertEquals(FileOwnerType.SYSTEM, file.getOwnerType());
        assertEquals("SYSTEM-AUTH-REGISTER", file.getOwnerReference());
        assertEquals(FileStatus.ACTIVE, file.getStatus());
    }

    @Test
    void rejectsNegativeFileSize() {
        assertThrows(IllegalArgumentException.class, () -> new FileMetadataEntity(
            "AUTH", FileOwnerType.USER, "user-1", null,
            "a.webm", "b.webm", "AUTH/2026/11/04/user-1/b.webm", "video/webm", -1L, null));
    }
}
