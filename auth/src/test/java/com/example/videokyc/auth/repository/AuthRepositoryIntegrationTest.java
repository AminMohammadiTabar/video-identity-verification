package com.example.videokyc.auth.repository;

import com.example.videokyc.auth.domain.RoleName;
import com.example.videokyc.auth.domain.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class AuthRepositoryIntegrationTest {
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;

    @Test
    void roleSeedAndUserPersistenceWork() {
        var userRole = roles.findByName(RoleName.ROLE_USER).orElseThrow();
        var email = "repo-" + UUID.randomUUID() + "@example.test";
        var user = new UserEntity(email, "test-hash-not-a-real-password");
        user.assignRole(userRole);
        users.saveAndFlush(user);
        assertTrue(users.existsByEmailIgnoreCase(email.toUpperCase(java.util.Locale.ROOT)));
        assertEquals(1, users.findByEmailIgnoreCase(email).orElseThrow().getRoles().size());
    }
}
