package com.example.videokyc.dms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/** H2 console is only available under the h2 profile, bound to 127.0.0.1. */
@Configuration(proxyBeanMethods = false)
@Profile("h2")
public class LocalH2ConsoleSecurityConfiguration {
    @Bean
    @Order(1)
    SecurityFilterChain h2ConsoleSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/h2-console/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
