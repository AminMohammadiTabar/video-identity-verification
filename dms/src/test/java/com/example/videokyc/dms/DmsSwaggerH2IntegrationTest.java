package com.example.videokyc.dms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "dms.security.password=test-only-dms-docs-passphrase")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class DmsSwaggerH2IntegrationTest {
    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () ->
                "jdbc:h2:mem:dms_docs_step4;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Test
    void openApiSpecDescribesProtectedFileOperations() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/v3/api-docs/dms-api"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/dms/files")))
                .andExpect(content().string(containsString("dmsBasic")))
                .andExpect(content().string(containsString("/purge")));
        mvc.perform(get("/api/v1/dms/files/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/api/v1/dms/files/00000000-0000-0000-0000-000000000000")
                        .with(httpBasic("dms-admin", "test-only-dms-docs-passphrase")))
                .andExpect(status().isNotFound());
    }
}
