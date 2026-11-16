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

import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "dms.security.password=admin-local-password",
        "dms.security.auth-service.username=auth-service",
        "dms.security.auth-service.password=service-local-password"
})
@ActiveProfiles("h2")
@AutoConfigureMockMvc
class DmsAuthServiceScopeH2IntegrationTest {
    @TempDir static Path temp;
    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:dms_service_scope;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("dms.storage.root", () -> temp.resolve("files").toString());
    }

    @Test
    void authServiceCannotAccessSystemFilesOrMetadata() throws Exception {
        byte[] bytes = new byte[]{0,0,0,16,'f','t','y','p','i','s','o','m',0,0,0,0};
        String user = UUID.randomUUID().toString();
        var created = mvc.perform(multipart("/api/v1/dms/files")
                    .file(new MockMultipartFile("file", "id.mp4", "video/mp4", bytes))
                    .param("subsystem", "AUTH").param("ownerType", "USER")
                    .param("ownerReference", user)
                    .with(httpBasic("auth-service", "service-local-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerReference").value(user))
                .andReturn();
        var matcher = java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"")
                .matcher(created.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find());
        String id = matcher.group(1);
        mvc.perform(get("/api/v1/dms/files/" + id + "/content")
                    .with(httpBasic("auth-service", "service-local-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dms/files/" + id)
                    .with(httpBasic("auth-service", "service-local-password")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/dms/files")
                    .file(new MockMultipartFile("file", "id.mp4", "video/mp4", bytes))
                    .param("subsystem", "AUTH").param("ownerType", "SYSTEM")
                    .param("ownerReference", "SYSTEM-AUTH-REGISTER")
                    .with(httpBasic("auth-service", "service-local-password")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/dms/files")
                    .file(new MockMultipartFile("file", "id.mp4", "video/mp4", bytes))
                    .param("subsystem", "OTHER").param("ownerType", "USER")
                    .param("ownerReference", user)
                    .with(httpBasic("auth-service", "service-local-password")))
                .andExpect(status().isForbidden());
    }
}
