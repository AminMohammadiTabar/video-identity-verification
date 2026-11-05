package com.example.videokyc.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class AuthApplicationTests {
    @Test
    void contextLoadsWithFlywayAndPostgresql() {
        // The application startup must run Flyway and Hibernate schema validation.
    }
}
