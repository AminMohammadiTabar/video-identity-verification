package com.example.videokyc.workflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI workflowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("WORKFLOW Service API")
                .version("v1")
                .description("Scaffold only. There are no state machine, transitions or workflow administration endpoints in phase one.")
                .contact(new Contact().name("Video Identity Verification Engineering")));
    }

    @Bean
    GroupedOpenApi workflowApiGroup() {
        return GroupedOpenApi.builder().group("workflow-api")
                .pathsToMatch("/api/v1/workflow/**").build();
    }
}
