package com.example.videokyc.dms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Public API docs only in local developer profiles; production docs stay Basic-protected if opted in. */
@Configuration(proxyBeanMethods = false)
@Profile({"h2", "local"})
public class LocalSwaggerSecurityConfiguration {
    @Bean
    @Order(2)
    SecurityFilterChain swaggerSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
