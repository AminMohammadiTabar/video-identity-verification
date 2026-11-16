package com.example.videokyc.dms.repository;

import com.example.videokyc.dms.domain.FileMetadataEntity;
import com.example.videokyc.dms.domain.FileOwnerType;
import com.example.videokyc.dms.domain.FileStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class DmsRepositoryIntegrationTest {
    @Autowired FileMetadataRepository files;

    @Test
    void systemOwnedFileMetadataCanBeSearched() {
        String name = UUID.randomUUID() + ".webm";
        var file = new FileMetadataEntity("AUTH", FileOwnerType.SYSTEM,
            "SYSTEM-AUTH-REGISTER", null, "registration.webm", name,
            "AUTH/2026/11/04/SYSTEM-AUTH-REGISTER/" + name,
            "video/webm", 2048, null);
        files.saveAndFlush(file);
        assertTrue(files.findByIdAndStatus(file.getId(), FileStatus.ACTIVE).isPresent());
        assertFalse(files.findBySubsystemKeyAndOwnerTypeAndOwnerReferenceAndStatus(
            "AUTH", FileOwnerType.SYSTEM, "SYSTEM-AUTH-REGISTER",
            FileStatus.ACTIVE, PageRequest.of(0, 100)).isEmpty());
    }
}
