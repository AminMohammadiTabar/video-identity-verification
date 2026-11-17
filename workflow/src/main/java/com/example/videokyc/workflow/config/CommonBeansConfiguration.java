package com.example.videokyc.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Common infrastructure beans for WORKFLOW.
 * <p>
 * Ensures {@link ObjectMapper} is available to {@link com.example.videokyc.workflow.observability.audit.AuditEventRecorder}
 * for persisting structured audit event details.
 */
@Configuration(proxyBeanMethods = false)
public class CommonBeansConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
