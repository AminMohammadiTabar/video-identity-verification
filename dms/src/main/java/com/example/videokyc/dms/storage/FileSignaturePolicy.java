package com.example.videokyc.dms.storage;

import com.example.videokyc.dms.service.DmsException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates binary file signatures (magic bytes) to prevent extension spoofing.
 * <p>
 * Supported formats according to project specification:
 * <ul>
 *   <li>PDF: starts with ASCII {@code %PDF-}</li>
 *   <li>PNG: starts with {@code 0x89 50 4E 47 0D 0A 1A 0A}</li>
 *   <li>JPEG: starts with {@code 0xFF D8 FF}</li>
 *   <li>MP4: contains {@code ftyp} at offset 4</li>
 *   <li>WebM: starts with EBML ID {@code 0x1A 45 DF A3}</li>
 * </ul>
 */
@Component
public class FileSignaturePolicy {

    private static final byte[] PDF_MAGIC = new byte[]{'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FTYP_MAGIC = new byte[]{'f', 't', 'y', 'p'};
    private static final byte[] WEBM_MAGIC = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    public record DetectedType(String contentType, String extension) {}

    /**
     * Inspects the file header and returns verified media type metadata.
     * Throws {@link HttpStatus#UNSUPPORTED_MEDIA_TYPE} if magic bytes do not match supported formats.
     *
     * @param header initial bytes of the file (at least 4 bytes required)
     * @param originalFilename client-supplied original filename
     * @param declaredContentType client-supplied MIME content type
     * @return validated DetectedType with canonical MIME type and safe extension
     */
    public DetectedType validate(byte[] header, String originalFilename, String declaredContentType) {
        if (header == null || header.length < 4) {
            throw new DmsException(HttpStatus.BAD_REQUEST, "File is too short or empty");
        }

        DetectedType detected = detect(header);
        if (detected == null) {
            throw new DmsException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file format or invalid file signature");
        }

        return detected;
    }

    /**
     * Detects media type from header bytes without throwing exceptions.
     */
    public DetectedType detect(byte[] header) {
        if (header == null || header.length < 4) {
            return null;
        }

        if (startsWith(header, PDF_MAGIC)) {
            return new DetectedType("application/pdf", "pdf");
        }
        if (startsWith(header, PNG_MAGIC)) {
            return new DetectedType("image/png", "png");
        }
        if (startsWith(header, JPEG_MAGIC)) {
            return new DetectedType("image/jpeg", "jpg");
        }
        if (isMp4(header)) {
            return new DetectedType("video/mp4", "mp4");
        }
        if (startsWith(header, WEBM_MAGIC)) {
            return new DetectedType("video/webm", "webm");
        }

        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static boolean isMp4(byte[] data) {
        if (data.length < 8) return false;
        return data[4] == FTYP_MAGIC[0] && data[5] == FTYP_MAGIC[1] &&
               data[6] == FTYP_MAGIC[2] && data[7] == FTYP_MAGIC[3];
    }
}
