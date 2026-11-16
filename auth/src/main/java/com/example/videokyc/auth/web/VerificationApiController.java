package com.example.videokyc.auth.web;

import com.example.videokyc.auth.observability.AuthActionAudit;
import com.example.videokyc.auth.service.VerificationException;
import com.example.videokyc.auth.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth/verifications")
@SecurityRequirement(name = "sessionCookie")
@Tag(name = "Video verification", description = "Authenticated user's verification attempts and history")
public class VerificationApiController {
    private final VerificationService verifications;
    public VerificationApiController(VerificationService verifications) { this.verifications = verifications; }

    @Operation(summary = "My latest verification status")
    @GetMapping("/me")
    public VerificationService.VerificationView mine(Authentication auth) {
        return verifications.mine(requiredUser(auth));
    }

    @Operation(summary = "My complete verification attempt history", description = "Newest attempt first; rejected attempts remain immutable history.")
    @GetMapping("/history")
    public List<VerificationService.VerificationView> history(Authentication auth) {
        return verifications.history(requiredUser(auth));
    }

    @Operation(summary = "Submit a verification video", description = "Max 50 MiB; only MP4 or WebM. A new attempt is created after a rejection. A transient DMS failure can retry the same attempt.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Submitted for admin review"),
            @ApiResponse(responseCode = "409", description = "An active or approved attempt already exists"),
            @ApiResponse(responseCode = "502", description = "DMS storage error")})
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VerificationService.VerificationView> submit(Authentication auth,
                            @RequestPart("video") MultipartFile video) {
        return ResponseEntity.status(HttpStatus.CREATED).body(verifications.submit(requiredUser(auth), video));
    }

    static java.util.UUID requiredUser(Authentication authentication) {
        var user = AuthActionAudit.userId(authentication);
        if (user == null) throw new VerificationException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return user;
    }
}
