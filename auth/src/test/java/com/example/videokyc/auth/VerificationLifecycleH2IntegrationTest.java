package com.example.videokyc.auth;

import com.example.videokyc.auth.domain.RoleName;
import com.example.videokyc.auth.repository.RoleRepository;
import com.example.videokyc.auth.repository.UserRepository;
import com.example.videokyc.auth.repository.VerificationRequestRepository;
import com.example.videokyc.auth.repository.EmailOutboxRepository;
import com.example.videokyc.auth.domain.EmailOutboxStatus;
import com.example.videokyc.auth.domain.EmailNotificationType;
import com.example.videokyc.auth.security.AccountPrincipal;
import com.example.videokyc.auth.service.DmsGateway;
import com.example.videokyc.auth.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class VerificationLifecycleH2IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired RegistrationService registration;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired VerificationRequestRepository requests;
    @Autowired EmailOutboxRepository emailOutbox;
    @MockitoBean DmsGateway dms;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () ->
                "jdbc:h2:mem:verification_step5;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    private AccountPrincipal principal(UUID id, String email, String role) {
        return new AccountPrincipal(id, User.withUsername(email).password("hidden").roles(role).build());
    }

    private MockMultipartFile video() {
        return new MockMultipartFile("video", "verification.webm", "video/webm", new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3,1});
    }

    @Test
    void userSubmissionAdminReviewAndDuplicateProtection() throws Exception {
        String email = "review-" + UUID.randomUUID() + "@example.com";
        UUID id = registration.register(email, "A-good-secret-23").id();
        UUID dmsId = UUID.randomUUID();
        when(dms.uploadUserVideo(eq(id), any())).thenReturn(dmsId);
        var principal = principal(id, email, "USER");

        mvc.perform(multipart("/api/v1/auth/verifications")
                        .file(video()).with(user(principal)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.dmsFileId").value(dmsId.toString()));
        mvc.perform(get("/api/v1/auth/verifications/me").with(user(principal)))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        mvc.perform(multipart("/api/v1/auth/verifications")
                        .file(video()).with(user(principal)).with(csrf()))
                .andExpect(status().isConflict());
        verify(dms, times(1)).uploadUserVideo(eq(id), any());

        var request = requests.findTopByUser_IdOrderByAttemptNumberDesc(id).orElseThrow();
        mvc.perform(get("/api/v1/auth/admin/verifications/pending").with(user(principal)))
                .andExpect(status().isForbidden());

        var reviewer = registration.register("admin-review-" + UUID.randomUUID() + "@example.com", "A-good-secret-23");
        var admin = principal(reviewer.id(), "admin@example.com", "ADMIN");
        String decision = "{\"approved\":true}";
        mvc.perform(post("/api/v1/auth/admin/verifications/" + request.getId() + "/decision")
                        .with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        var queued = emailOutbox.findByVerificationRequestId(request.getId());
        assertEquals(1, queued.size());
        assertEquals(EmailOutboxStatus.PENDING, queued.getFirst().getStatus());
        assertEquals(EmailNotificationType.VERIFICATION_APPROVED, queued.getFirst().getNotificationType());
        mvc.perform(post("/api/v1/auth/admin/verifications/" + request.getId() + "/decision")
                        .with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isConflict());
    }

    @Test
    void securityCsrfAndFailedUploadRecovery() throws Exception {
        var account = registration.register("failed-" + UUID.randomUUID() + "@example.com", "A-good-secret-23");
        UUID userId = account.id();
        var principal = principal(userId, account.email(), "USER");
        mvc.perform(multipart("/api/v1/auth/verifications").file(video()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/auth/verifications")
                        .file(video()).with(user(principal)))
                .andExpect(status().isForbidden());
        when(dms.uploadUserVideo(eq(userId), any())).thenThrow(new RuntimeException("connection error"));
        mvc.perform(multipart("/api/v1/auth/verifications")
                        .file(video()).with(user(principal)).with(csrf()))
                .andExpect(status().isBadGateway());
        assertEquals("UPLOAD_FAILED", requests.findTopByUser_IdOrderByAttemptNumberDesc(userId).orElseThrow().getStatus().name());
        when(dms.uploadUserVideo(eq(userId), any())).thenReturn(UUID.randomUUID());
        mvc.perform(multipart("/api/v1/auth/verifications")
                        .file(video()).with(user(principal)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void rejectedAttemptIsPreservedAndUserCanSubmitANewAttempt() throws Exception {
        var account = registration.register("reattempt-" + UUID.randomUUID() + "@example.com", "A-good-secret-23");
        UUID userId = account.id();
        var userPrincipal = principal(userId, account.email(), "USER");
        when(dms.uploadUserVideo(eq(userId), any())).thenReturn(UUID.randomUUID(), UUID.randomUUID());

        mvc.perform(multipart("/api/v1/auth/verifications").file(video())
                        .with(user(userPrincipal)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNumber").value(1));
        var first = requests.findTopByUser_IdOrderByAttemptNumberDesc(userId).orElseThrow();

        var reviewer = registration.register("reviewer-" + UUID.randomUUID() + "@example.com", "A-good-secret-23");
        var admin = principal(reviewer.id(), reviewer.email(), "ADMIN");
        mvc.perform(post("/api/v1/auth/admin/verifications/" + first.getId() + "/decision")
                        .with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"reason\":\"تصویر واضح نیست\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        var rejectedOutbox = emailOutbox.findByVerificationRequestId(first.getId());
        assertEquals(1, rejectedOutbox.size());
        assertEquals(EmailNotificationType.VERIFICATION_REJECTED, rejectedOutbox.getFirst().getNotificationType());

        mvc.perform(multipart("/api/v1/auth/verifications").file(video())
                        .with(user(userPrincipal)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNumber").value(2))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        assertEquals(2, requests.findByUser_IdOrderByAttemptNumberDesc(userId).size());
        mvc.perform(get("/api/v1/auth/verifications/history").with(user(userPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptNumber").value(2))
                .andExpect(jsonPath("$[1].attemptNumber").value(1))
                .andExpect(jsonPath("$[1].reviewReason").value("تصویر واضح نیست"));
    }

}
