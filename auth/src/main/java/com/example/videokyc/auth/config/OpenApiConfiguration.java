package com.example.videokyc.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI authOpenApi() {
        return new OpenAPI()
                .info(new Info().title("AUTH Service API")
                        .version("v1")
                        .description("Registration and session-aware account endpoints. "
                                + "HTML form login/logout are browser MVC routes, not JSON API endpoints. "
                                + "Swagger uses the browser's existing session cookie; obtain a CSRF token "
                                + "from GET /api/v1/auth/csrf before POST requests.")
                        .contact(new Contact().name("Video Identity Verification Engineering")))
                .components(new Components().addSecuritySchemes("sessionCookie",
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("JSESSIONID")
                                .description("Spring Security browser session cookie")));
    }

    @Bean
    GroupedOpenApi authApiGroup() {
        return GroupedOpenApi.builder().group("auth-api")
                .pathsToMatch("/api/v1/auth/**")
                .build();
    }
}
