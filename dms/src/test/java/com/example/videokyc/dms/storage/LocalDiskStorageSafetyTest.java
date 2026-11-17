package com.example.videokyc.dms.storage;

import com.example.videokyc.dms.domain.FileOwnerType;
import com.example.videokyc.dms.service.DmsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and safety tests for {@link LocalDiskStorage} and {@link FileSignaturePolicy}.
 * <p>
 * Verifies path layout, magic byte verification, path traversal protection,
 * failure compensation, and physical purge capabilities.
 */
class LocalDiskStorageSafetyTest {

    @TempDir
    Path tempStorageRoot;

    private LocalDiskStorage storage;
    private FileSignaturePolicy signaturePolicy;

    @BeforeEach
    void setUp() {
        signaturePolicy = new FileSignaturePolicy();
        storage = new LocalDiskStorage(tempStorageRoot.toString(), signaturePolicy);
    }

    @Test
    void storesValidPdfWithCorrectPathLayoutAndChecksum() throws IOException {
        byte[] pdfContent = "%PDF-1.7\nSample content\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "document.pdf", "application/pdf", pdfContent);
        String ownerUuid = UUID.randomUUID().toString();

        StoredFile stored = storage.store(file, "AUTH", FileOwnerType.USER, ownerUuid);

        assertNotNull(stored);
        assertEquals("document.pdf", stored.originalFilename());
        assertEquals("application/pdf", stored.contentType());
        assertEquals(pdfContent.length, stored.sizeBytes());
        assertTrue(stored.relativePath().startsWith("AUTH/"));
        assertTrue(stored.relativePath().contains("/" + ownerUuid + "/"));
        assertTrue(stored.storedFilename().endsWith(".pdf"));

        Path resolved = storage.pathForDownload(stored.relativePath());
        assertTrue(Files.exists(resolved));
        assertArrayEquals(pdfContent, Files.readAllBytes(resolved));
    }

    @Test
    void storesValidMp4Video() {
        byte[] mp4Header = new byte[]{0, 0, 0, 20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "selfie.mp4", "video/mp4", mp4Header);

        StoredFile stored = storage.store(file, "AUTH", FileOwnerType.USER, UUID.randomUUID().toString());

        assertNotNull(stored);
        assertEquals("video/mp4", stored.contentType());
        assertTrue(stored.storedFilename().endsWith(".mp4"));
    }

    @Test
    void rejectsInvalidFileSignature() {
        byte[] fakeContent = "This is plain text pretending to be a pdf".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", fakeContent);

        DmsException ex = assertThrows(DmsException.class, () ->
                storage.store(file, "AUTH", FileOwnerType.USER, UUID.randomUUID().toString()));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatus());
    }

    @Test
    void rejectsEmptyOrNullFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        DmsException ex = assertThrows(DmsException.class, () ->
                storage.store(emptyFile, "AUTH", FileOwnerType.USER, UUID.randomUUID().toString()));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void rejectsPathTraversal() {
        DmsException ex = assertThrows(DmsException.class, () ->
                storage.pathForDownload("../../secret.txt"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void compensatesFailedUploadByDeletingFile() {
        byte[] pdfContent = "%PDF-1.7\nSample content\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfContent);

        StoredFile stored = storage.store(file, "AUTH", FileOwnerType.USER, UUID.randomUUID().toString());
        Path path = storage.pathForDownload(stored.relativePath());
        assertTrue(Files.exists(path));

        storage.compensateFailedUpload(stored.relativePath());
        assertFalse(Files.exists(path));
    }

    @Test
    void purgesFilePermanently() {
        byte[] pdfContent = "%PDF-1.7\nSample content\n%%EOF".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "to-purge.pdf", "application/pdf", pdfContent);

        StoredFile stored = storage.store(file, "AUTH", FileOwnerType.USER, UUID.randomUUID().toString());
        Path path = storage.pathForDownload(stored.relativePath());
        assertTrue(Files.exists(path));

        storage.purge(stored.relativePath());
        assertFalse(Files.exists(path));
    }
}
