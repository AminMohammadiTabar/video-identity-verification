package com.example.videokyc.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regression: docs should be anonymously accessible even without h2/local security chain. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_swagger_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.locations=classpath:db/migration-h2",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerWithoutLocalProfileIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void enabledDocsNeverRedirectAnonymousVisitorsToLogin() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/v3/api-docs/auth-api"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/verification/upload"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
