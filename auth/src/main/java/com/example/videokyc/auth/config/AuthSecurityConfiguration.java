package com.example.videokyc.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.example.videokyc.auth.observability.AuthActionAudit;

@Configuration(proxyBeanMethods = false)
public class AuthSecurityConfiguration {
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
                        // Deny unsupported API methods. With a valid CSRF token,
                        // anonymous callers receive 401 via HttpStatusEntryPoint;
                        // authenticated callers receive 403 via AccessDeniedHandler.
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
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
                            response.sendRedirect(request.getContextPath() + "/verification/upload");
                        })
                        .failureHandler((request, response, exception) -> {
                            AuthActionAudit.record("AUTH_LOGIN", "DENIED", null);
                            response.sendRedirect(request.getContextPath() + "/login?error");
                        }).permitAll())
                .logout(logout -> logout.logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .addLogoutHandler((request, response, authentication) ->
                                AuthActionAudit.record("AUTH_LOGOUT", "SUCCESS", AuthActionAudit.userId(authentication))));
        return http.build();
    }
}
