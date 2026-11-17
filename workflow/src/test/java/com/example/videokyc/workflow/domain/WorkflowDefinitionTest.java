package com.example.videokyc.workflow.domain;

import com.example.videokyc.workflow.domain.definition.WorkflowDefinition;
import com.example.videokyc.workflow.domain.definition.WorkflowDefinitionStatus;
import com.example.videokyc.workflow.domain.definition.WorkflowKey;
import com.example.videokyc.workflow.domain.shared.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowDefinitionTest {

    private final Instant now = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    @DisplayName("Should successfully create a workflow definition with initial ACTIVE status")
    void shouldCreateDefinition() {
        WorkflowDefinition def = WorkflowDefinition.create(
                WorkflowKey.of("VIDEO_KYC"),
                "Video KYC Workflow",
                "End-to-end video KYC process",
                "admin",
                now
        );

        assertThat(def.getWorkflowKey().value()).isEqualTo("VIDEO_KYC");
        assertThat(def.getName()).isEqualTo("Video KYC Workflow");
        assertThat(def.getStatus()).isEqualTo(WorkflowDefinitionStatus.ACTIVE);
        assertThat(def.getActiveVersionId()).isNull();
        assertThat(def.getPendingVersionId()).isNull();
        assertThat(def.isExecutable()).isFalse();
    }

    @Test
    @DisplayName("Should validate WorkflowKey constraints")
    void shouldValidateWorkflowKey() {
        assertThatThrownBy(() -> WorkflowKey.of(""))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must not be null or blank");

        assertThatThrownBy(() -> WorkflowKey.of("invalid key with spaces!"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("must match pattern");

        WorkflowKey validKey = WorkflowKey.of("KYC_V2-PROCESS");
        assertThat(validKey.value()).isEqualTo("KYC_V2-PROCESS");
    }

    @Test
    @DisplayName("Should update metadata when definition is active")
    void shouldUpdateMetadata() {
        WorkflowDefinition def = WorkflowDefinition.create(
                WorkflowKey.of("KYC"), "Old Name", "Old Desc", "admin", now
        );

        def.updateMetadata("New Name", "New Desc", "editor", now.plusSeconds(60));

        assertThat(def.getName()).isEqualTo("New Name");
        assertThat(def.getDescription()).isEqualTo("New Desc");
        assertThat(def.getUpdatedBy()).isEqualTo("editor");
    }

    @Test
    @DisplayName("Should prohibit updates or archiving when pending version exists")
    void shouldProhibitArchivingWithPendingVersion() {
        WorkflowDefinition def = WorkflowDefinition.create(
                WorkflowKey.of("KYC"), "KYC", "Desc", "admin", now
        );
        def.setPendingVersion(10L, "admin", now);

        assertThatThrownBy(() -> def.archive("admin", now))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot archive definition while a version publication is pending");
    }

    @Test
    @DisplayName("Should prevent setting two pending versions concurrently")
    void shouldPreventConcurrentPendingVersions() {
        WorkflowDefinition def = WorkflowDefinition.create(
                WorkflowKey.of("KYC"), "KYC", "Desc", "admin", now
        );
        def.setPendingVersion(10L, "admin", now);

        assertThatThrownBy(() -> def.setPendingVersion(11L, "admin", now))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already pending");
    }

    @Test
    @DisplayName("Should prevent active and pending versions from being the same")
    void shouldPreventSameActiveAndPendingVersion() {
        WorkflowDefinition def = WorkflowDefinition.create(
                WorkflowKey.of("KYC"), "KYC", "Desc", "admin", now
        );
        def.setPendingVersion(10L, "admin", now);
        def.completePublication(10L, "admin", now);

        assertThat(def.getActiveVersionId()).isEqualTo(10L);
        assertThat(def.getPendingVersionId()).isNull();

        assertThatThrownBy(() -> def.setPendingVersion(10L, "admin", now))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("is already active");
    }

    @Test
    @DisplayName("Should correctly track executability based on active status and active version pointer")
    void shouldTrackExecutability() {
        WorkflowDefinition def = WorkflowDefinition.create(
                WorkflowKey.of("KYC"), "KYC", "Desc", "admin", now
        );
        assertThat(def.isExecutable()).isFalse();

        def.setPendingVersion(1L, "admin", now);
        def.completePublication(1L, "admin", now);
        assertThat(def.isExecutable()).isTrue();

        def.disable("admin", now);
        assertThat(def.isExecutable()).isFalse();
    }
}
