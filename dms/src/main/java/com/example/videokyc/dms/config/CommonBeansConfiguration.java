package com.example.videokyc.dms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Common infrastructure beans for DMS.
 * <p>
 * Ensures {@link ObjectMapper} is available to {@link com.example.videokyc.dms.observability.audit.AuditEventRecorder}
 * even in narrow test slice contexts.
 */
@Configuration(proxyBeanMethods = false)
public class CommonBeansConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
