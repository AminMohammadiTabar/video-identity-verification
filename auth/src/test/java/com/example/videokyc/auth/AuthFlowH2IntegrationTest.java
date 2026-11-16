package com.example.videokyc.auth;

import com.example.videokyc.auth.domain.RoleName;
import com.example.videokyc.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import com.example.videokyc.auth.observability.HttpAuditFilter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AuthFlowH2IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () ->
                "jdbc:h2:mem:auth_step4_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Test
    void registrationLoginAndProtectedVideoPage() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "A-good-secret-23";
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ثبت‌نام")));

        mvc.perform(post("/register").with(csrf())
                        .param("email", email.toUpperCase())
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        var created = users.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(encoder.matches(password, created.getPasswordHash()));
        assertNotEquals(password, created.getPasswordHash());
        assertEquals(1, created.getRoles().size());
        assertEquals(RoleName.ROLE_USER, created.getRoles().iterator().next().getName());

        var login = mvc.perform(formLogin("/login").user(email).password(password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verification/upload"))
                .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/verification/upload").session(session))
                .andExpect(status().isOk())
                .andExpect(request().attribute(HttpAuditFilter.ACTOR_ATTRIBUTE, "user:" + created.getId()))
                .andExpect(content().string(containsString("NOT_SUBMITTED")))
                .andExpect(content().string(containsString("ارسال ویدئو برای بررسی")));
        mvc.perform(get("/admin").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void csrfRoleAndDuplicateRegistrationAreEnforced() throws Exception {
        mvc.perform(post("/register").param("email", "no-csrf@example.com")
                        .param("password", "A-good-secret-23"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/verification/upload")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());

        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/register").with(csrf()).param("email", email)
                        .param("password", "A-good-secret-23"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/register").with(csrf()).param("email", email)
                        .param("password", "A-good-secret-23"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("قبلاً ثبت شده")));
    }

    @Test
    void documentedRestEndpointsAreLiveAndCsrfProtected() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/registrations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"rest@example.com\",\"password\":\"A-good-secret-23\"}"))
                .andExpect(status().isForbidden());

        String email = "api-" + UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"A-good-secret-23\"}";
        mvc.perform(post("/api/v1/auth/registrations").with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/v1/auth/registrations").with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void unsupportedCsrfEndpointMethodDistinguishesAuthenticationFromAuthorization() throws Exception {
        // With a valid CSRF token, denyAll() routes an anonymous caller to the
        // REST authentication entry point (401), not to the access-denied handler.
        mvc.perform(post("/api/v1/auth/csrf").with(csrf()))
                .andExpect(status().isUnauthorized());

        // An authenticated user is still not permitted to POST to this GET-only API.
        mvc.perform(post("/api/v1/auth/csrf")
                        .with(user("api-user").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        // CSRF rejection happens before authorization, even for anonymous clients.
        mvc.perform(post("/api/v1/auth/csrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerProducesCorrectAuthContract() throws Exception {
        mvc.perform(get("/v3/api-docs/auth-api"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/auth/registrations")))
                .andExpect(content().string(containsString("sessionCookie")));
        // Public documentation must redirect to Swagger's static UI, never to /login.
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/swagger-ui/")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/login"))));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
    @Test
    void allResponsesHaveCorrelationIdAndAuthenticatedPagesCaptureActor() throws Exception {
        mvc.perform(get("/verification/upload"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/verification/upload").with(user("sample@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(request().attribute(HttpAuditFilter.ACTOR_ATTRIBUTE, "authenticated"))
                .andExpect(header().exists("X-Request-ID"));
    }
}
