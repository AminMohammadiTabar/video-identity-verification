package com.example.videokyc.auth.web;

import com.example.videokyc.auth.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/admin/verifications")
@SecurityRequirement(name = "sessionCookie")
@Tag(name = "Admin video reviews", description = "ROLE_ADMIN only; protected behind session + CSRF")
public class ReviewApiController {
    private final VerificationService verifications;
    public ReviewApiController(VerificationService verifications) { this.verifications = verifications; }

    @Operation(summary = "List pending verification requests")
    @GetMapping("/pending")
    public List<VerificationService.AdminVerificationView> pending() {
        return verifications.pending();
    }

    @Operation(summary = "Approve or reject a pending request", description = "Rejection requires a reason. "
            + "Concurrent second decisions return 409. Requires session and CSRF.")
    @PostMapping("/{id}/decision")
    public VerificationService.VerificationView decide(@PathVariable UUID id,
                                     @RequestBody DecisionRequest request, Authentication auth) {
        return verifications.decide(id, VerificationApiController.requiredUser(auth),
                request.approved(), request.reason());
    }

    @Operation(summary = "Watch a submitted video", description = "Authenticated admin only; "
            + "downloaded from DMS on the server and never cached")
    @ApiResponse(responseCode = "200", description = "Private video bytes",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/{id}/video")
    public ResponseEntity<byte[]> video(@PathVariable UUID id) {
        var video = verifications.adminVideo(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(video.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(video.bytes());
    }

    public record DecisionRequest(boolean approved, String reason) { }
}
