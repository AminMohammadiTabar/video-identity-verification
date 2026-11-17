package com.example.videokyc.workflow;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Authoritative integration test verifying PostgreSQL Flyway migrations (db/migration/ V1 through V4),
 * upgrade paths, Hibernate schema validation, and PostgreSQL-specific relational constraints
 * against a real PostgreSQL container.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class WorkflowPostgresMigrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("video_identity_db")
            .withUsername("vik_local")
            .withPassword("change-me-local-only");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.schemas", () -> "workflow");
        registry.add("spring.flyway.default-schema", () -> "workflow");
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("PostgreSQL: Should verify that all V1-V4 tables exist in schema workflow")
    void shouldVerifyAllTablesExistInPostgres() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'workflow'",
                Integer.class
        );
        // Expecting audit_events, workflow_definitions, workflow_versions, workflow_states,
        // workflow_transitions, workflow_publication_jobs, workflow_outbox_events, flyway_schema_history
        assertThat(count).isGreaterThanOrEqualTo(7);
    }

    @Test
    @DisplayName("PostgreSQL: Should verify upgrade path from existing V1 schema to V4 on real PostgreSQL")
    void shouldVerifyPostgresUpgradePathFromV1ToV4() {
        // Use a dedicated database/schema inside the PostgreSQL container for upgrade testing
        String jdbcUrl = postgres.getJdbcUrl();
        String user = postgres.getUsername();
        String pass = postgres.getPassword();

        // Step 1: Migrate only V1 in a separate schema "workflow_upgrade_test"
        Flyway flywayV1 = Flyway.configure()
                .dataSource(jdbcUrl, user, pass)
                .schemas("workflow_upgrade_test")
                .defaultSchema("workflow_upgrade_test")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target("1")
                .load();
        flywayV1.migrate();

        assertThat(flywayV1.info().current().getVersion().getVersion()).isEqualTo("1");

        // Step 2: Upgrade existing V1 schema to latest (V4)
        Flyway flywayLatest = Flyway.configure()
                .dataSource(jdbcUrl, user, pass)
                .schemas("workflow_upgrade_test")
                .defaultSchema("workflow_upgrade_test")
                .locations("classpath:db/migration")
                .load();
        flywayLatest.migrate();

        assertThat(flywayLatest.info().current().getVersion().getVersion()).isEqualTo("4");
    }

    @Test
    @DisplayName("PostgreSQL: Should enforce UNIQUE workflow_key on workflow_definitions")
    void shouldEnforcePostgresUniqueWorkflowKey() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (workflow_key, name, status, created_by, updated_by) " +
                "VALUES ('FLOW_PG_UNIQ', 'Name 1', 'ACTIVE', 'admin', 'admin')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_definitions (workflow_key, name, status, created_by, updated_by) " +
                        "VALUES ('FLOW_PG_UNIQ', 'Name 2', 'ACTIVE', 'admin', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL: Should enforce ck_workflow_definitions_status check constraint")
    void shouldEnforcePostgresStatusCheckConstraint() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_definitions (workflow_key, name, status, created_by, updated_by) " +
                        "VALUES ('FLOW_PG_BAD_STATUS', 'Bad', 'INVALID_STATUS', 'admin', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL: Should enforce UNIQUE (definition_id, version_number) on workflow_versions")
    void shouldEnforcePostgresUniqueVersionNumberPerDefinition() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (200, 'FLOW_PG_VNUM', 'Name', 'ACTIVE', 'admin', 'admin')");

        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (2001, 200, 1, 'DRAFT', '{}', 'admin')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                        "VALUES (2002, 200, 1, 'DRAFT', '{}', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL: Should enforce ck_workflow_versions_published_data constraint on PUBLISHED")
    void shouldEnforcePostgresPublishedVersionRequiresChecksumAndSnapshot() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (210, 'FLOW_PG_PUB_DATA', 'Name', 'ACTIVE', 'admin', 'admin')");

        // Attempting to insert PUBLISHED without snapshot_json, checksum, or published_at
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                        "VALUES (2101, 210, 1, 'PUBLISHED', '{}', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL: Should enforce ck_wv_checksum_hex (64 hex characters) on published versions")
    void shouldEnforcePostgresChecksumHexCheckConstraint() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (220, 'FLOW_PG_HEX', 'Name', 'ACTIVE', 'admin', 'admin')");

        // Checksum with invalid character 'Z' (not hex)
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, snapshot_json, checksum, published_at, context_schema_json, created_by) " +
                        "VALUES (2201, 220, 1, 'PUBLISHED', '{\"test\":true}', 'ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ', CURRENT_TIMESTAMP, '{}', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL: Should enforce UNIQUE (version_id, state_key) on workflow_states")
    void shouldEnforcePostgresUniqueStateKeyPerVersion() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (230, 'FLOW_PG_SKEY', 'Name', 'ACTIVE', 'admin', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (2301, 230, 1, 'DRAFT', '{}', 'admin')");

        jdbc.update("INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                "VALUES (23001, 2301, 'STATE_A', 'State A', 'INITIAL', 0)");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                        "VALUES (23002, 2301, 'STATE_A', 'Duplicate State A', 'INTERMEDIATE', 1)"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL: Should reject cross-version transitions using composite same-version FK")
    void shouldEnforcePostgresCrossVersionTransitionRejection() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (240, 'FLOW_PG_CROSS', 'Name', 'ACTIVE', 'admin', 'admin')");

        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (2401, 240, 1, 'DRAFT', '{}', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (2402, 240, 2, 'DRAFT', '{}', 'admin')");

        // State 1 in Version 1
        jdbc.update("INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                "VALUES (24001, 2401, 'FROM_S', 'From State', 'INITIAL', 0)");
        // State 2 in Version 2
        jdbc.update("INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                "VALUES (24002, 2402, 'TO_S', 'To State', 'FINAL', 0)");

        // Attempting to create transition in Version 1 pointing to to_state_id 24002 (which belongs to Version 2)
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_transitions (id, version_id, transition_key, name, from_state_id, to_state_id) " +
                        "VALUES (240001, 2401, 'CROSS_TRANS', 'Cross Transition', 24001, 24002)"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL: Should enforce chk_wpj_lease_pair worker lease consistency check on publication jobs")
    void shouldEnforcePostgresWorkerLeasePairConstraint() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (250, 'FLOW_PG_WPJ', 'Name', 'ACTIVE', 'admin', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (2501, 250, 1, 'PUBLISH_PENDING', '{}', 'admin')");

        // Insert job with worker_id set but lease_expires_at NULL (violates chk_wpj_lease_pair)
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_publication_jobs (definition_id, target_version_id, status, worker_id, lease_expires_at) " +
                        "VALUES (250, 2501, 'PROCESSING', 'worker-1', NULL)"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL: Should enforce chk_woe_lease_pair publisher lease consistency check on outbox events")
    void shouldEnforcePostgresOutboxLeasePairConstraint() {
        UUID eventId = UUID.randomUUID();
        // Insert outbox event with locked_by set but lease_expires_at NULL (violates chk_woe_lease_pair)
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_outbox_events (event_id, aggregate_type, aggregate_id, event_type, payload_json, status, locked_by, lease_expires_at) " +
                        "VALUES (?, 'WorkflowVersion', 2501, 'WORKFLOW_PUBLISHED', '{}', 'PROCESSING', 'publisher-1', NULL)",
                eventId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
