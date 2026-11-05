package com.example.videokyc.dms.api;

import com.example.videokyc.dms.domain.FileOwnerType;
import com.example.videokyc.dms.service.DmsFileService;
import com.example.videokyc.dms.service.FileMetadataResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dms/files")
@Tag(name = "DMS Files", description = "Secure file storage and lifecycle management")
public class DmsFileController {
    private final DmsFileService files;

    public DmsFileController(DmsFileService files) { this.files = files; }

    @Operation(summary = "Upload a file", description = "Writes the file to the local storage root under "
            + "SUBSYSTEM/YYYY/MM/DD/OWNER/UUID.ext. Supports PDF, PNG, JPEG, MP4, WebM (50 MB maximum).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Metadata for the newly stored file"),
        @ApiResponse(responseCode = "400", description = "Invalid owner or subsystem"),
        @ApiResponse(responseCode = "401", description = "DMS credentials required"),
        @ApiResponse(responseCode = "413", description = "File too large"),
        @ApiResponse(responseCode = "415", description = "Unsupported or forged file type")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileMetadataResponse> upload(
            @Parameter(description = "Registered subsystem key (e.g. AUTH)") @RequestParam("subsystem") String subsystem,
            @Parameter(description = "USER or SYSTEM") @RequestParam("ownerType") FileOwnerType ownerType,
            @Parameter(description = "User UUID or SYSTEM-AUTH-REGISTER") @RequestParam("ownerReference") String ownerReference,
            @RequestPart("file") MultipartFile file) {
        FileMetadataResponse response = files.upload(subsystem, ownerType, ownerReference, file);
        return ResponseEntity.created(URI.create("/api/v1/dms/files/" + response.id())).body(response);
    }

    @Operation(summary = "Read file metadata", description = "Does not expose the OS storage path")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "File metadata"),
                   @ApiResponse(responseCode = "404", description = "Unknown file")})
    @GetMapping("/{id}")
    public FileMetadataResponse metadata(@PathVariable UUID id) { return files.metadata(id); }

    @Operation(summary = "Download active file", description = "Streams binary data and sets Content-Disposition attachment")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Binary file attachment; actual Content-Type matches the file",
                content = @Content(mediaType = "application/octet-stream",
                        schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(responseCode = "401", description = "HTTP Basic authentication required"),
        @ApiResponse(responseCode = "404", description = "Unknown, deleted or purged file")
    })
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        var file = files.download(id);
        var disposition = ContentDisposition.attachment()
                .filename(file.originalFilename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }

    @Operation(summary = "Logical delete", description = "Marks the file DELETED without removing bytes from disk")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Logically deleted"),
                   @ApiResponse(responseCode = "404", description = "Unknown file")})
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(@PathVariable UUID id) {
        files.logicalDelete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Permanently purge", description = "Physically removes a previously soft-deleted file. "
            + "Metadata is retained for audit purposes.")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Purged"),
                   @ApiResponse(responseCode = "409", description = "Must logically delete first"),
                   @ApiResponse(responseCode = "404", description = "Unknown file")})
    @PostMapping("/{id}/purge")
    public ResponseEntity<Void> purge(@PathVariable UUID id) {
        files.purge(id);
        return ResponseEntity.noContent().build();
    }
}
