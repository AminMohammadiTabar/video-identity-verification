package com.example.videokyc.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import com.example.videokyc.auth.observability.AuthActionAudit;
import com.example.videokyc.auth.observability.audit.AuditEventCommand;
import com.example.videokyc.auth.observability.audit.AuditEventRecorder;

/**
 * Spring Security configuration for the AUTH service.
 * <p>
 * Configures two distinct filter chains:
 * <ul>
 *   <li><b>REST API Filter Chain (Order 1):</b> Manages {@code /api/v1/auth/**} with CSRF validation,
 *       stateless entry point (401 for anonymous access denial), and custom access denied handlers.</li>
 *   <li><b>Web MVC Filter Chain (Order 2):</b> Manages session-backed Mustache UI routes ({@code /login},
 *       {@code /register}, {@code /verification/**}, {@code /admin/**}) with standard form login and logout.</li>
 * </ul>
 * Also captures security denial events independently via {@link AuditEventRecorder}.
 */
@Configuration(proxyBeanMethods = false)
public class AuthSecurityConfiguration {
    private final AuditEventRecorder audit;

    public AuthSecurityConfiguration(AuditEventRecorder audit) {
        this.audit = audit;
    }

    private void recordSecurityDenied(org.springframework.security.core.Authentication authentication,
                                      String eventType, String reasonCode) {
        java.util.UUID userId = AuthActionAudit.userId(authentication);
        audit.recordIndependent(AuditEventCommand.of(eventType, actorType(authentication),
                userId == null ? null : userId.toString(), null, null, "AUTH_API", null,
                "DENIED", reasonCode, null, null));
    }

    private static String actorType(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return "ANONYMOUS";
        return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))
                ? "ADMIN" : "USER";
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Order(3)
    SecurityFilterChain restApiSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/registrations").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/auth/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/verifications/me", "/api/v1/auth/verifications/history").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/verifications").authenticated()
                        // Deny unsupported API methods without weakening session/CSRF security.
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, failure) -> {
                            recordSecurityDenied(null, "auth.security.authentication.required", "AUTHENTICATION_REQUIRED");
                            response.sendError(HttpStatus.UNAUTHORIZED.value());
                        })
                        .accessDeniedHandler((request, response, failure) -> {
                            if (failure instanceof org.springframework.security.web.csrf.CsrfException) {
                                recordSecurityDenied(SecurityContextHolder.getContext().getAuthentication(),
                                        "auth.security.csrf.rejected", "CSRF_REJECTED");
                            } else {
                                recordSecurityDenied(SecurityContextHolder.getContext().getAuthentication(),
                                        "auth.authorization.denied", "INSUFFICIENT_AUTHORITY");
                            }
                            response.sendError(HttpStatus.FORBIDDEN.value());
                        }));
        // CSRF stays enabled here (including public registration) to protect session cookies.
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain browserSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/", "/login", "/register", "/css/**", "/favicon.ico").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers("/verification/**").authenticated()
                    .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler((request, response, authentication) -> {
                            java.util.UUID userId = AuthActionAudit.userId(authentication);
                            request.setAttribute(com.example.videokyc.auth.observability.HttpAuditFilter.ACTOR_ATTRIBUTE,
                                    userId == null ? "authenticated" : "user:" + userId);
                            AuthActionAudit.record("AUTH_LOGIN", "SUCCESS", userId);
                            audit.recordIndependent(AuditEventCommand.of("auth.login.succeeded", actorType(authentication),
                                    userId == null ? null : userId.toString(), null, null, "AUTH_SESSION", null,
                                    "SUCCESS", null, null, null));
                            response.sendRedirect(request.getContextPath() + "/verification/upload");
                        })
                        .failureHandler((request, response, exception) -> {
                            AuthActionAudit.record("AUTH_LOGIN", "DENIED", null);
                            audit.recordIndependent(AuditEventCommand.of("auth.login.failed", "ANONYMOUS", null,
                                    null, null, "AUTH_SESSION", null, "DENIED", "INVALID_CREDENTIALS", null, null));
                            response.sendRedirect(request.getContextPath() + "/login?error");
                        }).permitAll())
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, failure) -> {
                    if (failure instanceof org.springframework.security.web.csrf.CsrfException) {
                        recordSecurityDenied(SecurityContextHolder.getContext().getAuthentication(),
                                "auth.security.csrf.rejected", "CSRF_REJECTED");
                    } else {
                        recordSecurityDenied(SecurityContextHolder.getContext().getAuthentication(),
                                "auth.authorization.denied", "BROWSER_ACCESS_DENIED");
                    }
                    response.sendError(HttpStatus.FORBIDDEN.value());
                }))
                .logout(logout -> logout.logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .addLogoutHandler((request, response, authentication) -> {
                            java.util.UUID userId = AuthActionAudit.userId(authentication);
                            AuthActionAudit.record("AUTH_LOGOUT", "SUCCESS", userId);
                            audit.recordIndependent(AuditEventCommand.of("auth.logout", actorType(authentication),
                                    userId == null ? null : userId.toString(), null, null, "AUTH_SESSION", null,
                                    "SUCCESS", null, null, null));
                        }));
        return http.build();
    }
}
