package com.example.videokyc.auth.web;

import com.example.videokyc.auth.repository.UserRepository;
import com.example.videokyc.auth.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication & Registration", description = "REST endpoints for account creation and session inspection")
public class AuthApiController {
    private final RegistrationService registration;
    private final UserRepository users;

    public AuthApiController(RegistrationService registration, UserRepository users) {
        this.registration = registration;
        this.users = users;
    }

    @Operation(summary = "Create a user account", description = "Public, CSRF-protected registration. "
            + "First call GET /api/v1/auth/csrf from the same browser session, "
            + "then include X-CSRF-TOKEN with the POST. Users always receive ROLE_USER.",
            parameters = @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER,
                    required = true, description = "CSRF token bound to the same session cookie"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "403", description = "Missing or invalid CSRF token"),
        @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    @PostMapping("/registrations")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        var created = registration.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegistrationResponse(created.id(), created.email()));
    }

    @Operation(summary = "Retrieve a CSRF token", description = "Use the token in X-CSRF-TOKEN "
            + "for subsequent state-changing requests, keeping the browser session cookie.")
    @ApiResponse(responseCode = "200", description = "CSRF token for the current HTTP session")
    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
        if (token == null) throw new IllegalStateException("CSRF protection is not configured");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @Operation(summary = "Get the authenticated account", description = "Requires the same Spring Security "
            + "session used to sign in through the Mustache login form.")
    @SecurityRequirement(name = "sessionCookie")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current account"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/me")
    public AccountResponse me(Authentication authentication) {
        var user = users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
        Set<String> roles = user.getRoles().stream().map(role -> role.getName().name())
                .collect(Collectors.toUnmodifiableSet());
        return new AccountResponse(user.getId(), user.getEmail(), roles);
    }

    public record RegistrationRequest(
            @Schema(example = "person@example.com") @NotBlank @Email @Size(max = 320) String email,
            @Schema(description = "Plain text password (never echoed)", accessMode = Schema.AccessMode.WRITE_ONLY,
                    minLength = 12, maxLength = 72) @NotBlank @Size(min = 12, max = 72) String password) {}

    public record RegistrationResponse(UUID id, String email) {}
    public record CsrfResponse(String headerName, String token) {}
    public record AccountResponse(UUID id, String email, Set<String> roles) {}
}
