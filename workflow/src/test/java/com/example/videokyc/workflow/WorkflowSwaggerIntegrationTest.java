package com.example.videokyc.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WorkflowSwaggerIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void scaffoldIsDocumentedWithoutAnEngine() throws Exception {
        mvc.perform(get("/api/v1/workflow/info"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.status").value("NOT_IMPLEMENTED"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/workflow-api"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/workflow/info")));
    }
}
