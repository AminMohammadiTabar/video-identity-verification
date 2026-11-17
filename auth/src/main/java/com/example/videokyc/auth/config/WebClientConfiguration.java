package com.example.videokyc.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Common bean definitions for AUTH infrastructure.
 * <p>
 * Provides fallback {@link ObjectMapper} and {@link RestClient.Builder} instances
 * when running in standalone slices, modular classpaths, or unit test contexts
 * where full Spring Boot auto-configuration is not triggered.
 */
@Configuration(proxyBeanMethods = false)
public class WebClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
