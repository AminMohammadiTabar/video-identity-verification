package com.example.videokyc.workflow;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test verifying Flyway migrations V1 through V4 and relational constraints
 * on H2 in PostgreSQL compatibility mode.
 */
@SpringBootTest
@ActiveProfiles("h2")
class WorkflowDatabaseMigrationIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () ->
                "jdbc:h2:mem:workflow_migration_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Should verify that all V1-V4 tables exist in schema workflow")
    void shouldVerifyTablesExist() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_SCHEMA) = 'workflow'",
                Integer.class
        );
        // Expecting audit_events, workflow_definitions, workflow_versions, workflow_states,
        // workflow_transitions, workflow_publication_jobs, workflow_outbox_events, flyway_schema_history
        assertThat(count).isGreaterThanOrEqualTo(7);
    }

    @Test
    @DisplayName("Should verify upgrade path from existing V1 schema to V4")
    void shouldUpgradeFromExistingV1ToV4() {
        String url = "jdbc:h2:mem:workflow_upgrade_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        // Step 1: Migrate only up to V1 (simulating pre-Phase-7 database)
        Flyway flywayV1 = Flyway.configure()
                .dataSource(url, "sa", "")
                .schemas("workflow")
                .defaultSchema("workflow")
                .createSchemas(true)
                .locations("classpath:db/migration-h2")
                .target("1")
                .load();
        flywayV1.migrate();

        assertThat(flywayV1.info().current().getVersion().getVersion()).isEqualTo("1");

        // Step 2: Migrate from V1 to latest (V4)
        Flyway flywayLatest = Flyway.configure()
                .dataSource(url, "sa", "")
                .schemas("workflow")
                .defaultSchema("workflow")
                .locations("classpath:db/migration-h2")
                .load();
        flywayLatest.migrate();

        assertThat(flywayLatest.info().current().getVersion().getVersion()).isEqualTo("4");
    }

    @Test
    @DisplayName("Should enforce UNIQUE workflow_key on workflow_definitions")
    void shouldEnforceUniqueWorkflowKey() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (workflow_key, name, status, created_by, updated_by) " +
                "VALUES ('FLOW_UNIQ', 'Name 1', 'ACTIVE', 'admin', 'admin')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_definitions (workflow_key, name, status, created_by, updated_by) " +
                        "VALUES ('FLOW_UNIQ', 'Name 2', 'ACTIVE', 'admin', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce ck_workflow_definitions_status enum check")
    void shouldRejectInvalidDefinitionStatus() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_definitions (workflow_key, name, status, created_by, updated_by) " +
                        "VALUES ('FLOW_BAD_STATUS', 'Bad', 'INVALID_STATUS', 'admin', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce UNIQUE (definition_id, version_number) on workflow_versions")
    void shouldEnforceUniqueVersionNumberPerDefinition() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (100, 'FLOW_VNUM', 'Name', 'ACTIVE', 'admin', 'admin')");

        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (1001, 100, 1, 'DRAFT', '{}', 'admin')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                        "VALUES (1002, 100, 1, 'DRAFT', '{}', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should reject non-positive version numbers on workflow_versions")
    void shouldRejectNonPositiveVersionNumber() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (150, 'FLOW_VPOS', 'Name', 'ACTIVE', 'admin', 'admin')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                        "VALUES (1501, 150, 0, 'DRAFT', '{}', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce ck_workflow_versions_published_data: PUBLISHED requires snapshot, checksum, published_at")
    void shouldEnforcePublishedVersionRequiresSnapshotChecksumAndTimestamp() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (160, 'FLOW_PUB_DATA', 'Name', 'ACTIVE', 'admin', 'admin')");

        // Attempting to insert PUBLISHED without snapshot_json, checksum, or published_at
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                        "VALUES (1601, 160, 1, 'PUBLISHED', '{}', 'admin')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce UNIQUE (version_id, state_key) on workflow_states")
    void shouldEnforceUniqueStateKeyPerVersion() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (170, 'FLOW_SKEY', 'Name', 'ACTIVE', 'admin', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (1701, 170, 1, 'DRAFT', '{}', 'admin')");

        jdbc.update("INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                "VALUES (17001, 1701, 'STATE_A', 'State A', 'INITIAL', 0)");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                        "VALUES (17002, 1701, 'STATE_A', 'Duplicate State A', 'INTERMEDIATE', 1)"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should reject negative display order on workflow_states")
    void shouldRejectNegativeDisplayOrderOnStates() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (180, 'FLOW_SNEG', 'Name', 'ACTIVE', 'admin', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (1801, 180, 1, 'DRAFT', '{}', 'admin')");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                        "VALUES (18001, 1801, 'STATE_NEG', 'Negative Order', 'INITIAL', -1)"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce UNIQUE (version_id, transition_key) on workflow_transitions")
    void shouldEnforceUniqueTransitionKeyPerVersion() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (190, 'FLOW_TKEY', 'Name', 'ACTIVE', 'admin', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (1901, 190, 1, 'DRAFT', '{}', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                "VALUES (19001, 1901, 'S1', 'State 1', 'INITIAL', 0)");
        jdbc.update("INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                "VALUES (19002, 1901, 'S2', 'State 2', 'FINAL', 1)");

        jdbc.update("INSERT INTO workflow.workflow_transitions (id, version_id, transition_key, name, from_state_id, to_state_id, display_order) " +
                "VALUES (190001, 1901, 'TR_KEY', 'Transition', 19001, 19002, 0)");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_transitions (id, version_id, transition_key, name, from_state_id, to_state_id, display_order) " +
                        "VALUES (190002, 1901, 'TR_KEY', 'Duplicate Transition', 19001, 19002, 1)"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce cross-version transition FK constraint: transition cannot connect states from different versions")
    void shouldEnforceTransitionSameVersionConstraint() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (200, 'FLOW_CROSS', 'Cross', 'ACTIVE', 'admin', 'admin')");

        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (2001, 200, 1, 'DRAFT', '{}', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (2002, 200, 2, 'DRAFT', '{}', 'admin')");

        jdbc.update("INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                "VALUES (5001, 2001, 'S1', 'State 1', 'INITIAL', 0)");
        jdbc.update("INSERT INTO workflow.workflow_states (id, version_id, state_key, name, state_type, display_order) " +
                "VALUES (5002, 2002, 'S2', 'State 2 in v2', 'FINAL', 1)");

        // Attempting to create transition in version 2001 pointing to to_state_id 5002 (which belongs to version 2002)
        // Composite FK: FOREIGN KEY (to_state_id, version_id) REFERENCES workflow_states(id, version_id)
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_transitions (version_id, transition_key, name, from_state_id, to_state_id, display_order) " +
                        "VALUES (2001, 'TR_INVALID', 'Invalid', 5001, 5002, 0)"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce composite FK ensuring active_version_id belongs to the same definition")
    void shouldEnforceActiveVersionBelongsToSameDefinition() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (300, 'DEF_A', 'Def A', 'ACTIVE', 'admin', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (301, 'DEF_B', 'Def B', 'ACTIVE', 'admin', 'admin')");

        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by, published_at, snapshot_json, checksum) " +
                "VALUES (3001, 301, 1, 'PUBLISHED', '{}', 'admin', CURRENT_TIMESTAMP, '{}', '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef')");

        // Attempt to set active_version_id of DEF_A (id=300) to version 3001 (which belongs to DEF_B id=301)
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE workflow.workflow_definitions SET active_version_id = 3001 WHERE id = 300"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce ck_workflow_definitions_versions_different: active and pending version cannot be the same")
    void shouldEnforceActiveAndPendingCannotBeEqual() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (400, 'DEF_DIFF', 'Def Diff', 'ACTIVE', 'admin', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by, published_at, snapshot_json, checksum) " +
                "VALUES (4001, 400, 1, 'PUBLISHED', '{}', 'admin', CURRENT_TIMESTAMP, '{}', '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef')");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE workflow.workflow_definitions SET active_version_id = 4001, pending_version_id = 4001 WHERE id = 400"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce ck_workflow_publication_jobs_lease on publication jobs table")
    void shouldEnforcePublicationJobLeaseCheck() {
        jdbc.update("INSERT INTO workflow.workflow_definitions (id, workflow_key, name, status, created_by, updated_by) " +
                "VALUES (500, 'DEF_JOB', 'Def Job', 'ACTIVE', 'admin', 'admin')");
        jdbc.update("INSERT INTO workflow.workflow_versions (id, definition_id, version_number, status, context_schema_json, created_by) " +
                "VALUES (5001, 500, 1, 'DRAFT', '{}', 'admin')");

        // PROCESSING requires locked_by and locked_until
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_publication_jobs (version_id, status) VALUES (5001, 'PROCESSING')"
        )).isInstanceOf(DataIntegrityViolationException.class);

        // PENDING must NOT have locked_by or locked_until
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_publication_jobs (version_id, status, locked_by) " +
                        "VALUES (5001, 'PENDING', 'worker-1')"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce ck_workflow_outbox_lease and published timestamp on outbox events table")
    void shouldEnforceOutboxConstraints() {
        UUID eventId = UUID.randomUUID();

        // PROCESSING requires locked_by and locked_until
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_outbox_events (event_id, aggregate_type, aggregate_id, event_type, event_version, routing_key, payload_json, status) " +
                        "VALUES (?, 'DEFINITION', 1, 'workflow.definition.published', 1, 'workflow.definition.published', '{}', 'PROCESSING')",
                eventId
        )).isInstanceOf(DataIntegrityViolationException.class);

        // PUBLISHED requires published_at
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_outbox_events (event_id, aggregate_type, aggregate_id, event_type, event_version, routing_key, payload_json, status) " +
                        "VALUES (?, 'DEFINITION', 1, 'workflow.definition.published', 1, 'workflow.definition.published', '{}', 'PUBLISHED')",
                eventId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should enforce UNIQUE event_id on workflow_outbox_events")
    void shouldEnforceUniqueEventIdOnOutbox() {
        UUID eventId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO workflow.workflow_outbox_events (event_id, aggregate_type, aggregate_id, event_type, event_version, routing_key, payload_json, status) " +
                        "VALUES (?, 'DEFINITION', 1, 'workflow.definition.published', 1, 'workflow.definition.published', '{}', 'PENDING')",
                eventId
        );

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO workflow.workflow_outbox_events (event_id, aggregate_type, aggregate_id, event_type, event_version, routing_key, payload_json, status) " +
                        "VALUES (?, 'DEFINITION', 2, 'workflow.definition.published', 1, 'workflow.definition.published', '{}', 'PENDING')",
                eventId
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
