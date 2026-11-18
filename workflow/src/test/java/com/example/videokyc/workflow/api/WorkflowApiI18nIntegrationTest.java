package com.example.videokyc.workflow.api;

import com.example.videokyc.workflow.domain.shared.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Import(WorkflowApiI18nIntegrationTest.TestExceptionThrowingController.class)
class WorkflowApiI18nIntegrationTest {

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:h2:mem:workflow_i18n_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class TestExceptionThrowingController {
        @GetMapping("/api/test/throw")
        public void throwDomainException(@RequestParam String code) {
            throw new DomainException(code, "Default fallback error message for " + code);
        }
    }

    @Test
    @DisplayName("Should resolve Persian error message by default when Accept-Language is absent")
    void shouldResolvePersianByDefault() throws Exception {
        mockMvc.perform(get("/api/test/throw")
                        .param("code", "DEFINITION_NOT_ACTIVE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("DEFINITION_NOT_ACTIVE"))
                .andExpect(jsonPath("$.detail").value("تعریف گردش کار فعال نیست و امکان انتشار نسخه برای آن وجود ندارد."))
                .andExpect(jsonPath("$.errorCode").value("DEFINITION_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("Should resolve Persian error message when Accept-Language is fa")
    void shouldResolvePersianWhenExplicitlyRequested() throws Exception {
        mockMvc.perform(get("/api/test/throw")
                        .param("code", "DEFINITION_NOT_FOUND")
                        .header("Accept-Language", "fa")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("DEFINITION_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("تعریف گردش کار با شناسه مورد نظر یافت نشد."))
                .andExpect(jsonPath("$.errorCode").value("DEFINITION_NOT_FOUND"));
    }

    @Test
    @DisplayName("Should resolve English error message when Accept-Language is en")
    void shouldResolveEnglishWhenRequested() throws Exception {
        mockMvc.perform(get("/api/test/throw")
                        .param("code", "DEFINITION_NOT_ACTIVE")
                        .header("Accept-Language", "en")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("DEFINITION_NOT_ACTIVE"))
                .andExpect(jsonPath("$.detail").value("Workflow definition is not active; version publication is not allowed."))
                .andExpect(jsonPath("$.errorCode").value("DEFINITION_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("Should resolve conflict status and message for duplicate state key in English")
    void shouldResolveConflictAndMessageInEnglish() throws Exception {
        mockMvc.perform(get("/api/test/throw")
                        .param("code", "DUPLICATE_STATE_KEY")
                        .header("Accept-Language", "en")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("DUPLICATE_STATE_KEY"))
                .andExpect(jsonPath("$.detail").value("State key already exists in this version."))
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_STATE_KEY"));
    }

    @Test
    @DisplayName("Should fall back to exception default message when error code is unmapped in properties")
    void shouldFallbackToExceptionMessageWhenCodeNotMapped() throws Exception {
        mockMvc.perform(get("/api/test/throw")
                        .param("code", "CUSTOM_UNMAPPED_ERROR")
                        .header("Accept-Language", "en")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("CUSTOM_UNMAPPED_ERROR"))
                .andExpect(jsonPath("$.detail").value("Default fallback error message for CUSTOM_UNMAPPED_ERROR"));
    }
}
