package com.example.videokyc.auth;

import com.example.videokyc.auth.domain.RoleName;
import com.example.videokyc.auth.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("h2")
class AuthH2FlywayIntegrationTest {
    @Autowired RoleRepository roles;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", () -> "jdbc:h2:mem:auth_step3_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Test
    void flywayCreatesRoles() {
        assertTrue(roles.findByName(RoleName.ROLE_USER).isPresent());
        assertTrue(roles.findByName(RoleName.ROLE_ADMIN).isPresent());
    }
}
