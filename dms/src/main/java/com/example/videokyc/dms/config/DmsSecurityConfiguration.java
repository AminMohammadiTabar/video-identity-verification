package com.example.videokyc.dms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import java.util.ArrayList;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import com.example.videokyc.dms.observability.audit.AuditEventCommand;
import com.example.videokyc.dms.observability.audit.AuditEventRecorder;
import com.example.videokyc.dms.observability.audit.DmsAuditActorProvider;

@Configuration(proxyBeanMethods = false)
public class DmsSecurityConfiguration {
    private final AuditEventRecorder audit;
    private final DmsAuditActorProvider actors;

    public DmsSecurityConfiguration(AuditEventRecorder audit, DmsAuditActorProvider actors) {
        this.audit = audit;
        this.actors = actors;
    }

    @Bean
    @Order(3)
    SecurityFilterChain dmsApiSecurity(HttpSecurity http) throws Exception {
        // HTTP Basic with stateless requests, no browser session/cookie authentication.
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/dms/files").hasAnyRole("ADMIN", "AUTH_SERVICE")
                        .requestMatchers(HttpMethod.GET, "/api/v1/dms/files/*/content").hasAnyRole("ADMIN", "AUTH_SERVICE")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/dms/files/*").hasAnyRole("ADMIN", "AUTH_SERVICE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/dms/files/*/purge").hasAnyRole("ADMIN", "AUTH_SERVICE")
                        .anyRequest().hasRole("ADMIN"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, failure) -> {
                            audit.recordIndependent(AuditEventCommand.of("dms.security.authentication.failed",
                                    "ANONYMOUS", null, null, null, "DMS_API", null,
                                    "DENIED", "AUTHENTICATION_REQUIRED", null, null));
                            response.sendError(401);
                        })
                        .accessDeniedHandler((request, response, failure) -> {
                            var actor = actors.current();
                            audit.recordIndependent(AuditEventCommand.of("dms.security.access.denied",
                                    actor.type(), actor.id(), null, null, "DMS_API", null,
                                    "DENIED", "INSUFFICIENT_AUTHORITY", null, null));
                            response.sendError(403);
                        }))
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, failure) -> {
                    audit.recordIndependent(AuditEventCommand.of("dms.security.authentication.failed",
                            "ANONYMOUS", null, null, null, "DMS_API", null,
                            "DENIED", "INVALID_BASIC_CREDENTIALS", null, null));
                    response.sendError(401);
                }));
        return http.build();
    }

    @Bean
    PasswordEncoder dmsPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService dmsUsers(@Value("${dms.security.username}") String username,
                                @Value("${dms.security.password}") String password,
                                PasswordEncoder passwordEncoder,
                                @Value("${dms.security.auth-service.username:auth-service}") String serviceUsername,
                                @Value("${dms.security.auth-service.password:}") String servicePassword) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("DMS_API_USERNAME and DMS_API_PASSWORD must be set");
        }
        var accounts = new ArrayList<UserDetails>();
        accounts.add(User.withUsername(username).password(passwordEncoder.encode(password))
                .roles("ADMIN").build());
        if (servicePassword != null && !servicePassword.isBlank()) {
            if (serviceUsername == null || serviceUsername.isBlank() || serviceUsername.equals(username)) {
                throw new IllegalStateException("DMS service username must be distinct");
            }
            accounts.add(User.withUsername(serviceUsername)
                    .password(passwordEncoder.encode(servicePassword)).roles("AUTH_SERVICE").build());
        }
        return new InMemoryUserDetailsManager(accounts);
    }
}
