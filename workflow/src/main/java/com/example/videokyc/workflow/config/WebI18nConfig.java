package com.example.videokyc.workflow.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Internationalization (i18n) configuration for the Workflow service.
 * <p>
 * Configures Persian (fa) as the default locale and English (en) as a supported locale,
 * resolving client preference via the HTTP {@code Accept-Language} header.
 */
@Configuration(proxyBeanMethods = false)
public class WebI18nConfig {

    public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("fa");
    public static final List<Locale> SUPPORTED_LOCALES = List.of(
            DEFAULT_LOCALE,
            Locale.ENGLISH
    );

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        return resolver;
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages/messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }
}
