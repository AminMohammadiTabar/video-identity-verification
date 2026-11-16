package com.example.videokyc.auth;

import com.example.videokyc.auth.domain.RoleName;
import com.example.videokyc.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "auth.bootstrap.enabled=true",
        "auth.bootstrap.admin-email=initial-admin@example.com",
        "auth.bootstrap.admin-password=Bootstrap-Secret-2026"
})
@ActiveProfiles("h2")
class AdminBootstrapH2IntegrationTest {
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () ->
                "jdbc:h2:mem:auth_admin_bootstrap_step4;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Test
    void optInAdminBootstrapAssignsAdminRole() {
        var admin = users.findByEmailIgnoreCase("initial-admin@example.com").orElseThrow();
        assertTrue(encoder.matches("Bootstrap-Secret-2026", admin.getPasswordHash()));
        assertTrue(admin.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ROLE_ADMIN));
        assertEquals(1, users.countUsersWithRole(RoleName.ROLE_ADMIN));
    }
}
