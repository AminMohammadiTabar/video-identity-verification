package com.example.videokyc.dms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "dms.security.password=test-only-long-dms-password")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class DmsFileFlowH2IntegrationTest {
    @TempDir static Path temporary;
    @Autowired MockMvc mvc;
    private static final String API = "/api/v1/dms/files";
    private static final String AUTH_USER = "dms-admin";
    private static final String AUTH_PASSWORD = "test-only-long-dms-password";
    private static final Pattern ID = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> "jdbc:h2:mem:dms_step3_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        props.add("dms.storage.root", () -> temporary.resolve("storage").toString());
    }

    @Test
    void uploadDownloadSoftDeleteAndPurge() throws Exception {
        byte[] bytes = "%PDF-1.7\nexample document\n%%EOF\n".getBytes(StandardCharsets.UTF_8);
        var upload = mvc.perform(multipart(API)
                .file(new MockMultipartFile("file", "evidence.pdf", "application/pdf", bytes))
                .param("subsystem", "AUTH")
                .param("ownerType", "SYSTEM")
                .param("ownerReference", "SYSTEM-AUTH-REGISTER")
                .with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.ownerReference").value("SYSTEM-AUTH-REGISTER"))
            .andExpect(jsonPath("$.relativePath").doesNotExist())
            .andReturn();
        var matcher = ID.matcher(upload.getResponse().getContentAsString());
        assertTrue(matcher.find());
        UUID id = UUID.fromString(matcher.group(1));

        Path systemFolder;
        try (var files = Files.walk(temporary.resolve("storage").resolve("AUTH"))) {
            systemFolder = files.filter(Files::isRegularFile).findFirst().orElseThrow().getParent();
        }
        assertEquals("SYSTEM-AUTH-REGISTER", systemFolder.getFileName().toString());
        assertEquals(3, temporary.resolve("storage").resolve("AUTH").relativize(systemFolder).getNameCount() - 1);

        mvc.perform(get(API + "/" + id + "/content").with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes));

        mvc.perform(delete(API + "/" + id).with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(status().isNoContent());
        mvc.perform(get(API + "/" + id + "/content").with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(status().isNotFound());
        mvc.perform(get(API + "/" + id).with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(jsonPath("$.status").value("DELETED"));
        mvc.perform(post(API + "/" + id + "/purge").with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(status().isNoContent());
        mvc.perform(get(API + "/" + id).with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(jsonPath("$.status").value("PURGED"));
        mvc.perform(post(API + "/" + id + "/purge").with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(status().isNoContent());
        try (var files = Files.walk(temporary.resolve("storage"))) {
            assertEquals(0, files.filter(Files::isRegularFile).count());
        }
    }

    @Test
    void authenticationAndInputValidation() throws Exception {
        mvc.perform(get(API + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post(API + "/" + UUID.randomUUID() + "/purge")
                .with(httpBasic(AUTH_USER, AUTH_PASSWORD))).andExpect(status().isNotFound());
        mvc.perform(multipart(API)
                .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)))
                .param("subsystem", "AUTH")
                .param("ownerType", "SYSTEM")
                .param("ownerReference", "SYSTEM-AUTH-REGISTER")
                .with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart(API)
                .file(new MockMultipartFile("file", "sample.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)))
                .param("subsystem", "../AUTH")
                .param("ownerType", "SYSTEM")
                .param("ownerReference", "SYSTEM-AUTH-REGISTER")
                .with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(API)
                .file(new MockMultipartFile("file", "sample.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)))
                .param("subsystem", "AUTH")
                .param("ownerType", "USER")
                .param("ownerReference", "not-uuid")
                .with(httpBasic(AUTH_USER, AUTH_PASSWORD)))
                .andExpect(status().isBadRequest());
    }
}
