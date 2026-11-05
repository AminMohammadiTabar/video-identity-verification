package com.example.videokyc.dms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI dmsOpenApi() {
        return new OpenAPI()
                .info(new Info().title("DMS File Management API").version("v1")
                        .description("Protected upload, download, metadata and lifecycle endpoints. "
                                + "Files are stored under subsystem/date/owner folders. "
                                + "Send HTTP Basic credentials over HTTPS outside local development.")
                        .contact(new Contact().name("Video Identity Verification Engineering")))
                .components(new Components().addSecuritySchemes("dmsBasic",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("basic").description("DMS administrator credentials")))
                .addSecurityItem(new SecurityRequirement().addList("dmsBasic"));
    }

    @Bean
    GroupedOpenApi dmsApiGroup() {
        return GroupedOpenApi.builder().group("dms-api")
                .pathsToMatch("/api/v1/dms/**").build();
    }
}
